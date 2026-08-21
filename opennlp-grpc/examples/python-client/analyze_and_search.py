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
"""Analyze documents, build a TurboQuant index, and search it through gRPC."""

from __future__ import annotations

import argparse
import sys
from collections.abc import Sequence

import grpc

from org.apache.opennlp.grpc.v1 import opennlp_document_pb2 as document_pb2
from org.apache.opennlp.grpc.v1 import opennlp_pipeline_pb2 as pipeline_pb2
from org.apache.opennlp.grpc.v1 import opennlp_search_pb2 as search_pb2
from org.apache.opennlp.grpc.v1 import opennlp_search_pb2_grpc as search_grpc
from org.apache.opennlp.grpc.v1 import opennlp_service_pb2 as service_pb2
from org.apache.opennlp.grpc.v1 import opennlp_service_pb2_grpc as service_grpc

CORPUS = (
    (
        "habeas",
        "The writ of habeas corpus protects a prisoner from unlawful detention. "
        "A court may order the custodian to release the prisoner.",
    ),
    (
        "appeal",
        "The appellate court reviews the trial record for reversible error. "
        "A timely notice preserves the right to appeal.",
    ),
    (
        "zoning",
        "A city may regulate rooftop apiaries through its zoning code. "
        "The applicant requested a variance for three beehives.",
    ),
)

BASE_STEPS = (
    pipeline_pb2.PIPELINE_STEP_LANGUAGE_DETECT,
    pipeline_pb2.PIPELINE_STEP_SENTENCE_DETECT,
    pipeline_pb2.PIPELINE_STEP_TOKENIZE,
    pipeline_pb2.PIPELINE_STEP_POS_TAG,
    pipeline_pb2.PIPELINE_STEP_LEMMATIZE,
    pipeline_pb2.PIPELINE_STEP_STEM,
    pipeline_pb2.PIPELINE_STEP_TERM_VECTOR,
)


def select_embedding_model(
    analysis: service_grpc.OpenNlpAnalysisServiceStub,
    requested: str | None,
) -> str:
    """Selects a configured logical embedding model."""
    response = analysis.ListModelBundles(service_pb2.ListModelBundlesRequest(), timeout=10)
    models = sorted({
        model.name
        for bundle in response.bundles
        for model in bundle.models
        if model.embedding_dimension > 0
    })
    if requested:
        if requested not in models:
            available = ", ".join(models) if models else "none"
            raise ValueError(
                f"embedding model {requested!r} is not configured; available: {available}"
            )
        return requested
    if len(models) == 1:
        return models[0]
    if not models:
        raise ValueError(
            "the server has no embedding model; configure one before running search"
        )
    raise ValueError(
        "the server has several embedding models; pass --embedding-model with one of: "
        + ", ".join(models)
    )


def analysis_profile() -> pipeline_pb2.AnalysisProfile:
    """Builds a model-backed profile available in the default server."""
    profile = pipeline_pb2.AnalysisProfile(steps=BASE_STEPS)
    profile.stemmer.algorithm = document_pb2.STEMMER_ALGORITHM_SNOWBALL
    profile.stemmer.language = "en"
    profile.term_vector.mode = document_pb2.TERM_VECTOR_MODE_FULL
    profile.term_vector.source_layer.standard = document_pb2.STANDARD_LAYER_LEMMAS
    return profile


def chunk_configs(model_id: str) -> list[service_pb2.ChunkEmbedConfigEntry]:
    """Builds sentence and overlapping token-window projections."""
    sentence = service_pb2.ChunkEmbedConfigEntry(
        config_id="sentences",
        result_set_name="Sentence chunks",
        embedding_model_ids=[model_id],
    )
    sentence.chunking.strategy.standard = (
        document_pb2.STANDARD_CHUNKING_STRATEGY_SENTENCE
    )

    windows = service_pb2.ChunkEmbedConfigEntry(
        config_id="token-windows",
        result_set_name="Eight-token windows",
        embedding_model_ids=[model_id],
    )
    windows.chunking.strategy.standard = document_pb2.STANDARD_CHUNKING_STRATEGY_TOKEN
    windows.chunking.chunk_size = 8
    windows.chunking.chunk_overlap = 2
    return [sentence, windows]


def analyze_documents(
    analysis: service_grpc.OpenNlpAnalysisServiceStub,
    model_id: str,
) -> list[document_pb2.OpenNlpDocument]:
    """Analyzes the sample corpus and returns document-shaped responses."""
    profile = analysis_profile()
    chunks = chunk_configs(model_id)
    analyzed = []
    for doc_id, text in CORPUS:
        request = service_pb2.AnalyzeDocumentRequest(
            document=document_pb2.OpenNlpDocument(doc_id=doc_id, raw_text=text),
            profile=profile,
            options=pipeline_pb2.AnalysisOptions(include_probabilities=True),
            chunk_embed_configs=chunks,
        )
        analyzed.append(analysis.AnalyzeDocument(request, timeout=60).document)
    return analyzed


def print_analysis(documents: Sequence[document_pb2.OpenNlpDocument]) -> None:
    """Prints a compact, readable view of the document-shape response."""
    print("\nDocument analysis")
    for document in documents:
        analytics = document.analytics
        layer_ids = ", ".join(layer.id for layer in document.layers.layers)
        print(
            f"  {document.doc_id}: {analytics.total_sentences} sentences, "
            f"{analytics.total_tokens} tokens, {analytics.unique_lemma_count} unique lemmas"
        )
        first_tokens = document.sentences[0].tokens[:8]
        token_summary = " ".join(
            f"{token.text}/{token.pos_tag}/{token.lemma}" for token in first_tokens
        )
        print(f"    first tokens: {token_summary}")
        print(f"    layers: {layer_ids}")


def index_documents(
    search: search_grpc.OpenNlpSearchServiceStub,
    model_id: str,
    documents: Sequence[document_pb2.OpenNlpDocument],
) -> search_pb2.IndexDocumentsResponse:
    """Creates one process-local TurboQuant index."""
    request = search_pb2.IndexDocumentsRequest(
        display_name="Python quickstart corpus",
        documents=documents,
        embedding=document_pb2.EmbeddingSelector(model_id=model_id),
        provider=search_pb2.SearchProviderSelector(
            standard=search_pb2.STANDARD_SEARCH_PROVIDER_TURBO_QUANT
        ),
    )
    return search.IndexDocuments(request, timeout=60)


def search_all(
    search: search_grpc.OpenNlpSearchServiceStub,
    index_id: str,
) -> search_pb2.SearchIndexResponse:
    """Runs a document-shaped semantic query and requests every TurboQuant hit."""
    request = search_pb2.SearchIndexRequest(
        index_id=index_id,
        query=document_pb2.OpenNlpDocument(
            doc_id="query-1",
            raw_text="Which court remedy protects a prisoner from unlawful custody?",
        ),
        all_hits=True,
    )
    return search.SearchIndex(request, timeout=60)


def run(target: str, requested_model: str | None, cleanup: bool) -> None:
    """Runs the complete analyze, index, and search example."""
    with grpc.insecure_channel(target) as channel:
        grpc.channel_ready_future(channel).result(timeout=15)
        analysis = service_grpc.OpenNlpAnalysisServiceStub(channel)
        search = search_grpc.OpenNlpSearchServiceStub(channel)

        info = analysis.GetServiceInfo(service_pb2.GetServiceInfoRequest(), timeout=10)
        print(
            f"Connected to OpenNLP {info.opennlp_version}, API {info.api_version}, "
            f"service {info.service_version}"
        )
        print(f"Server text limit: {info.max_text_bytes:,} UTF-8 bytes")

        model_id = select_embedding_model(analysis, requested_model)
        print(f"Embedding model: {model_id}")
        documents = analyze_documents(analysis, model_id)
        print(f"Analyzed {len(documents)} documents")
        print_analysis(documents)

        created = index_documents(search, model_id, documents)
        index_id = created.index.index_id
        print(
            f"\nTurboQuant index {index_id}: {created.indexed_documents} documents, "
            f"{created.indexed_chunks} chunks"
        )
        if not created.index.supports_all_hits:
            raise ValueError("the TurboQuant index did not advertise exhaustive search")

        try:
            response = search_all(search, index_id)
            print(f"\nSearch results ({len(response.hits)} exhaustive hits)")
            for rank, hit in enumerate(response.hits, start=1):
                print(
                    f"  {rank:>2}. {hit.score:+.4f}  "
                    f"{hit.document_id}/{hit.chunk_group_id}: {hit.emitted_text}"
                )
            if response.truncated:
                print("  Results were truncated by the server response-byte limit.")
        finally:
            if cleanup:
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
        "--embedding-model",
        help="logical embedding model id; inferred when exactly one is configured",
    )
    parser.add_argument(
        "--cleanup",
        action="store_true",
        help="delete the process-local index after displaying results",
    )
    return parser.parse_args()


def main() -> int:
    """Runs the command and renders actionable gRPC failures."""
    args = parse_args()
    try:
        run(args.target, args.embedding_model, args.cleanup)
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
