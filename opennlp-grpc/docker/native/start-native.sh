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

# Native demonstration entrypoint: start the native gRPC server, wait until it
# accepts connections, then start the native JSON gateway against it.
set -eu

GRPC_PORT="${OPENNLP_GRPC_PORT:-7071}"
HTTP_PORT="${OPENNLP_HTTP_PORT:-7072}"
CONFIG_FILE=/srv/opennlp/server.properties

server_args=(--port "${GRPC_PORT}")
if [ -f "${CONFIG_FILE}" ]; then
  server_args=("${server_args[@]}" --config "${CONFIG_FILE}")
fi

# shellcheck disable=SC2086 -- operator-supplied option strings are word-split.
/opt/opennlp/opennlp-grpc-server \
  -Donnxruntime.native.path=/opt/onnxruntime \
  "${server_args[@]}" ${OPENNLP_SERVER_OPTS:-} &
server_pid=$!

for _ in $(seq 1 120); do
  if (exec 3<>"/dev/tcp/127.0.0.1/${GRPC_PORT}") 2>/dev/null; then
    break
  fi
  if ! kill -0 "${server_pid}" 2>/dev/null; then
    echo "opennlp-grpc-server exited before accepting connections" >&2
    exit 1
  fi
  sleep 1
done

# shellcheck disable=SC2086 -- operator-supplied option strings are word-split.
/opt/opennlp/opennlp-grpc-webapp \
  --grpc-target "127.0.0.1:${GRPC_PORT}" --http-port "${HTTP_PORT}" \
  --http-host 0.0.0.0 --allow-remote ${OPENNLP_WEBAPP_OPTS:-} &
webapp_pid=$!

term() {
  kill "${webapp_pid}" 2>/dev/null || true
  kill "${server_pid}" 2>/dev/null || true
}
trap term TERM INT
wait "${webapp_pid}" "${server_pid}"
