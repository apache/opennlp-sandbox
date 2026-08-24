# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

function trimmed(value) {
  sub(/^[[:space:]]+/, "", value)
  sub(/[[:space:]]+$/, "", value)
  return value
}

BEGIN {
  depth = 0
  tree = ""
}

{
  line = trimmed($0)
  if (line != "") {
    if (tree == "") {
      tree = line
    } else {
      tree = tree " " line
    }
    for (cursor = 1; cursor <= length(line); cursor++) {
      character = substr(line, cursor, 1)
      if (character == "(") {
        depth++
      } else if (character == ")") {
        depth--
      }
      if (depth < 0) {
        print "unbalanced close in " FILENAME > "/dev/stderr"
        exit 2
      }
    }
    if (depth == 0) {
      print tree
      tree = ""
    }
  }
}

END {
  if (depth != 0 || tree != "") {
    print "unbalanced tree in " FILENAME > "/dev/stderr"
    exit 2
  }
}
