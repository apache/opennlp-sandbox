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

# Container entrypoint: start the OpenNLP gRPC server, wait until it accepts
# connections, then start the JSON gateway and web workbench against it.
# Stopping the container stops the gateway first, then lets the server drain
# in-flight RPCs through its configured shutdown grace period.
#
# Environment:
#   OPENNLP_GRPC_PORT    gRPC listen port (default 7071)
#   OPENNLP_HTTP_PORT    gateway HTTP port (default 7072)
#   OPENNLP_SERVER_OPTS  extra opennlp-grpc-server arguments
#   OPENNLP_WEBAPP_OPTS  extra opennlp-grpc-webapp arguments
#   JDK_JAVA_OPTIONS     JVM options for both processes (standard JDK variable)
#
# When /srv/opennlp/server.properties exists it is passed to the server as its
# configuration file.
set -eu

GRPC_PORT="${OPENNLP_GRPC_PORT:-7071}"
HTTP_PORT="${OPENNLP_HTTP_PORT:-7072}"
CONFIG_FILE=/srv/opennlp/server.properties
STARTUP_TIMEOUT_SECONDS=120

server_jar=(/opt/opennlp/opennlp-grpc-server-*.jar)
webapp_jar=(/opt/opennlp/opennlp-grpc-webapp-*.jar)

server_args=(--port "${GRPC_PORT}")
if [ -f "${CONFIG_FILE}" ]; then
  server_args+=(--config "${CONFIG_FILE}")
fi
# The optional embedding backends are plain jars appended to the shaded
# server jar, matching the classpath the demo-model-download.sh script prints.
# shellcheck disable=SC2086 -- operator-supplied option strings are word-split.
java -cp "${server_jar[0]}:/opt/opennlp/backends/*" \
  org.apache.opennlp.grpc.server.OpenNlpGrpcServer \
  "${server_args[@]}" ${OPENNLP_SERVER_OPTS:-} &
server_pid=$!

webapp_pid=""
shutdown() {
  trap - TERM INT
  if [ -n "${webapp_pid}" ]; then
    kill "${webapp_pid}" 2>/dev/null || true
    wait "${webapp_pid}" 2>/dev/null || true
  fi
  kill "${server_pid}" 2>/dev/null || true
  wait "${server_pid}" 2>/dev/null || true
}
trap 'shutdown' TERM INT

# Wait until the gRPC listener accepts a TCP connection so the gateway's
# startup discovery does not race the server.
deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
until (exec 3<>"/dev/tcp/127.0.0.1/${GRPC_PORT}") 2>/dev/null; do
  if ! kill -0 "${server_pid}" 2>/dev/null; then
    echo "opennlp-grpc-server exited before accepting connections" >&2
    wait "${server_pid}" 2>/dev/null || exit 1
    exit 1
  fi
  if [ "${SECONDS}" -ge "${deadline}" ]; then
    echo "opennlp-grpc-server did not accept connections within ${STARTUP_TIMEOUT_SECONDS}s" >&2
    shutdown
    exit 1
  fi
  sleep 1
done

# The gateway must bind the container interface for published ports to work.
# It stays private as long as the container publishes to loopback, as the
# provided docker-compose.yml and the documented docker run commands do.
# shellcheck disable=SC2086 -- operator-supplied option strings are word-split.
java -jar "${webapp_jar[0]}" \
  --http-host 0.0.0.0 --allow-remote \
  --http-port "${HTTP_PORT}" \
  --grpc-target "127.0.0.1:${GRPC_PORT}" \
  ${OPENNLP_WEBAPP_OPTS:-} &
webapp_pid=$!

# Exit when either process exits, stopping the other in order.
wait -n "${server_pid}" "${webapp_pid}"
status=$?
shutdown
exit "${status}"
