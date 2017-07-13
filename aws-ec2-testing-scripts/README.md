# OpenNLP Testing Scripts

These are scripts useful when testing OpenNLP builds on EC2.

## Directory Structure

These scripts are written expecting the following directory structure:

* `/opt/` - Contains these scripts.
* `/opt/opennlp` - Contains the OpenNLP code as cloned from https://github.com/apache/opennlp.
* `/opt/opennlp-data` - Contains the data required for some of the OpenNLP tests. Contact dev@opennlp.apache.org for information on this data.

## EC2 Instance Requirements

* The instance must have the AWS CLI installed.
* The scripts use SNS to send notifications so the instance must have permissions to publish SNS messages through either an instance role or via access/secret keys configured in the AWS CLI.
* You must have an existing SNS topic configured to publish messages to and you must set the ARN in the `notify.sh` script.

## Notifications and Results

You can configure the subject, message, and destination (topic ARN) in the `notify.sh` script. The build log will be too large (>256KB) for sending in the SNS message. You can modify the `notify.sh` script for uploading the build results to an S3 bucket.

## CloudFormation Template

The CloudFormation template can help with creating the instance.
