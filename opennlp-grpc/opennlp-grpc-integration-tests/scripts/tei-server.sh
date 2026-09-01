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
# KIND, either express or implied.  See the License for the specific
# language governing permissions and limitations under the License.

# Starts a real HuggingFace Text Embeddings Inference (TEI) gRPC server in Docker
# for the opennlp-grpc live integration tests.
#
# Usage:
#   ./tei-server.sh start [--cpu|--gpu] [--model <hf-id>] [--port <port>] [--version <tei-version>]
#   ./tei-server.sh stop
#   ./tei-server.sh logs
#
# After 'start' reports ready, run the opt-in integration test with:
#   OPENNLP_TEI_TARGET=localhost:<port> mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify

set -euo pipefail

CONTAINER_NAME="opennlp-grpc-it-tei"
DEVICE="cpu"
MODEL="sentence-transformers/all-MiniLM-L6-v2"
PORT="18080"
TEI_VERSION="1.9"
CACHE_DIR="${HOME}/.cache/opennlp-grpc-it-tei-data"
READY_TIMEOUT_SECONDS=600

usage() {
  sed -n '18,28p' "$0"
  exit 1
}

# TEI publishes one GPU image per CUDA compute capability; map the detected
# capability to the matching image tag prefix (see the TEI README image matrix).
gpu_image_tag() {
  local compute_cap
  compute_cap="$(nvidia-smi --query-gpu=compute_cap --format=csv,noheader | head -n 1 | tr -d ' ')"
  case "${compute_cap}" in
    7.5) echo "turing-${TEI_VERSION}-grpc" ;;
    8.0) echo "${TEI_VERSION}-grpc" ;;
    8.6) echo "86-${TEI_VERSION}-grpc" ;;
    8.9) echo "89-${TEI_VERSION}-grpc" ;;
    9.0) echo "hopper-${TEI_VERSION}-grpc" ;;
    10.0) echo "100-${TEI_VERSION}-grpc" ;;
    12.0) echo "120-${TEI_VERSION}-grpc" ;;
    12.1) echo "121-${TEI_VERSION}-grpc" ;;
    *)
      echo "Unsupported or undetected CUDA compute capability: '${compute_cap}'" >&2
      exit 1
      ;;
  esac
}

start() {
  local image gpu_args=()
  if [[ "${DEVICE}" == "gpu" ]]; then
    image="ghcr.io/huggingface/text-embeddings-inference:$(gpu_image_tag)"
    gpu_args=(--gpus all)
  else
    image="ghcr.io/huggingface/text-embeddings-inference:cpu-${TEI_VERSION}-grpc"
  fi

  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Container ${CONTAINER_NAME} is already running; stop it first." >&2
    exit 1
  fi

  mkdir -p "${CACHE_DIR}"
  echo "Starting TEI (${DEVICE}) on localhost:${PORT} with model ${MODEL}"
  echo "Image: ${image} (model cache: ${CACHE_DIR})"
  docker run -d --rm --name "${CONTAINER_NAME}" \
    "${gpu_args[@]+"${gpu_args[@]}"}" \
    -p "${PORT}:80" \
    -v "${CACHE_DIR}:/data" \
    --pull always \
    "${image}" \
    --model-id "${MODEL}"

  echo -n "Waiting for TEI to become ready (downloads the model on first run) "
  local waited=0
  until docker logs "${CONTAINER_NAME}" 2>&1 | grep -q "Ready"; do
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
      echo
      echo "TEI container exited unexpectedly; last logs:" >&2
      docker logs "${CONTAINER_NAME}" 2>&1 | tail -n 50 >&2 || true
      exit 1
    fi
    if (( waited >= READY_TIMEOUT_SECONDS )); then
      echo
      echo "TEI did not become ready within ${READY_TIMEOUT_SECONDS}s; last logs:" >&2
      docker logs "${CONTAINER_NAME}" 2>&1 | tail -n 50 >&2
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
    echo -n "."
  done
  echo
  echo "TEI is ready."
  echo
  echo "Run the live tests against it with:"
  echo "  OPENNLP_TEI_TARGET=localhost:${PORT} mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify"
}

stop() {
  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    docker stop "${CONTAINER_NAME}"
    echo "Stopped ${CONTAINER_NAME}."
  else
    echo "Container ${CONTAINER_NAME} is not running."
  fi
}

logs() {
  docker logs -f "${CONTAINER_NAME}"
}

[[ $# -ge 1 ]] || usage
COMMAND="$1"
shift

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cpu) DEVICE="cpu"; shift ;;
    --gpu) DEVICE="gpu"; shift ;;
    --model) MODEL="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --version) TEI_VERSION="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

case "${COMMAND}" in
  start) start ;;
  stop) stop ;;
  logs) logs ;;
  *) echo "Unknown command: ${COMMAND}" >&2; usage ;;
esac
