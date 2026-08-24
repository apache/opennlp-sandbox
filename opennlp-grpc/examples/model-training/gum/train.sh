#!/usr/bin/env bash
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

set -euo pipefail

GUM_REVISION=22fdf87f9c71c96bcc771461d06e689b1f90020d
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
OUTPUT_DIR=${1:-"${SCRIPT_DIR}/output"}
OPENNLP_CHECKOUT=${OPENNLP_SOURCE:-}

if [[ -z "${OPENNLP_CHECKOUT}" || ! -x "${OPENNLP_CHECKOUT}/mvnw" ]]; then
  echo "OPENNLP_SOURCE must name a current OpenNLP source checkout" >&2
  exit 2
fi

OUTPUT_DIR=$(mkdir -p "${OUTPUT_DIR}" && cd "${OUTPUT_DIR}" && pwd)
WORK_DIR=$(mktemp -d)
trap 'rm -rf "${WORK_DIR}"' EXIT

git clone --quiet --filter=blob:none --no-checkout \
  https://github.com/amir-zeldes/gum.git "${WORK_DIR}/gum"
git -C "${WORK_DIR}/gum" sparse-checkout init --no-cone
git -C "${WORK_DIR}/gum" sparse-checkout set /const/ /splits.md /LICENSE.md
git -C "${WORK_DIR}/gum" checkout --quiet --detach "${GUM_REVISION}"

mkdir -p "${WORK_DIR}/prepared"
for partition in train dev test; do
  awk -v partition="${partition}" -f "${SCRIPT_DIR}/select-split.awk" \
    "${WORK_DIR}/gum/splits.md" > "${WORK_DIR}/prepared/${partition}-files.txt"
  sed "s#^#${WORK_DIR}/gum/#" "${WORK_DIR}/prepared/${partition}-files.txt" \
    | xargs awk -f "${SCRIPT_DIR}/ptb-one-line.awk" \
    > "${WORK_DIR}/prepared/${partition}.parse"
done

"${OPENNLP_CHECKOUT}/mvnw" -q -f "${OPENNLP_CHECKOUT}/pom.xml" \
  -pl opennlp-tools -am -Dopennlp.forkCount=1 -DskipTests package
"${OPENNLP_CHECKOUT}/mvnw" -q -f "${OPENNLP_CHECKOUT}/pom.xml" \
  -pl opennlp-tools -DincludeScope=runtime dependency:build-classpath \
  -Dmdep.outputFile="${WORK_DIR}/classpath.txt"

TOOLS_JAR=
for candidate in "${OPENNLP_CHECKOUT}"/opennlp-tools/target/opennlp-tools-*.jar; do
  case "${candidate}" in
    *-tests.jar|*-javadoc.jar|*-sources.jar) ;;
    *) TOOLS_JAR=${candidate} ;;
  esac
done
if [[ -z "${TOOLS_JAR}" ]]; then
  echo "OpenNLP tools jar was not produced" >&2
  exit 2
fi
CLASSPATH="${TOOLS_JAR}:$(tr -d '\n' < "${WORK_DIR}/classpath.txt")"

java -Xmx6g -cp "${CLASSPATH}" opennlp.tools.cmdline.CLI ParserTrainer \
  -parserType CHUNKING \
  -headRules "${OPENNLP_CHECKOUT}/opennlp-tools/lang/en/parser/en-head_rules" \
  -model "${OUTPUT_DIR}/en-gum-cc-by-4-parser.bin" \
  -lang en -data "${WORK_DIR}/prepared/train.parse" -encoding UTF-8

javac -d "${WORK_DIR}/classes" -cp "${CLASSPATH}" "${SCRIPT_DIR}/GumModelTool.java"
java -Xmx4g -cp "${WORK_DIR}/classes:${CLASSPATH}" GumModelTool \
  "${OUTPUT_DIR}/en-gum-cc-by-4-parser.bin" \
  "${OUTPUT_DIR}/en-gum-cc-by-4-chunker.bin" \
  "${WORK_DIR}/prepared/test.parse" | tee "${OUTPUT_DIR}/EVALUATION.txt"

cp "${WORK_DIR}/gum/LICENSE.md" "${OUTPUT_DIR}/LICENSE-GUM.md"
(
  cd "${OUTPUT_DIR}"
  sha256sum en-gum-cc-by-4-parser.bin en-gum-cc-by-4-chunker.bin > SHA256SUMS
)
{
  printf 'GUM revision: %s\n' "${GUM_REVISION}"
  printf 'OpenNLP revision: %s\n' "$(git -C "${OPENNLP_CHECKOUT}" rev-parse HEAD)"
  printf 'Training trees: %s\n' "$(wc -l < "${WORK_DIR}/prepared/train.parse")"
  printf 'Development trees: %s\n' "$(wc -l < "${WORK_DIR}/prepared/dev.parse")"
  printf 'Test trees: %s\n' "$(wc -l < "${WORK_DIR}/prepared/test.parse")"
  for partition in train dev test; do
    digest=$(sha256sum "${WORK_DIR}/prepared/${partition}.parse")
    printf '%s  prepared/%s.parse\n' "${digest%% *}" "${partition}"
  done
} > "${OUTPUT_DIR}/TRAINING-PROVENANCE.txt"

echo "Models and provenance written to ${OUTPUT_DIR}"
