# Test case for replicating problems when attempting to run a Micronaut Lambda under an AWS ALB

### See https://github.com/micronaut-projects/micronaut-core/issues/9160

## Prerequisites

- Java 17
- [AWS CLI](https://aws.amazon.com/cli/)
- [CDK CLI](https://docs.aws.amazon.com/cdk/v2/guide/cli.html)

## Setup:

### Set your AWS credentials, typically either via environment variables or in `~/.aws/credentials`

### Modify `infra/cdk.json` to add your existing VPN to one of the environments (e.g. `dev01-stack01`)

## Deploy

```
cd infra
cdk deploy -c config=dev01-stack01
```

## Verification

### External
If the deployment worked then one of the last lines should include a URL of your deployed Lambda.
E.g:
`fubar-dev01-stack01-us-west-2.dns = fubar-dev01-stack01-alb-123456789.us-west-2.elb.amazonaws.com`

The portion on the right is the endpoint to your Lambda. Hit that with curl:
`curl -v fubar-dev01-stack01-alb-123456789.us-west-2.elb.amazonaws.com`

You should see something that includes lines similar to:
````
HTTP/1.1 200 OK OK
Server: awselb/2.0
Date: Wed, 26 Apr 2023 18:28:33 GMT
Content-Type: application/octet-stream
````

Note that content type should be application/json if this was working.

### AWS Console
Go to the deployed Lambda in the AWS Console.
Click on the 'Test' tab and look at the detailed response. It will look something like:
````
{
  "statusCode": 200,
  "multiValueHeaders": {
    "Content-Type": [
      "application/json"
    ],
    "Date": [
      "Wed, 26 Apr 2023 18:30:38 GMT"
    ]
  },
````
Note that that's appropriate for an API Gateway response, but not for a normal RESTful
service. Also note that content type is correct here, but is buried in `multiValuedHeaders`.

## Cleanup
Destroy what was deployed:
```
cd infra
cdk destroy -c config=dev01-stack01
```