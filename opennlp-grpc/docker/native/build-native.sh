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

# Compiles the shaded server and gateway jars into native binaries with the
# GraalVM builder container, using the curated reachability metadata under
# docker/native/config/. Build the repository first (mvn clean install -Dgpu
# for the CUDA server flavor), then from opennlp-grpc/:
#   bash docker/native/build-native.sh
# Outputs land in docker/native/out/. See docker/native/README.md for the
# background on every flag.
set -euo pipefail
cd "$(dirname "$0")/../.."

BUILDER_IMAGE=ghcr.io/graalvm/native-image-community:25
SLF4J_SIMPLE_VERSION=2.0.18
OUT=docker/native/out
mkdir -p "${OUT}"
mkdir -p "${OUT}"

# The native binaries log through slf4j-simple instead of log4j-core: netty's
# bundled native-image metadata initializes its logging at build time, and
# log4j-core cannot be safely initialized then (its appenders capture open
# jar streams in the image heap).
slf4j_jar="${OUT}/slf4j-simple-${SLF4J_SIMPLE_VERSION}.jar"
if [ ! -f "${slf4j_jar}" ]; then
  curl -fsSL -o "${slf4j_jar}" "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/${SLF4J_SIMPLE_VERSION}/slf4j-simple-${SLF4J_SIMPLE_VERSION}.jar"
fi

cp opennlp-grpc-distr/target/opennlp-grpc-server-all-*.jar "${OUT}/server.jar"
# The target directory also holds -sources and -javadoc jars after an install.
webapp_jar=$(ls opennlp-grpc-webapp/target/opennlp-grpc-webapp-*.jar \
  | grep -vE -- '-(sources|javadoc|tests)\.jar$' | head -n 1)
cp "${webapp_jar}" "${OUT}/webapp.jar"

# Shared flags. The SimulateClassInitializer limits matter: without them the
# points-to analysis wedges for hours unrolling one enormous static
# initializer in the shaded server jar.
common_flags=(
  --no-fallback --enable-url-protocols=http,https -H:+AddAllCharsets
  --initialize-at-build-time=org.slf4j.simple,org.slf4j.helpers,org.slf4j.LoggerFactory,org.slf4j.Logger
  # grpc-netty-shaded manages pooled direct buffers through the FFM API on JDK 25
  # (CleanerJava25 closes an Arena.ofShared per freed chunk); without shared arena
  # support the first large response dies mid-write with UnsupportedFeatureError.
  -H:+SharedArenaSupport
  -H:+UnlockExperimentalVMOptions
  -H:SimulateClassInitializerMaxLoopIterations=32
  -H:SimulateClassInitializerMaxInlineDepth=8
  -H:SimulateClassInitializerMaxAllocatedBytes=4096
)

build() {
  local jar="$1" main="$2" output="$3" config="$4" xmx="$5"
  docker run --rm \
    -v "$(pwd)/${OUT}:/work" \
    -v "$(pwd)/docker/native/config/${config}:/config:ro" \
    "${BUILDER_IMAGE}" \
    -cp "/work/$(basename "${slf4j_jar}"):/work/${jar}" "${main}" -o "/work/${output}" \
    -H:ConfigurationFileDirectories=/config \
    "${common_flags[@]}" \
    -H:DeadlockWatchdogInterval=40 -H:-DeadlockWatchdogExitOnTimeout \
    "-J-Xmx${xmx}"
}

# Optional first argument selects what to build: all (default), gateway, or
# server. Front-end iteration only needs the one-minute gateway build.
target="${1:-all}"
case "${target}" in
  all|gateway|server) ;;
  *) echo "usage: $0 [all|gateway|server]" >&2; exit 2 ;;
esac

if [ "${target}" != server ]; then
  echo "== Building the gateway binary (takes about a minute)"
  build webapp.jar org.apache.opennlp.grpc.webapp.OpenNlpGrpcWebApp \
    opennlp-grpc-webapp webapp 24g
fi

if [ "${target}" != gateway ]; then
  echo "== Building the server binary (takes about 45 minutes)"
  build server.jar org.apache.opennlp.grpc.server.OpenNlpGrpcServer \
    opennlp-grpc-server server 64g
fi

echo "== Done: ${OUT}/opennlp-grpc-server and ${OUT}/opennlp-grpc-webapp"
echo "Build the runtime image with:"
echo "  docker build -f docker/native/Dockerfile.native -t opennlp-grpc-demo:native ."
