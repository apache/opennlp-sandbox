# OpenNLP Testing Scripts

These are scripts to automate running OpenNLP's evaluation tests on AWS EC2. The scripts are composed of a Packer script, a CloudFormation template, and a few supporting bash scripts.

## Running OpenNLP Tests

To run the tests two actions must be performed:

1. Create an AMI that contains the required tools and OpenNLP test data.
1. Launch a CloudFormation stack that creates an instance from the AMI and runs the tests.

These two steps are described in detail below.

### Creating the AMI used for Testing

Creating the AMI requires the [Packer](https://www.packer.io/intro/index.html) tool. To create the AMI execute the `build-ami.sh` script. You may need to modify the location of the Packer executable in the `build-ami.sh` script. The OpenNLP test data should exist as `opennlp-data.zip` in the current directory prior to creating the AMI. This allows the Packer script to upload the test data to the instance when creating the AMI.

You only need to create the AMI once. The same AMI can be reused for testing future OpenNLP versions. The only need to create a new AMI is to include updated OpenNLP test data.

### Create the CloudFormation Stack

Using the `cf-template.json` CloudFormation template create a new stack. The `Image` parameter should reference the AMI created by Packer. Be sure to check your email and confirm your subscription to the newly created SNS topic in order to receive the build emails.

You can create a stack from the template either through the AWS Console or using the AWS CLI:

```
aws cloudformation create-stack \
  --stack-name OpenNLP-Testing \
  --template-body file://./cf-template.json \
  --parameters \
    ParameterKey=InstanceType,ParameterValue=m4.xlarge \
    ParameterKey=KeyName,ParameterValue=keyname \
    ParameterKey=NotificationsEmail,ParameterValue=your@email.com \
    ParameterKey=Branch,ParameterValue=opennlp-1.8.3 \
    ParameterKey=Tests,ParameterValue=run-eval-tests.sh
```

When the tests are complete (either as success or failure) the email address specified in the `NotificationsEmail` parameter will receive an email notification. The email's subject will indicate if the tests were successful or failed and the email's body will contain approximately the last 200 KB of text from the Maven build log. Once you receive the notification you can terminate the stack or you can SSH into the EC2 instance if you need to debug or re-run any tests.

```
aws cloudformation delete-stack --stack-name OpenNLP-Testing
```

## AWS Infrastructure

The `cf-template.json` CloudFormation template creates a new VPC to contain the EC2 instance that runs the tests. The template creates all the necessary components such as the route table, subnet, IAM policies and roles, and security group.

### Instance Directory Structure

These scripts are written expecting the following directory structure:

* `/opt/` - Contains these scripts.
* `/opt/opennlp` - Contains the OpenNLP code as cloned from https://github.com/apache/opennlp.
* `/opt/opennlp-data` - Contains the data required for OpenNLP's eval tests.

## License

Licensed under the Apache Software License, version 2.
