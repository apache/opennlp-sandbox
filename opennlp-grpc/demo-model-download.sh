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
default_jar=$script_dir/opennlp-grpc-service/target/opennlp-grpc-server-3.0.0-SNAPSHOT.jar
default_static_backend_jar=$script_dir/opennlp-grpc-backend-static/target/opennlp-grpc-backend-static-3.0.0-SNAPSHOT.jar
target=$script_dir/demo-models
config=
server_jar=$default_jar
static_backend_jar=$default_static_backend_jar
parser_source=
parser_checksum=
chunker_source=
chunker_checksum=
embedding_dir=
embedding_model_id=opennlp-legal-demo
public_embedding_fallback=false
list_only=false

ner_revision=24c7e5aba9ae350923357a6f0b92571be34037ec
ner_base=https://huggingface.co/Xenova/bert-base-NER/resolve/$ner_revision
ner_model_checksum=caaee70a5518ec7f9e46e5308fcc9263a8c227703a9ce46cf61c69a552349648
ner_vocab_checksum=eeaa9875b23b04b4c54ef759d03db9d1ba1554838f8fb26c5d96fa551df93d02

sentiment_revision=fad9b372f770c208d2efaf00d02f2cb3b61ec450
sentiment_base=https://huggingface.co/Xenova/bert-base-multilingual-uncased-sentiment/resolve/$sentiment_revision
sentiment_model_checksum=011c17a2902d1439f02ba6acfd90ed6ca4d3f5f059b9293e6bd999d094a87cf0
sentiment_vocab_checksum=87b44292b452f6c05afa49b2e488e7eedf79ea4f4c39db6f2f4b37764228ef3f

potion_revision=bf8b056651a2c21b8d2565580b8569da283cab23
potion_base=https://huggingface.co/minishlab/potion-base-8M/resolve/$potion_revision
potion_model_checksum=f65d0f325faadc1e121c319e2faa41170d3fa07d8c89abd48ca5358d9a223de2
potion_vocab_checksum=1394523a67ddd404a825428018c0582a6998bcfa044ecbcbf1f4d71adb94c61c
potion_config_checksum=2a6ac0e9aaa356a68a5688070db78fc3a464fefe85d2f06a1905ce3718687553
potion_tokenizer_checksum=6725995e3ab3039857ff5bd99178a7cdf42863abb04449e7bb31feb1f55fe567

sentencepiece_revision=df1b051c49625cf57a3d0d8d3863ed4d13564fe4
sentencepiece_source=https://huggingface.co/google-t5/t5-small/resolve/$sentencepiece_revision/spiece.model
sentencepiece_checksum=d60acb128cf7b7f2536e8f38a5b18a05535c9e14c7a355904270e15b0945ea86

wordnet_source=https://en-word.net/downloads/english-wordnet-2025.xml.gz
wordnet_checksum=9ca6d1dcb75f822fdd66617f7d9da48142ace38dd544d6ad5e2feca1674ad3fe

usage() {
  cat <<EOF
Usage: $(basename -- "$0") [options]

Downloads checksum-pinned demo resources through the OpenNLP resource installer and
writes a complete gRPC server model configuration.

Options:
  --target DIR              Installation directory (default: $target)
  --config FILE             Generated properties file (default: DIR/demo-server.properties)
  --server-jar FILE         Shaded gRPC server jar containing install-resource
  --static-backend-jar FILE Static embedding provider jar
  --parser-source URI       Current operator-approved OpenNLP parser model
  --parser-checksum HEX     SHA-256 or SHA-512 for --parser-source
  --chunker-source URI      Current operator-approved OpenNLP chunker model
  --chunker-checksum HEX    SHA-256 or SHA-512 for --chunker-source
  --embedding-dir DIR       Operator-trained static embedding model directory
  --embedding-model-id ID   Logical id for --embedding-dir (default: $embedding_model_id)
  --public-embedding-fallback
                            Use checksum-pinned Potion when no trained model is supplied
  --list                    Print the resource and feature map without downloading
  -h, --help                Show this help

The current Apache opennlp-models Maven artifacts already supply English sentence
detection, tokenization, POS tagging, lemmatization, and language detection. This
script does not download the retired SourceForge 1.5 models.
EOF
}

die() {
  printf 'demo-model-download: %s\n' "$1" >&2
  exit 2
}

require_value() {
  (($# >= 2)) || die "$1 requires a value"
}

while (($# > 0)); do
  case "$1" in
    --target)
      require_value "$@"
      target=$2
      shift 2
      ;;
    --config)
      require_value "$@"
      config=$2
      shift 2
      ;;
    --server-jar)
      require_value "$@"
      server_jar=$2
      shift 2
      ;;
    --static-backend-jar)
      require_value "$@"
      static_backend_jar=$2
      shift 2
      ;;
    --parser-source)
      require_value "$@"
      parser_source=$2
      shift 2
      ;;
    --parser-checksum)
      require_value "$@"
      parser_checksum=$2
      shift 2
      ;;
    --chunker-source)
      require_value "$@"
      chunker_source=$2
      shift 2
      ;;
    --chunker-checksum)
      require_value "$@"
      chunker_checksum=$2
      shift 2
      ;;
    --embedding-dir)
      require_value "$@"
      embedding_dir=$2
      shift 2
      ;;
    --embedding-model-id)
      require_value "$@"
      embedding_model_id=$2
      shift 2
      ;;
    --public-embedding-fallback)
      public_embedding_fallback=true
      shift
      ;;
    --list)
      list_only=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

print_resource_map() {
  cat <<EOF
Bundled through opennlp-models-* Maven dependencies:
  Language detection, English sentence detection, tokenization, POS, and lemmatization

Built into OpenNLP or the gRPC service:
  Normalization, Snowball/Porter/UniNE stemming, term vectors, and Natural Earth geocoding

Downloaded, checksum-pinned demo resources:
  NER: dslim/bert-base-NER ONNX export at $ner_revision (MIT)
  Sentiment and document category: multilingual BERT sentiment ONNX export at $sentiment_revision (MIT)
  Subwords: google-t5/t5-small SentencePiece model at $sentencepiece_revision (Apache-2.0)
  Lexical expansion: Open English WordNet 2025 WN-LMF (CC-BY-4.0)

Embedding choices:
  Preferred: --embedding-dir accepts an OpenNLP DistillModel output
  Explicit fallback: --public-embedding-fallback downloads minishlab/potion-base-8M (MIT)

Optional operator resources:
  Parser: operator-supplied current model required
  Syntactic chunker: operator-supplied current model required

No SourceForge 1.5 model is installed by this script.
EOF
}

if [[ $list_only == true ]]; then
  print_resource_map
  exit 0
fi

if [[ -z $config ]]; then
  config=$target/demo-server.properties
fi

validate_checksum() {
  local label=$1
  local checksum=$2
  local length=${#checksum}
  if [[ $length -ne 64 && $length -ne 128 ]]; then
    die "$label must be a 64-character SHA-256 or 128-character SHA-512 digest"
  fi
  case "$checksum" in
    *[!0-9A-Fa-f]*) die "$label contains a non-hexadecimal character" ;;
  esac
}

validate_external_pair() {
  local label=$1
  local source=$2
  local checksum=$3
  if [[ -n $source && -z $checksum ]]; then
    die "$label source requires its checksum option"
  fi
  if [[ -z $source && -n $checksum ]]; then
    die "$label checksum requires its source option"
  fi
  if [[ -n $source ]]; then
    case "$source" in
      https://*|file://*) ;;
      *) die "$label source must be an https or file URI" ;;
    esac
    validate_checksum "$label checksum" "$checksum"
  fi
}

validate_external_pair parser "$parser_source" "$parser_checksum"
validate_external_pair chunker "$chunker_source" "$chunker_checksum"

validate_model_id() {
  local value=$1
  [[ -n $value ]] || die "embedding model id must not be blank"
  local index
  local character
  for ((index = 0; index < ${#value}; index++)); do
    character=${value:index:1}
    case "$character" in
      [a-z0-9_-]) ;;
      *) die "embedding model id must contain only lower-case ASCII letters, digits, '_' or '-'" ;;
    esac
  done
}

validate_model_id "$embedding_model_id"

if [[ -n $embedding_dir && $public_embedding_fallback == true ]]; then
  die "choose either --embedding-dir or --public-embedding-fallback, not both"
fi
if [[ -z $embedding_dir && $public_embedding_fallback == false ]]; then
  die "pass --embedding-dir with an OpenNLP DistillModel output, or explicitly select --public-embedding-fallback"
fi

[[ -f $server_jar ]] || die "server jar not found: $server_jar; build it with ./mvnw package"
[[ -f $static_backend_jar ]] || die "static backend jar not found: $static_backend_jar; build it with ./mvnw package"
command -v java >/dev/null 2>&1 || die "java was not found on PATH"
command -v sha256sum >/dev/null 2>&1 || die "sha256sum was not found on PATH"

mkdir -p "$target"
target=$(CDPATH= cd -- "$target" && pwd -P)
config_parent=$(dirname -- "$config")
mkdir -p "$config_parent"
config_parent=$(CDPATH= cd -- "$config_parent" && pwd -P)
config=$config_parent/$(basename -- "$config")
state_dir=$target/.install-state
mkdir -p "$state_dir"

source_name() {
  local value=${1%%\?*}
  value=${value%%\#*}
  printf '%s\n' "${value##*/}"
}

install_resource() {
  local id=$1
  local source=$2
  local checksum=$3
  local destination=$4
  local installed_name=$5
  local installed=$destination/$installed_name
  local state=$state_dir/$id.state
  local recorded_source=
  local recorded_installed=

  if [[ -f $state ]]; then
    IFS= read -r recorded_source < "$state" || true
    IFS= read -r recorded_installed < <(sed -n '2p' "$state") || true
  fi
  if [[ -f $installed && $recorded_source == "$checksum" && -n $recorded_installed ]]; then
    local actual
    actual=$(sha256sum "$installed")
    actual=${actual%% *}
    if [[ $actual == "$recorded_installed" ]]; then
      printf 'Ready: %s\n' "$id"
      return
    fi
  fi

  printf 'Installing: %s\n' "$id"
  java -jar "$server_jar" install-resource \
    --source "$source" \
    --checksum "$checksum" \
    --target "$destination"
  [[ -f $installed ]] || die "$id installed without producing expected file: $installed"
  local installed_checksum
  installed_checksum=$(sha256sum "$installed")
  installed_checksum=${installed_checksum%% *}
  local state_tmp=$state.tmp.$$
  printf '%s\n%s\n' "$checksum" "$installed_checksum" > "$state_tmp"
  mv -f -- "$state_tmp" "$state"
}

write_text_file() {
  local destination=$1
  local content=$2
  mkdir -p "$(dirname -- "$destination")"
  local temp=$destination.tmp.$$
  printf '%s' "$content" > "$temp"
  mv -f -- "$temp" "$destination"
}

ner_dir=$target/ner
sentiment_dir=$target/sentiment
potion_dir=$target/embeddings/potion-base-8M
subword_dir=$target/subword/t5-small
wordnet_dir=$target/wordnet

install_resource ner-model "$ner_base/onnx/model_quantized.onnx" \
  "$ner_model_checksum" "$ner_dir" model_quantized.onnx
install_resource ner-vocab "$ner_base/vocab.txt" \
  "$ner_vocab_checksum" "$ner_dir" vocab.txt

# Map the model's abbreviated output classes to the service's canonical entity names.
# In particular, location makes the resulting entities eligible for Natural Earth geocoding.
ner_labels=$'O\nB-misc\nI-misc\nB-person\nI-person\nB-organization\nI-organization\n'
ner_labels+=$'B-location\nI-location\n'
write_text_file "$ner_dir/labels.txt" "$ner_labels"

install_resource sentiment-model "$sentiment_base/onnx/model_int8.onnx" \
  "$sentiment_model_checksum" "$sentiment_dir" model_int8.onnx
install_resource sentiment-vocab "$sentiment_base/vocab.txt" \
  "$sentiment_vocab_checksum" "$sentiment_dir" vocab.txt
write_text_file "$sentiment_dir/categories.txt" $'1 star\n2 stars\n3 stars\n4 stars\n5 stars\n'

if [[ -n $embedding_dir ]]; then
  [[ -d $embedding_dir ]] || die "embedding model directory not found: $embedding_dir"
  embedding_dir=$(CDPATH= cd -- "$embedding_dir" && pwd -P)
  [[ -f $embedding_dir/config.json ]] || die "embedding model lacks config.json: $embedding_dir"
  if [[ -f $embedding_dir/model.quantized ]]; then
    embedding_matrix=$embedding_dir/model.quantized
  elif [[ -f $embedding_dir/model.safetensors ]]; then
    embedding_matrix=$embedding_dir/model.safetensors
  else
    die "embedding model lacks model.quantized or model.safetensors: $embedding_dir"
  fi
  embedding_matrix_checksum=$(sha256sum "$embedding_matrix")
  embedding_matrix_checksum=${embedding_matrix_checksum%% *}
  embedding_provider_dir=$embedding_dir
  embedding_provider_id=$embedding_model_id
  embedding_vector_space=$embedding_model_id-sha256-$embedding_matrix_checksum
  embedding_source_note="- OpenNLP-distilled embedding model \`$embedding_provider_id\`, matrix SHA-256"
  embedding_source_note+=" \`$embedding_matrix_checksum\`. The operator supplies the model and its"
  embedding_source_note+=" license metadata."
else
  install_resource potion-model "$potion_base/model.safetensors" \
    "$potion_model_checksum" "$potion_dir" model.safetensors
  install_resource potion-vocab "$potion_base/vocab.txt" \
    "$potion_vocab_checksum" "$potion_dir" vocab.txt
  install_resource potion-config "$potion_base/config.json" \
    "$potion_config_checksum" "$potion_dir" config.json
  install_resource potion-tokenizer-config "$potion_base/tokenizer_config.json" \
    "$potion_tokenizer_checksum" "$potion_dir" tokenizer_config.json
  embedding_provider_dir=$potion_dir
  embedding_provider_id=potion-base-8m
  embedding_vector_space=potion-base-8m-$potion_revision
  embedding_source_note="- \`minishlab/potion-base-8M\`, revision \`$potion_revision\`, MIT, selected by"
  embedding_source_note+=" the explicit \`--public-embedding-fallback\` option."
fi

install_resource t5-small-sentencepiece "$sentencepiece_source" \
  "$sentencepiece_checksum" "$subword_dir" spiece.model
install_resource oewn-2025 "$wordnet_source" \
  "$wordnet_checksum" "$wordnet_dir" english-wordnet-2025.xml

parser_property='# Parser disabled: pass --parser-source and --parser-checksum for a current model.'
if [[ -n $parser_source ]]; then
  parser_name=$(source_name "$parser_source")
  [[ $parser_name == *.bin ]] || die "parser source file name must end in .bin"
  install_resource parser "$parser_source" "$parser_checksum" "$target/parser" "$parser_name"
  parser_property=model.parser.default.path=$target/parser/$parser_name
fi

chunker_property='# Syntactic chunker disabled: pass --chunker-source and --chunker-checksum for a current model.'
if [[ -n $chunker_source ]]; then
  chunker_name=$(source_name "$chunker_source")
  [[ $chunker_name == *.bin ]] || die "chunker source file name must end in .bin"
  install_resource chunker "$chunker_source" "$chunker_checksum" "$target/chunker" "$chunker_name"
  chunker_property=model.chunker.default.path=$target/chunker/$chunker_name
fi

config_content=$(cat <<EOF
# Generated by demo-model-download.sh. Rerun the script to refresh this file.
# The English sentence, tokenizer, POS, lemma, and language models are classpath resources.

model.name_finder_dl.bert_ner.path=$ner_dir/model_quantized.onnx
model.name_finder_dl.bert_ner.vocab=$ner_dir/vocab.txt
model.name_finder_dl.bert_ner.labels=$ner_dir/labels.txt

model.wordnet.oewn-2025.path=$wordnet_dir/english-wordnet-2025.xml
model.wordnet.default_id=oewn-2025

model.subword.t5-small.path=$subword_dir/spiece.model
model.subword.default_id=t5-small

model.sentiment_dl.multilingual-sentiment.path=$sentiment_dir/model_int8.onnx
model.sentiment_dl.multilingual-sentiment.vocab=$sentiment_dir/vocab.txt
model.sentiment_dl.multilingual-sentiment.categories=$sentiment_dir/categories.txt
model.sentiment.default_id=multilingual-sentiment

model.doccat_dl.multilingual-sentiment.path=$sentiment_dir/model_int8.onnx
model.doccat_dl.multilingual-sentiment.vocab=$sentiment_dir/vocab.txt
model.doccat_dl.multilingual-sentiment.categories=$sentiment_dir/categories.txt
model.doccat.default_id=multilingual-sentiment

model.embedder.$embedding_provider_id.static.dir=$embedding_provider_dir
model.embedder.$embedding_provider_id.static.vector_space_id=$embedding_vector_space
model.embedder.default_id=$embedding_provider_id

$parser_property
$chunker_property
EOF
)
write_text_file "$config" "$config_content"$'\n'

sources_content=$(cat <<EOF
# Demo model sources

This directory is generated by \`demo-model-download.sh\`. The server verifies every downloaded
resource against the checksum pinned in that script. Model and data files are not bundled in the
OpenNLP source or binary distribution.

## Already supplied by Apache OpenNLP

Language detection and the English sentence detector, tokenizer, POS tagger, and lemmatizer are
loaded from Apache Maven runtime dependencies (\`org.apache.opennlp:opennlp-models-*\`).

## Downloaded resources

- \`Xenova/bert-base-NER\`, revision \`$ner_revision\`, ONNX export of
  \`dslim/bert-base-NER\`, MIT.
- \`Xenova/bert-base-multilingual-uncased-sentiment\`, revision \`$sentiment_revision\`,
  ONNX export of the MIT-licensed \`nlptown/bert-base-multilingual-uncased-sentiment\` model.
$embedding_source_note
- \`google-t5/t5-small\`, revision \`$sentencepiece_revision\`, Apache-2.0. Only its SentencePiece
  tokenizer model is installed.
- Open English WordNet 2025 WN-LMF, CC-BY-4.0.

No SourceForge 1.5 model is installed. Parser and syntactic chunker models are installed only when
the operator supplies an explicit URI and checksum.
EOF
)
write_text_file "$target/MODEL-SOURCES.md" "$sources_content"$'\n'

printf '\nDemo model configuration: %s\n' "$config"
server_classpath=$server_jar:$static_backend_jar
printf 'Start the service with:\n  java -cp %q %s --config %q\n' \
  "$server_classpath" org.apache.opennlp.grpc.server.OpenNlpGrpcServer "$config"
if [[ -z $parser_source || -z $chunker_source ]]; then
  printf '\nParser and syntactic chunking remain off until current operator-approved models are supplied.\n'
fi
