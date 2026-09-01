#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Generates the disposable Go stubs for the v1 protos into gen/opennlpv1.
# The protos declare no go_package, so every file is mapped explicitly to this
# example module. Requires protoc, protoc-gen-go, and protoc-gen-go-grpc.
set -euo pipefail
cd "$(dirname "$0")"

proto_root=../../opennlp-grpc-api/src/main/proto
go_package=opennlp-grpc-go-example/gen/opennlpv1

mapping=()
for file in "$proto_root"/org/apache/opennlp/grpc/v1/*.proto; do
  relative=${file#"$proto_root"/}
  mapping+=("--go_opt=M${relative}=${go_package}" "--go-grpc_opt=M${relative}=${go_package}")
done

protoc -I "$proto_root" \
  --go_out=. --go_opt=module=opennlp-grpc-go-example \
  --go-grpc_out=. --go-grpc_opt=module=opennlp-grpc-go-example \
  "${mapping[@]}" \
  "$proto_root"/org/apache/opennlp/grpc/v1/*.proto

echo "Generated $(ls gen/opennlpv1 | wc -l) files into gen/opennlpv1"
