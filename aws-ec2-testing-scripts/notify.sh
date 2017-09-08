#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

SUBJECT=$1
TOPIC_ARN="TOPICARNPARAM"
LOG_FILE="/opt/build.log"

# The max size for a SNS body is 256KB.
# We'll round down a bit to safely stay under that limit.
tail -c 200000 $LOG_FILE > /tmp/subset.log

OUTCOME="SUCCESS"

# Look to see if the build failed.
if grep -q 'BUILD FAILURE' "$LOG_FILE"; then
  OUTCOME="FAILURE"
fi

# Publish the message to SNS.
aws sns publish \
  --region us-east-1 \
  --topic-arn "$TOPIC_ARN" \
  --subject "$OUTCOME - $SUBJECT" \
  --message file:///tmp/subset.log
