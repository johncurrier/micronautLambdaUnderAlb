package com.example.fubar;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import software.constructs.Construct;

import java.util.Map;

/**
 * Various utility functions that are common to whatever stack we're deploying to.
 *
 * @author John Currier
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class AppStackUtils {
	/**
	 * Get the configuration of our AWS environment from cdk.json.
	 */
	static AppStackConfig getConfig(Construct app) {
		Object envObj = app.getNode().tryGetContext("config");
		if (envObj == null) { // definitely null if it's not set
			throw new IllegalArgumentException("Environment not specified. Use 'cdk deploy -c config=XXX'");
		}

		String env = envObj.toString();

		@SuppressWarnings("unchecked")
		Map<String, String> propsByName = (Map<String, String>)app.getNode().tryGetContext(env);
		if (propsByName == null) {
			throw new IllegalArgumentException("No settings detected in cdk.json for environment " + env);
		}

		return AppStackConfig.builder()
				.env(env)
				.accountType(getRequiredProperty(propsByName, "accountType", env))
				.vpc(getRequiredProperty(propsByName, "vpc", env))
				.build();
	}

	/**
	 * Consistency in how our various parts are named.
	 */
	static String createName(String env, String region, String type) {
		StringBuilder buf = new StringBuilder("fubar-");

		buf.append(env);

		if (region != null) {
			buf.append("-");
			buf.append(region);
		}

		if (type != null) {
			buf.append("-");
			buf.append(type);
		}

		return buf.toString();
	}

	private static String getRequiredProperty(Map<String, String> propsByName, String name, String env) {
		String value = propsByName.get(name);
		if (value == null) {
			throw new IllegalArgumentException(name + " not specified in cdk.json for environment " + env);
		}

		return value;
	}
}