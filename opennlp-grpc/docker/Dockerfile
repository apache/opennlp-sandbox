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

# Runtime image for the OpenNLP gRPC server and its optional web application.
# Build the repository first (mvn clean install), then from opennlp-grpc/:
#   docker build -f docker/Dockerfile -t opennlp-grpc-demo .
# The base image is multi-arch (linux/amd64 and linux/arm64), so the same
# build works out of the box on Linux and on macOS Docker Desktop, including
# Apple silicon. The default configuration needs no native libraries: the
# bundled language detector and English models load from the shaded jar.
FROM eclipse-temurin:21-jre-noble

LABEL org.opencontainers.image.title="Apache OpenNLP gRPC demonstration stack" \
  org.opencontainers.image.description="OpenNLP gRPC server with the JSON gateway and browser workbench" \
  org.opencontainers.image.licenses="Apache-2.0" \
  org.opencontainers.image.source="https://github.com/apache/opennlp-sandbox"

RUN useradd --system --create-home --home-dir /home/opennlp opennlp \
  && mkdir -p /opt/opennlp/backends /srv/opennlp \
  && chown -R opennlp:opennlp /opt/opennlp /srv/opennlp

COPY --chown=opennlp:opennlp opennlp-grpc-distr/target/opennlp-grpc-server-all-*.jar /opt/opennlp/
COPY --chown=opennlp:opennlp opennlp-grpc-webapp/target/opennlp-grpc-webapp-*.jar /opt/opennlp/
# The server-all assembly already bundles every in-tree add-on (ONNX Runtime,
# static tables, TEI, OpenVINO); /opt/opennlp/backends stays as the drop-in
# point for third-party add-on jars mounted or copied by operators.
COPY --chown=opennlp:opennlp --chmod=0755 docker/start-demo.sh /opt/opennlp/start-demo.sh

# Persistent server-owned state (models, vocabulary artifacts, persisted
# indexes) and the optional server.properties live under /srv/opennlp.
VOLUME /srv/opennlp

# 7071 is the gRPC service, 7072 the JSON gateway and browser workbench.
EXPOSE 7071 7072

USER opennlp
WORKDIR /opt/opennlp

# The gateway has no authentication, so the healthcheck and the documented
# port mappings stay on loopback interfaces.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>\"/dev/tcp/127.0.0.1/${OPENNLP_HTTP_PORT:-7072}\""]

ENTRYPOINT ["/opt/opennlp/start-demo.sh"]
