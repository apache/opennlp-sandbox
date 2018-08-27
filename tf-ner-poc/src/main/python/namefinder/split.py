#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

import random
import sys

def main():

    if len(sys.argv) != 5:
        print("Usage split.py data_file train_file dev_file test_file")
        return

    train = []
    dev = []
    test = []

    with open(sys.argv[1]) as f:
        for line in f:

            if len(line.strip()) == 0:
                continue

            rand = random.random()
            if rand < 0.8:
                train.append(line)
            elif rand < 0.9:
                dev.append(line)
            elif rand <= 1.0:
                test.append(line)

    with open(sys.argv[2], 'w') as f:
        for item in train:
            f.write("%s" % item)

    with open(sys.argv[3], 'w') as f:
        for item in dev:
            f.write("%s" % item)

    with open(sys.argv[4], 'w') as f:
        for item in test:
            f.write("%s" % item)

if __name__ == "__main__":
    main()

