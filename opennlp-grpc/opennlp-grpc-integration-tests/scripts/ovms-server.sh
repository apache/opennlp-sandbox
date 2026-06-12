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

# Prepares and starts a real OpenVINO Model Server (OVMS) in Docker for the
# opennlp-grpc live integration tests.
#
# OVMS serves raw tensor models, so 'prepare' first exports the embedding model as a
# single OpenVINO graph (tokenizer + transformer + mean pooling + L2 normalization,
# fused with openvino_tokenizers) that maps text strings directly to embeddings.
# Requires Docker and uv (https://docs.astral.sh/uv/).
#
# Usage:
#   ./ovms-server.sh prepare [--model <hf-id>]
#   ./ovms-server.sh start [--cpu|--gpu] [--port <grpc-port>] [--version <ovms-version>]
#   ./ovms-server.sh stop
#   ./ovms-server.sh logs
#
# --gpu targets Intel GPUs (iGPU / Arc / Flex via /dev/dri); there is no CUDA variant.
#
# After 'start' reports ready, run the opt-in integration test with:
#   OPENNLP_OVMS_TARGET=localhost:<grpc-port> mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER_NAME="opennlp-grpc-it-ovms"
SERVED_MODEL_NAME="embedder"
DEVICE="cpu"
MODEL="sentence-transformers/all-MiniLM-L6-v2"
GRPC_PORT="19000"
REST_PORT="19001"
OVMS_VERSION="latest"
MODELS_DIR="${HOME}/.cache/opennlp-grpc-it-ovms-models"
READY_TIMEOUT_SECONDS=300

usage() {
  sed -n '26,35p' "$0"
  exit 1
}

prepare() {
  command -v uv >/dev/null || { echo "uv is required for model export" >&2; exit 1; }
  local version_dir="${MODELS_DIR}/${SERVED_MODEL_NAME}/1"
  echo "Exporting '${MODEL}' to ${version_dir} (text -> normalized embedding, single graph)"
  uv run --with "optimum[openvino]" --with openvino-tokenizers \
    python "${SCRIPT_DIR}/prepare_ovms_embedder.py" --model-id "${MODEL}" --output "${version_dir}"
}

start() {
  if [[ ! -f "${MODELS_DIR}/${SERVED_MODEL_NAME}/1/model.xml" ]]; then
    echo "No exported model at ${MODELS_DIR}/${SERVED_MODEL_NAME}/1; run '$0 prepare' first." >&2
    exit 1
  fi
  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Container ${CONTAINER_NAME} is already running; stop it first." >&2
    exit 1
  fi

  local image="openvino/model_server:${OVMS_VERSION}" device_args=() ovms_args=()
  if [[ "${DEVICE}" == "gpu" ]]; then
    image="openvino/model_server:${OVMS_VERSION}-gpu"
    device_args=(--device /dev/dri --group-add "$(stat -c '%g' /dev/dri/render* | head -n 1)")
    ovms_args=(--target_device GPU)
  fi

  echo "Starting OVMS (${DEVICE}) on localhost:${GRPC_PORT} (gRPC) serving '${SERVED_MODEL_NAME}'"
  echo "Image: ${image} (models: ${MODELS_DIR})"
  docker run -d --rm --name "${CONTAINER_NAME}" \
    "${device_args[@]+"${device_args[@]}"}" \
    -p "${GRPC_PORT}:9000" -p "${REST_PORT}:8000" \
    -v "${MODELS_DIR}:/models:ro" \
    --pull always \
    "${image}" \
    --model_name "${SERVED_MODEL_NAME}" --model_path "/models/${SERVED_MODEL_NAME}" \
    --port 9000 --rest_port 8000 \
    "${ovms_args[@]+"${ovms_args[@]}"}"

  echo -n "Waiting for OVMS to report the model ready "
  local waited=0
  until curl -sf "http://localhost:${REST_PORT}/v2/models/${SERVED_MODEL_NAME}/ready" \
      >/dev/null 2>&1; do
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
      echo
      echo "OVMS container exited unexpectedly; last logs:" >&2
      docker logs "${CONTAINER_NAME}" 2>&1 | tail -n 50 >&2 || true
      exit 1
    fi
    if (( waited >= READY_TIMEOUT_SECONDS )); then
      echo
      echo "OVMS did not become ready within ${READY_TIMEOUT_SECONDS}s; last logs:" >&2
      docker logs "${CONTAINER_NAME}" 2>&1 | tail -n 50 >&2
      exit 1
    fi
    sleep 2
    waited=$((waited + 2))
    echo -n "."
  done
  echo
  echo "OVMS is ready."
  echo
  echo "Run the live tests against it with:"
  echo "  OPENNLP_OVMS_TARGET=localhost:${GRPC_PORT} mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify"
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
    --port) GRPC_PORT="$2"; REST_PORT="$(( $2 + 1 ))"; shift 2 ;;
    --version) OVMS_VERSION="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

case "${COMMAND}" in
  prepare) prepare ;;
  start) start ;;
  stop) stop ;;
  logs) logs ;;
  *) echo "Unknown command: ${COMMAND}" >&2; usage ;;
esac
