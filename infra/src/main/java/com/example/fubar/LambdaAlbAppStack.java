package com.example.fubar;

import io.micronaut.aws.cdk.function.MicronautFunction;
import io.micronaut.aws.cdk.function.MicronautFunctionFile;
import io.micronaut.starter.application.ApplicationType;
import io.micronaut.starter.options.BuildTool;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.targets.LambdaTarget;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.CfnFunction.SnapStartProperty;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static com.example.fubar.AppStackUtils.*;

/**
 * CDK-based deployment.</p>
 *
 * Gets called by <code>cdk synth</code> / <code>cdk deploy</code> from the infra directory.
 *
 * @author John Currier
 */
public class LambdaAlbAppStack extends Stack {
	public static void main(final String[] args) {
		App app = new App();

		AppStackConfig config = getConfig(app);
		new LambdaAlbAppStack(app, config, System.getenv("CDK_DEFAULT_REGION"), System.getenv("CDK_DEFAULT_ACCOUNT"));

		app.synth();
	}

	private LambdaAlbAppStack(Construct app, AppStackConfig config, String region, String account) {
		// name of the stack as seen by CloudFormation
		super(app, createName(config.getEnv(), region, null),
				StackProps.builder().env(Environment.builder()
						.account(account)
						.region(region)
						.build()).build());

		String env = config.getEnv();

		System.out.println("Deploying to " + env + "." + region);

		Map<String, String> envVars = Map.of(
				// https://aws.amazon.com/blogs/compute/optimizing-aws-lambda-function-performance-for-java/
				"JAVA_TOOL_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1",
				"AWS_SERVERLESS_JAVA_CONTAINER_INIT_GRACE_TIME", "500");

		System.out.println("Specifying environment variables: " + envVars);

		// name of the lambda function as well as part of the CloudWatch log group
		//  e.g. cdk-fubar-dev02-stack01
		String lambdaName = createName(config.getEnv(), null, null);

		// this handler seems to be unexpectedly written to be API Gateway-specific
		String handlerClassName = "io.micronaut.function.aws.proxy.alb.ApplicationLoadBalancerFunction";

		Function lambda = MicronautFunction.create(ApplicationType.DEFAULT, false, this, lambdaName)
				.functionName(lambdaName)
				.runtime(Runtime.JAVA_17)
				.description("Replicate response issues related to running a Micronaut Lambda under ALB")
				.handler(handlerClassName)
				.environment(envVars)
				.role(createRole(this, env, region))
				.code(Code.fromAsset(functionPath()))
				.timeout(Duration.seconds(20))
				.memorySize(1024)
				.tracing(Tracing.DISABLED) // not supported by SnapStart
				.logRetention(RetentionDays.ONE_MONTH)
				.architecture(Architecture.X86_64) // what the Micronaut launch samples use
				.currentVersionOptions(VersionOptions.builder().removalPolicy(RemovalPolicy.DESTROY).build())
				.build();

		// SnapStart requires versioning to be enabled
		// this lets us point to what's being deployed
		lambda.addAlias("current");

		enableSnapStart(lambda);

		IVpc vpc = Vpc.fromLookup(this, "vpc",
				VpcLookupOptions.builder().vpcName(config.getVpc()).build());

		SecurityGroup securityGroup = SecurityGroup.Builder.create(this, "securityGroup")
				.securityGroupName(createName(env, region, "securityGroup"))
				.vpc(vpc)
				.build();

		ApplicationLoadBalancer alb = ApplicationLoadBalancer.Builder.create(this, "alb")
				.loadBalancerName(createName(env, null, "alb"))	// max of 32 chars
				.vpc(vpc)
				.internetFacing(true)
				.securityGroup(securityGroup)
				.http2Enabled(true)	// no differences when enabled???
				.build();

		ApplicationListener listener = alb.addListener("listener", BaseApplicationListenerProps.builder()
				.protocol(ApplicationProtocol.HTTP)
				.open(true)
				.build());

		// WARNING:
		//  LambdaTarget doesn't support being associated with a given alias / version,
		//   so when we do a fresh deployment we have to:
		//    - go to EC2 Target Groups in the AWS Console
		//    - deregister the existing target
		//    - register a new one that points to the "current" alias
		listener.addTargets("targets", AddApplicationTargetsProps.builder()
				.targetGroupName(createName(env, null, "tg"))	// max of 32 chars
				.targets(List.of(new LambdaTarget(lambda)))
				.build());

		CfnOutput.Builder.create(this, "dns")
				.exportName(createName(env, region, "dns"))
				.value(alb.getLoadBalancerDnsName())
				.build();
	}

	private static String functionPath() {
		return "../app/build/libs/" + functionFilename();
	}

	private static String functionFilename() {
		return MicronautFunctionFile.builder()
				.graalVMNative(false)
				.version("1.0")
				.archiveBaseName("app")
				.buildTool(BuildTool.GRADLE)
				.build();
	}

	private static Role createRole(Stack stack, String env, String region) {
		RoleProps props = RoleProps.builder()
				.roleName(createName(env, region, "role")) // per Darby: include region in the role's name
				.assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
				.build();
		Role role = new Role(stack, props.getRoleName(), props);

		// CloudWatch write perms
		role.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));

		return role;
	}

	private static void enableSnapStart(Function lambda) {
		// tells AWS to create a snapshot of the initialized execution environment when we get published
		SnapStartProperty snapStartProp = CfnFunction.SnapStartProperty.builder().applyOn("PublishedVersions").build();
		((CfnFunction)lambda.getNode().getDefaultChild()).setSnapStart(snapStartProp);
	}
}