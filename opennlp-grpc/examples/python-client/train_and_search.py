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
"""Distill a static model, build a TurboQuant index, and search it through gRPC."""

from __future__ import annotations

import argparse
import sys

import grpc

from analyze_and_search import CORPUS, analysis_profile, search_all
from org.apache.opennlp.grpc.v1 import opennlp_document_pb2 as document_pb2
from org.apache.opennlp.grpc.v1 import opennlp_search_pb2 as search_pb2
from org.apache.opennlp.grpc.v1 import opennlp_search_pb2_grpc as search_grpc
from org.apache.opennlp.grpc.v1 import opennlp_service_pb2 as service_pb2
from org.apache.opennlp.grpc.v1 import opennlp_training_pb2 as training_pb2
from org.apache.opennlp.grpc.v1 import opennlp_training_pb2_grpc as training_grpc
from org.apache.opennlp.grpc.v1 import opennlp_vocabulary_pb2 as vocabulary_pb2
from org.apache.opennlp.grpc.v1 import opennlp_vocabulary_pb2_grpc as vocabulary_grpc

DICTIONARY = b"habeas corpus\nappellate court\nreversible error\nzoning variance\n"


def validate_server(
    vocabulary: vocabulary_grpc.OpenNlpVocabularyServiceStub,
    training: training_grpc.OpenNlpModelTrainingServiceStub,
    teacher_id: str,
) -> None:
    """Checks operator-gated prerequisites before uploading any data."""
    formats = vocabulary.ListDictionaryFormats(
        vocabulary_pb2.ListDictionaryFormatsRequest(), timeout=10
    )
    if not formats.writes_enabled:
        raise ValueError(
            "vocabulary writes are disabled; configure vocabulary.artifact_root"
        )
    teachers = training.ListTeachers(training_pb2.ListTeachersRequest(), timeout=10)
    configured = {teacher.teacher_id for teacher in teachers.teachers}
    if teacher_id not in configured:
        available = ", ".join(sorted(configured)) if configured else "none"
        raise ValueError(
            f"teacher {teacher_id!r} is not configured; available: {available}"
        )


def import_dictionary(
    vocabulary: vocabulary_grpc.OpenNlpVocabularyServiceStub,
) -> vocabulary_pb2.DictionaryArtifactDescriptor:
    """Imports the small domain dictionary used by this example."""
    frames = (
        vocabulary_pb2.ImportDictionaryRequest(
            start=vocabulary_pb2.ImportDictionaryStart(
                format=vocabulary_pb2.DictionaryFormatSelector(
                    standard=(
                        vocabulary_pb2.STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES
                    )
                ),
                display_name="Python training example dictionary",
                provenance_summary="Built-in terms from train_and_search.py",
            )
        ),
        vocabulary_pb2.ImportDictionaryRequest(data=DICTIONARY),
    )
    return vocabulary.ImportDictionary(iter(frames), timeout=30)


def training_start(
    dictionary_id: str,
    teacher_id: str,
    alias: str,
) -> training_pb2.StreamingTrainingRequest:
    """Builds the first frame for one bounded document-to-index session."""
    request = training_pb2.StreamingTrainingRequest()
    start = request.start
    start.analysis.profile.CopyFrom(analysis_profile())
    start.analysis.options.include_probabilities = True
    start.vocabulary.dictionary_artifact_id = dictionary_id
    start.vocabulary.display_name = "Python example vocabulary"
    start.vocabulary.min_frequency = 1
    start.vocabulary.max_terms = 10_000
    start.vocabulary.provenance_summary = "Three-document Python example corpus"
    start.model.teacher_id = teacher_id
    start.model.display_name = "Python example static model"
    start.model.provenance_summary = "Distilled by train_and_search.py"
    start.index.display_name = "Python example TurboQuant index"
    start.index.provider.standard = search_pb2.STANDARD_SEARCH_PROVIDER_TURBO_QUANT
    start.index.durability = (
        training_pb2.STREAMING_TRAINING_INDEX_DURABILITY_PROCESS_LOCAL
    )
    start.index.alias = alias

    sentence = start.index.chunk_embed_configs.add()
    sentence.config_id = "sentences"
    sentence.result_set_name = "Sentence chunks"
    sentence.chunking.strategy.standard = (
        document_pb2.STANDARD_CHUNKING_STRATEGY_SENTENCE
    )

    windows = start.index.chunk_embed_configs.add()
    windows.config_id = "token-windows"
    windows.result_set_name = "Eight-token windows"
    windows.chunking.strategy.standard = (
        document_pb2.STANDARD_CHUNKING_STRATEGY_TOKEN
    )
    windows.chunking.chunk_size = 8
    windows.chunking.chunk_overlap = 2
    return request


def train(
    training: training_grpc.OpenNlpModelTrainingServiceStub,
    dictionary_id: str,
    teacher_id: str,
    alias: str,
) -> training_pb2.StreamingTrainingCompleted:
    """Streams source documents and returns the terminal published artifacts."""
    frames = [training_start(dictionary_id, teacher_id, alias)]
    for sequence, (doc_id, text) in enumerate(CORPUS, start=1):
        frames.append(
            training_pb2.StreamingTrainingRequest(
                document=service_pb2.AnalyzeStreamDocument(
                    sequence=sequence,
                    document=document_pb2.OpenNlpDocument(
                        doc_id=doc_id,
                        raw_text=text,
                    ),
                )
            )
        )

    completed = None
    updates = training.StreamingTraining(iter(frames), timeout=1800)
    for update in updates:
        kind = update.WhichOneof("update")
        if kind == "accepted":
            print(
                "Training session accepted: "
                f"up to {update.accepted.max_documents} documents and "
                f"{update.accepted.max_corpus_bytes:,} UTF-8 bytes"
            )
        elif kind == "document":
            result = update.document.result
            if result.WhichOneof("result") != "ok":
                raise ValueError(
                    f"document {result.sequence} failed: {result.error.message}"
                )
            analyzed = result.ok.document
            print(
                f"  analyzed {analyzed.doc_id}: "
                f"{analyzed.analytics.total_tokens} tokens"
            )
        elif kind == "progress":
            print(f"  {update.progress.message}")
        elif kind == "completed":
            completed = update.completed

    if completed is None:
        raise ValueError("training stream ended without a completion descriptor")
    if not completed.HasField("model") or not completed.HasField("index"):
        raise ValueError("training completed without publishing a model and index")
    return completed


def print_model(completed: training_pb2.StreamingTrainingCompleted) -> None:
    """Prints the model and index identities needed by later clients."""
    model = completed.model
    index = completed.index.index
    print("\nPublished static model")
    print(f"  model id: {model.artifact_id}")
    print(f"  dimension: {model.dimension}")
    print(f"  learned term rows: {model.term_count}")
    print(f"  artifact SHA-256: {model.artifact_hash}")
    print("\nPublished TurboQuant index")
    print(f"  index id: {index.index_id}")
    print(f"  chunks: {index.size}")
    print(f"  vector space: {index.embedding_route.vector_space_id}")


def run(
    target: str,
    teacher_id: str,
    alias: str,
    cleanup_index: bool,
) -> None:
    """Runs dictionary import, training, indexing, and search."""
    with grpc.insecure_channel(target) as channel:
        grpc.channel_ready_future(channel).result(timeout=15)
        vocabulary = vocabulary_grpc.OpenNlpVocabularyServiceStub(channel)
        training = training_grpc.OpenNlpModelTrainingServiceStub(channel)
        search = search_grpc.OpenNlpSearchServiceStub(channel)
        validate_server(vocabulary, training, teacher_id)

        dictionary = import_dictionary(vocabulary)
        print(
            f"Imported dictionary {dictionary.artifact_id} "
            f"with {dictionary.entry_count} entries"
        )
        completed = train(training, dictionary.artifact_id, teacher_id, alias)
        print_model(completed)

        index_id = completed.index.index.index_id
        response = search_all(search, alias)
        print(f"\nSearch results ({len(response.hits)} exhaustive hits)")
        for rank, hit in enumerate(response.hits, start=1):
            print(
                f"  {rank:>2}. {hit.score:+.4f}  "
                f"{hit.document_id}/{hit.chunk_group_id}: {hit.indexed_text}"
            )
        if response.truncated:
            print("  Results were truncated by the server response-byte limit.")

        if cleanup_index:
            deleted = search.DeleteSearchIndex(
                search_pb2.DeleteSearchIndexRequest(index_id=index_id), timeout=10
            )
            if not deleted.deleted:
                raise ValueError(f"temporary index {index_id} was not deleted")
            print(f"\nDeleted temporary index {index_id}")


def parse_args() -> argparse.Namespace:
    """Parses command-line options."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--target",
        default="localhost:7071",
        help="gRPC server host:port (default: %(default)s)",
    )
    parser.add_argument(
        "--teacher-id",
        required=True,
        help="operator-configured teacher id returned by ListTeachers",
    )
    parser.add_argument(
        "--alias",
        default="python-trained-current",
        help="logical alias for the published index (default: %(default)s)",
    )
    parser.add_argument(
        "--cleanup-index",
        action="store_true",
        help="delete the process-local index after displaying results",
    )
    return parser.parse_args()


def main() -> int:
    """Runs the command and renders actionable gRPC failures."""
    args = parse_args()
    try:
        run(args.target, args.teacher_id, args.alias, args.cleanup_index)
        return 0
    except grpc.FutureTimeoutError:
        print(f"Could not connect to the gRPC server at {args.target}", file=sys.stderr)
    except grpc.RpcError as error:
        print(
            f"gRPC {error.code().name}: {error.details() or 'request failed'}",
            file=sys.stderr,
        )
    except ValueError as error:
        print(str(error), file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
