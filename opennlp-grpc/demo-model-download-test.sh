#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/opennlp-demo-model-test.XXXXXX")

cleanup() {
  find "$test_root" -type f -delete
  find "$test_root" -depth -type d -empty -delete
}
trap cleanup EXIT HUP INT TERM

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

assert_file_contains() {
  local file=$1
  local expected=$2
  grep -Fq -- "$expected" "$file" || fail "$file does not contain: $expected"
}

mkdir -p "$test_root/bin"
printf 'fake jar\n' > "$test_root/server.jar"
printf 'fake backend jar\n' > "$test_root/static-backend.jar"

cat > "$test_root/bin/java" <<'FAKE_JAVA'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "$*" >> "$DEMO_TEST_LOG"
source_uri=
target=
while (($# > 0)); do
  case "$1" in
    --source)
      source_uri=$2
      shift 2
      ;;
    --target)
      target=$2
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
name=${source_uri%%\?*}
name=${name%%\#*}
name=${name##*/}
if [[ $name == *.gz ]]; then
  name=${name%.gz}
fi
mkdir -p "$target"
printf 'installed fixture for %s\n' "$source_uri" > "$target/$name"
FAKE_JAVA
chmod +x "$test_root/bin/java"

log_file=$test_root/installer.log
target=$test_root/models
config=$test_root/demo-server.properties
run_output=$test_root/run-output.txt
parser_checksum=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
chunker_checksum=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb

PATH="$test_root/bin:$PATH" DEMO_TEST_LOG="$log_file" \
  "$script_dir/demo-model-download.sh" \
  --server-jar "$test_root/server.jar" \
  --static-backend-jar "$test_root/static-backend.jar" \
  --target "$target" \
  --config "$config" \
  --public-embedding-fallback \
  --parser-source https://models.example.invalid/en-parser-current.bin \
  --parser-checksum "$parser_checksum" \
  --chunker-source https://models.example.invalid/en-chunker-current.bin \
  --chunker-checksum "$chunker_checksum" > "$run_output"

assert_file_contains "$config" "model.name_finder_dl.bert_ner.path=$target/ner/model_quantized.onnx"
assert_file_contains "$config" "model.wordnet.default_id=oewn-2025"
assert_file_contains "$config" "model.subword.default_id=t5-small"
assert_file_contains "$config" "model.sentiment.default_id=multilingual-sentiment"
assert_file_contains "$config" "model.doccat.default_id=multilingual-sentiment"
assert_file_contains "$config" "model.embedder.default_id=potion-base-8m"
assert_file_contains "$config" "model.parser.default.path=$target/parser/en-parser-current.bin"
assert_file_contains "$config" "model.chunker.default.path=$target/chunker/en-chunker-current.bin"
assert_file_contains "$target/ner/labels.txt" "B-location"
assert_file_contains "$target/sentiment/categories.txt" "5 stars"
assert_file_contains "$target/MODEL-SOURCES.md" "Apache Maven runtime dependencies"
assert_file_contains "$target/MODEL-SOURCES.md" "No SourceForge 1.5 model is installed"
assert_file_contains "$run_output" "-cp"
assert_file_contains "$run_output" "$test_root/server.jar:$test_root/static-backend.jar"

first_count=$(wc -l < "$log_file")
[[ $first_count -eq 12 ]] || fail "expected 12 installer calls, found $first_count"

PATH="$test_root/bin:$PATH" DEMO_TEST_LOG="$log_file" \
  "$script_dir/demo-model-download.sh" \
  --server-jar "$test_root/server.jar" \
  --static-backend-jar "$test_root/static-backend.jar" \
  --target "$target" \
  --config "$config" \
  --public-embedding-fallback \
  --parser-source https://models.example.invalid/en-parser-current.bin \
  --parser-checksum "$parser_checksum" \
  --chunker-source https://models.example.invalid/en-chunker-current.bin \
  --chunker-checksum "$chunker_checksum"

second_count=$(wc -l < "$log_file")
[[ $second_count -eq "$first_count" ]] || fail "a matching rerun downloaded resources again"

own_model=$test_root/legal-minilm-full
mkdir -p "$own_model"
printf 'matrix\n' > "$own_model/model.safetensors"
printf '[PAD]\nlegal\n' > "$own_model/vocab.txt"
printf '{"normalize":true}\n' > "$own_model/config.json"
printf '{"do_lower_case":true}\n' > "$own_model/tokenizer_config.json"
own_log=$test_root/own-installer.log
own_config=$test_root/own-demo-server.properties
PATH="$test_root/bin:$PATH" DEMO_TEST_LOG="$own_log" \
  "$script_dir/demo-model-download.sh" \
  --server-jar "$test_root/server.jar" \
  --static-backend-jar "$test_root/static-backend.jar" \
  --target "$test_root/own-models" \
  --config "$own_config" \
  --embedding-dir "$own_model" \
  --embedding-model-id opennlp-legal-demo > /dev/null
assert_file_contains "$own_config" "model.embedder.opennlp-legal-demo.static.dir=$own_model"
assert_file_contains "$own_config" "model.embedder.default_id=opennlp-legal-demo"
assert_file_contains "$test_root/own-models/MODEL-SOURCES.md" "OpenNLP-distilled embedding model"
assert_file_contains "$test_root/own-models/MODEL-SOURCES.md" "opennlp-legal-demo"
own_matrix_checksum=$(sha256sum "$own_model/model.safetensors")
own_matrix_checksum=${own_matrix_checksum%% *}
assert_file_contains "$test_root/own-models/MODEL-SOURCES.md" "$own_matrix_checksum"
if grep -Fq 'minishlab/potion-base-8M' "$own_log"; then
  fail "the public fallback was downloaded despite an operator-trained embedding model"
fi

missing_embedding_output=$test_root/missing-embedding.txt
if PATH="$test_root/bin:$PATH" DEMO_TEST_LOG="$test_root/missing-embedding.log" \
  "$script_dir/demo-model-download.sh" \
  --server-jar "$test_root/server.jar" \
  --static-backend-jar "$test_root/static-backend.jar" \
  --target "$test_root/missing-embedding-models" > "$missing_embedding_output" 2>&1; then
  fail "the downloader silently selected a third-party embedding model"
fi
assert_file_contains "$missing_embedding_output" "--embedding-dir"
assert_file_contains "$missing_embedding_output" "--public-embedding-fallback"

list_output=$test_root/list.txt
"$script_dir/demo-model-download.sh" --list > "$list_output"
assert_file_contains "$list_output" "Bundled through opennlp-models-* Maven dependencies"
assert_file_contains "$list_output" "Parser: operator-supplied current model required"
if grep -Fq 'opennlp.sourceforge.net' "$list_output"; then
  fail "the default resource list references SourceForge"
fi

printf 'PASS: demo model downloader installs, configures, and resumes safely\n'
