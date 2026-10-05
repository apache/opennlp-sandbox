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
#
# /// script
# requires-python = ">=3.10"
# dependencies = [
#   "grpcio>=1.62",
#   "protobuf>=4.25",
# ]
# ///
"""Cross-language end-to-end drive of the OpenNLP gRPC training lifecycle.

This script is deliberately not a Java client: it loads the FileDescriptorSet
the api jar ships at META-INF/opennlp/descriptors/opennlp-grpc-v1.protobin and
builds every request dynamically, so it proves the wire contract carries the
whole lifecycle without generated code. The flow: import a dictionary, learn a
vocabulary, analyze and index explicitly identified documents, alias the
workspace, scope it into a collection, watch the drift stream, persist, rebuild
blue/green with an alias swap, run a compound query with matched spans, seal,
and clean up.

Usage:
  uv run lifecycle_e2e.py --target localhost:7071 --descriptors opennlp-grpc-v1.protobin

Options:
  --embedding-model  Serving embedding model id (default: minilm).
  --teacher-id       When set, also distills a static model through
                     TrainStaticModel and reindexes into its vector space;
                     requires the server to be configured with that teacher.
"""

from __future__ import annotations

import argparse
import queue
import sys
import threading
from pathlib import Path

import grpc
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory

PACKAGE = "org.apache.opennlp.grpc.v1"

CORPUS = [
    "The writ of habeas corpus protects against unlawful detention.",
    "A petition for habeas corpus challenges the custody of a prisoner.",
    "The court held the detention unlawful and ordered release.",
]
DRIFT_TEXT = "Zoning variances govern rooftop apiaries downtown."


class Api:
    """Dynamic gRPC facade over the shipped descriptor set."""

    def __init__(self, channel: grpc.Channel, descriptors: Path) -> None:
        self.pool = descriptor_pool.DescriptorPool()
        file_set = descriptor_pb2.FileDescriptorSet.FromString(descriptors.read_bytes())
        pending = list(file_set.file)
        # Files register in dependency order; retry until no more progress.
        while pending:
            remaining = []
            for file in pending:
                try:
                    self.pool.Add(file)
                except KeyError:
                    remaining.append(file)
            if len(remaining) == len(pending):
                raise SystemExit("Could not register the shipped descriptor set")
            pending = remaining
        self.channel = channel

    def message(self, name: str):
        return message_factory.GetMessageClass(
            self.pool.FindMessageTypeByName(f"{PACKAGE}.{name}"))

    def enum(self, name: str, value: str) -> int:
        return self.pool.FindEnumTypeByName(f"{PACKAGE}.{name}").values_by_name[value].number

    def _method(self, service: str, name: str):
        descriptor = self.pool.FindServiceByName(f"{PACKAGE}.{service}").FindMethodByName(name)
        request_cls = message_factory.GetMessageClass(descriptor.input_type)
        response_cls = message_factory.GetMessageClass(descriptor.output_type)
        path = f"/{PACKAGE}.{service}/{name}"
        return path, request_cls.SerializeToString, response_cls.FromString

    def unary(self, service: str, name: str, request, timeout: float = 30.0):
        path, serialize, deserialize = self._method(service, name)
        return self.channel.unary_unary(
            path, request_serializer=serialize, response_deserializer=deserialize,
        )(request, timeout=timeout)

    def client_stream(self, service: str, name: str, frames, timeout: float = 60.0):
        path, serialize, deserialize = self._method(service, name)
        return self.channel.stream_unary(
            path, request_serializer=serialize, response_deserializer=deserialize,
        )(iter(frames), timeout=timeout)

    def bidi_stream(self, service: str, name: str, frames, timeout: float = 60.0):
        path, serialize, deserialize = self._method(service, name)
        return self.channel.stream_stream(
            path, request_serializer=serialize, response_deserializer=deserialize,
        )(iter(frames), timeout=timeout)

    def server_stream(self, service: str, name: str, request, timeout: float | None = None):
        path, serialize, deserialize = self._method(service, name)
        return self.channel.unary_stream(
            path, request_serializer=serialize, response_deserializer=deserialize,
        )(request, timeout=timeout)


class Watch:
    """Reads one collection watch stream on a thread with bounded waits."""

    def __init__(self, api: Api, collection_id: str) -> None:
        request = api.message("WatchCollectionRequest")(collection_id=collection_id)
        self.call = api.server_stream("OpenNlpSearchService", "WatchCollection", request)
        self.events: queue.Queue = queue.Queue()
        self.thread = threading.Thread(target=self._pump, daemon=True)
        self.thread.start()

    def _pump(self) -> None:
        try:
            for event in self.call:
                self.events.put(event)
        except grpc.RpcError as error:  # cancellation ends the stream
            self.events.put(error)

    def expect(self, kind: str, timeout: float = 30.0):
        event = self.events.get(timeout=timeout)
        if isinstance(event, grpc.RpcError):
            raise AssertionError(f"Watch stream failed: {event}")
        actual = event.kind
        expected = f"COLLECTION_EVENT_KIND_{kind}"
        check(event.DESCRIPTOR.fields_by_name["kind"].enum_type
              .values_by_number[actual].name == expected,
              f"expected watch event {expected}")
        return event

    def close(self) -> None:
        self.call.cancel()


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def step(message: str) -> None:
    print(f"[e2e] {message}", flush=True)


def status_code(error: grpc.RpcError) -> grpc.StatusCode:
    return error.code()  # type: ignore[attr-defined]


def import_dictionary(api: Api) -> str:
    frame_cls = api.message("ImportDictionaryRequest")
    start = frame_cls(start=dict(
        format=dict(standard=api.enum("StandardDictionaryFormat",
                                      "STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES")),
        display_name="Lifecycle dictionary",
        provenance_summary="lifecycle_e2e.py fixture",
    ))
    data = frame_cls(data="habeas corpus\nwrit\ndetention\n".encode("utf-8"))
    descriptor = api.client_stream("OpenNlpVocabularyService", "ImportDictionary",
                                   [start, data])
    check(descriptor.entry_count == 3, "dictionary entry count")
    step(f"imported dictionary {descriptor.artifact_id}")
    return descriptor.artifact_id


def learn_vocabulary(api: Api, dictionary_id: str) -> str:
    frame_cls = api.message("LearnVocabularyRequest")
    frames = [frame_cls(start=dict(
        dictionary_artifact_id=dictionary_id,
        display_name="Lifecycle vocabulary",
        min_frequency=1,
        max_terms=10_000,
        provenance_summary="lifecycle_e2e.py fixture",
    ))]
    frames += [frame_cls(document=dict(doc_id=f"corpus-{index}", raw_text=text))
               for index, text in enumerate(CORPUS, start=1)]
    descriptor = api.client_stream("OpenNlpVocabularyService", "LearnVocabulary", frames)
    check(descriptor.term_count > 0, "learned vocabulary is empty")
    tsv = b"".join(chunk.data for chunk in api.server_stream(
        "OpenNlpVocabularyService", "DownloadVocabulary",
        api.message("DownloadVocabularyRequest")(artifact_id=descriptor.artifact_id),
        timeout=30.0)).decode("utf-8")
    check("habeas corpus\t" in tsv, "multiword dictionary term missing from the vocabulary TSV")
    step(f"learned vocabulary {descriptor.artifact_id} ({descriptor.term_count} terms)")
    return descriptor.artifact_id


def streaming_training(api: Api, dictionary_id: str, teacher_id: str):
    """Proves the one-RPC document-to-artifact workflow and correlated replies."""
    frame_cls = api.message("StreamingTrainingRequest")
    start_frame = frame_cls()
    start = start_frame.start
    start.vocabulary.dictionary_artifact_id = dictionary_id
    start.vocabulary.display_name = "Streaming lifecycle vocabulary"
    start.vocabulary.min_frequency = 1
    start.vocabulary.max_terms = 10_000
    start.vocabulary.provenance_summary = "lifecycle_e2e.py streaming fixture"
    if teacher_id:
        start.model.teacher_id = teacher_id
        start.model.display_name = "Streaming lifecycle model"
        start.model.provenance_summary = "lifecycle_e2e.py streaming distillation"
        start.index.display_name = "Streaming lifecycle index"
        start.index.provider.standard = api.enum(
            "StandardSearchProvider", "STANDARD_SEARCH_PROVIDER_TURBO_QUANT")
        chunks = start.index.chunk_embed_configs.add()
        chunks.config_id = "sentences"
        chunks.chunking.algorithm = "sentence"
        start.index.durability = api.enum(
            "StreamingTrainingIndexDurability",
            "STREAMING_TRAINING_INDEX_DURABILITY_PROCESS_LOCAL")
        start.index.alias = "streaming-current"
    frames = [start_frame]
    for sequence, text in enumerate(CORPUS, start=1):
        frames.append(frame_cls(document=dict(
            sequence=sequence,
            document=dict(doc_id=f"streaming-{sequence}", raw_text=text),
        )))
    updates = list(api.bidi_stream(
        "OpenNlpModelTrainingService", "StreamingTraining", frames, timeout=1800.0))
    kinds = [update.WhichOneof("update") for update in updates]
    check(kinds[0] == "accepted", f"first streaming update was {kinds[0]}")
    document_updates = [update.document for update in updates
                        if update.WhichOneof("update") == "document"]
    check(len(document_updates) == len(CORPUS), "streaming document reply count")
    check([update.result.sequence for update in document_updates] == [1, 2, 3],
          "streaming document correlation sequence")
    check(all(update.result.WhichOneof("result") == "ok"
              for update in document_updates), "streaming analysis failure")
    completions = [update.completed for update in updates
                   if update.WhichOneof("update") == "completed"]
    check(len(completions) == 1, "streaming training terminal update count")
    completed = completions[0]
    check(completed.accepted_documents == len(CORPUS),
          "streaming accepted document count")
    check(bool(completed.vocabulary.artifact_id), "streaming vocabulary was not published")
    if teacher_id:
        check(completed.HasField("model"), "streaming model was not published")
        check(completed.HasField("index"), "streaming index was not published")
        response = compound_query(api, "streaming-current",
                                  min(3, completed.index.index.max_top_k))
        check(len(response.hits) > 0, "streaming index query returned no hits")
    step(f"StreamingTraining analyzed {len(document_updates)} documents and published "
         f"vocabulary {completed.vocabulary.artifact_id}")
    return completed


def analyze(api: Api, doc_id: str, text: str, model: str):
    request = api.message("AnalyzeDocumentRequest")(
        document=dict(doc_id=doc_id, raw_text=text),
        chunk_embed_configs=[dict(config_id="sentences",
                                  chunking=dict(algorithm="sentence"),
                                  embedding_model_ids=[model])],
    )
    response = api.unary("OpenNlpAnalysisService", "AnalyzeDocument", request, timeout=60.0)
    check(len(response.document.chunk_embedding_groups) == 1, "analysis emitted no chunk group")
    return response.document


def index_documents(api: Api, model: str, documents, index_id: str | None) -> object:
    request_cls = api.message("IndexDocumentsRequest")
    request = request_cls(
        display_name="Lifecycle workspace",
        embedding=dict(model_id=model),
    )
    if index_id is None:
        request.provider.standard = api.enum("StandardSearchProvider",
                                             "STANDARD_SEARCH_PROVIDER_TURBO_QUANT")
    else:
        request.index_id = index_id
    for document in documents:
        request.documents.append(document)
    return api.unary("OpenNlpSearchService", "IndexDocuments", request)


def compound_query(api: Api, index_or_alias: str, top_k: int):
    node_cls = api.message("QueryNode")
    phrase = node_cls()
    phrase.phrase.text = "habeas corpus"
    phrase.phrase.slop = 0
    semantic = node_cls()
    semantic.semantic.document.raw_text = "unlawful detention"
    query = node_cls()
    query.join.operator = api.enum("JoinOperator", "JOIN_OPERATOR_AND")
    query.join.operands.append(phrase)
    query.join.operands.append(semantic)
    request = api.message("SearchIndexRequest")(index_id=index_or_alias, top_k=top_k)
    request.compound_query.CopyFrom(query)
    return api.unary("OpenNlpSearchService", "SearchIndex", request)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, help="host:port of the gRPC server")
    parser.add_argument("--descriptors", required=True,
                        help="path to the shipped opennlp-grpc-v1.protobin")
    parser.add_argument("--embedding-model", default="minilm")
    parser.add_argument("--teacher-id", default="",
                        help="configured teacher for the optional distillation step")
    args = parser.parse_args()

    channel = grpc.insecure_channel(args.target)
    grpc.channel_ready_future(channel).result(timeout=30)
    api = Api(channel, Path(args.descriptors))

    providers = api.unary("OpenNlpSearchService", "ListSearchProviders",
                          api.message("ListSearchProvidersRequest")())
    instance_ids = [provider.instance_id for provider in providers.providers]
    check({"flat_float", "terms", "turbo_quant"} <= set(instance_ids),
          f"built-in provider instances missing: {instance_ids}")
    step(f"provider instances: {', '.join(instance_ids)}")

    dictionary_id = import_dictionary(api)
    streaming_training(api, dictionary_id, args.teacher_id)
    vocabulary_id = learn_vocabulary(api, dictionary_id)

    analyzed = [analyze(api, f"case-{index}", text, args.embedding_model)
                for index, text in enumerate(CORPUS, start=1)]
    created = index_documents(api, args.embedding_model, analyzed, None)
    index_id = created.index.index_id
    check(created.indexed_documents == len(CORPUS), "indexed document count")
    step(f"indexed {created.indexed_chunks} chunks into {index_id}")

    api.unary("OpenNlpSearchService", "SetIndexAlias",
              api.message("SetIndexAliasRequest")(alias="legal-current", index_id=index_id))
    step("alias legal-current -> " + index_id)

    saved = api.unary("OpenNlpSearchService", "SetCollection",
                      api.message("SetCollectionRequest")(
                          collection_id="legal",
                          display_name="Legal corpus",
                          member_index_ids=["legal-current"],
                          vocabulary_artifact_id=vocabulary_id,
                          drift_new_term_threshold=1))
    check(list(saved.collection.member_index_ids) == [index_id],
          "collection stored an unresolved member alias")
    check(saved.collection.drift.new_terms == 0,
          f"expected full vocabulary coverage, saw {saved.collection.drift.new_terms} new terms")
    step("collection legal saved with full vocabulary coverage")

    watch = Watch(api, "legal")
    watch.expect("SNAPSHOT")
    step("watch subscribed; snapshot received")

    drifted = analyze(api, "case-drift", DRIFT_TEXT, args.embedding_model)
    index_documents(api, args.embedding_model, [drifted], index_id)
    drift_event = watch.expect("DRIFT_THRESHOLD_CROSSED")
    check(drift_event.collection.drift.new_terms >= 1, "drift event carries no new terms")
    step(f"drift threshold crossed at {drift_event.collection.drift.new_terms} new terms")

    persisted = api.unary("OpenNlpSearchService", "PersistIndex",
                          api.message("PersistIndexRequest")(index_id="legal-current"))
    check(persisted.index.persisted, "persist did not set the persisted flag")
    watch.expect("INDEX_PERSISTED")
    step("workspace persisted; watch reported it")

    reindex_model = args.embedding_model
    if args.teacher_id:
        train_cls = api.message("TrainStaticModelRequest")
        updates = api.server_stream(
            "OpenNlpModelTrainingService", "TrainStaticModel",
            train_cls(vocabulary_artifact_id=vocabulary_id,
                      teacher_id=args.teacher_id,
                      display_name="Lifecycle model",
                      provenance_summary="lifecycle_e2e.py distillation"),
            timeout=1800.0)
        model_id = ""
        for update in updates:
            if update.WhichOneof("update") == "model":
                model_id = update.model.artifact_id
            else:
                step(f"training: {update.progress}")
        check(bool(model_id), "training stream ended without a model")
        model_event = watch.expect("MODEL_PUBLISHED")
        check(model_event.model_artifact_id == model_id, "model event id mismatch")
        step(f"model {model_id} published; watch reported it")
        reindex_model = model_id

    rebuilt = api.unary("OpenNlpSearchService", "ReindexIndex",
                        api.message("ReindexIndexRequest")(
                            index_id="legal-current",
                            embedding=dict(model_id=reindex_model),
                            alias="legal-current"),
                        timeout=300.0)
    check(rebuilt.source_index_id == index_id, "reindex source id mismatch")
    check(rebuilt.index.index_id != index_id, "reindex did not build a new index")
    step(f"blue/green rebuilt {rebuilt.index.index_id}; alias swapped")

    response = compound_query(api, "legal-current",
                              top_k=min(5, rebuilt.index.max_top_k))
    check(response.index.index_id == rebuilt.index.index_id,
          "the alias did not resolve to the rebuilt index")
    check(len(response.hits) > 0, "compound query returned no hits")
    spans = [(span.start, span.end, span.term)
             for hit in response.hits for span in hit.matched_spans]
    check(any(term == "habeas corpus" for _, _, term in spans),
          f"no matched span for the multiword term: {spans}")
    step(f"compound query returned {len(response.hits)} hits with matched spans")

    sealed = api.unary("OpenNlpSearchService", "SealIndex",
                       api.message("SealIndexRequest")(index_id=index_id))
    check(sealed.index.immutable, "seal did not set the immutable flag")
    try:
        index_documents(api, args.embedding_model, [drifted], index_id)
        raise AssertionError("indexing into a sealed index unexpectedly succeeded")
    except grpc.RpcError as error:
        check(status_code(error) == grpc.StatusCode.FAILED_PRECONDITION,
              f"sealed index mutation reported {status_code(error)}")
    step("sealed workspace rejects further indexing")

    watch.close()
    deleted = api.unary("OpenNlpSearchService", "DeleteCollection",
                        api.message("DeleteCollectionRequest")(collection_id="legal"))
    check(deleted.deleted, "collection deletion failed")
    listing = api.unary("OpenNlpSearchService", "ListCollections",
                        api.message("ListCollectionsRequest")())
    check(len(listing.collections) == 0, "collections remained after deletion")
    step("collection deleted; lifecycle complete")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except AssertionError as failure:
        print(f"[e2e] FAILED: {failure}", file=sys.stderr, flush=True)
        sys.exit(1)
