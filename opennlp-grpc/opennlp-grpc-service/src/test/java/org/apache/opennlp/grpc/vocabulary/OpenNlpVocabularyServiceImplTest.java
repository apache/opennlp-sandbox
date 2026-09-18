/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.vocabulary;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.DownloadVocabularyRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryStart;
import org.apache.opennlp.grpc.v1.LearnVocabularyRequest;
import org.apache.opennlp.grpc.v1.LearnVocabularyStart;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsRequest;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsResponse;
import org.apache.opennlp.grpc.v1.ListDictionariesRequest;
import org.apache.opennlp.grpc.v1.ListDictionariesResponse;
import org.apache.opennlp.grpc.v1.ListVocabulariesRequest;
import org.apache.opennlp.grpc.v1.ListVocabulariesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.v1.VocabularyArtifactChunk;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryEntryConsumer;

class OpenNlpVocabularyServiceImplTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void listsFormatsEvenWhenWritesAreDisabled() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(
        formats, VocabularyArtifactStore.fromConfiguration(Map.of(), formats));
    final CapturingObserver<ListDictionaryFormatsResponse> response = new CapturingObserver<>();

    service.listDictionaryFormats(ListDictionaryFormatsRequest.getDefaultInstance(), response);

    assertEquals(1, response.values.size());
    assertEquals(3, response.values.getFirst().getFormatsCount());
    assertFalse(response.values.getFirst().getWritesEnabled());
    assertTrue(response.completed);
    assertNull(response.error);
  }

  @Test
  void importsLearnsAndDownloadsThroughStreamingRpcBoundaries() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats, Map.of());
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(formats, store);
    final CapturingObserver<DictionaryArtifactDescriptor> imported = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> importRequests =
        service.importDictionary(imported);
    importRequests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());
    importRequests.onNext(ImportDictionaryRequest.newBuilder().setData(ByteString.copyFromUtf8(
        "habeas corpus\tA writ.\nliberty\tA right.\n")).build());
    importRequests.onCompleted();

    assertNull(imported.error);
    assertEquals(2, imported.values.getFirst().getEntryCount());
    final CapturingObserver<ListDictionariesResponse> dictionaries = new CapturingObserver<>();
    service.listDictionaries(ListDictionariesRequest.getDefaultInstance(), dictionaries);
    assertEquals(List.of(imported.values.getFirst()), dictionaries.values.getFirst().getDictionariesList());
    final CapturingObserver<VocabularyArtifactDescriptor> learned = new CapturingObserver<>();
    final StreamObserver<LearnVocabularyRequest> learnRequests = service.learnVocabulary(learned);
    learnRequests.onNext(LearnVocabularyRequest.newBuilder().setStart(
        LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId(imported.values.getFirst().getArtifactId())
            .setDisplayName("Demo vocabulary")
            .setMinFrequency(1)
            .setMaxTerms(20)
            .setProvenanceSummary("Authored corpus")).build());
    learnRequests.onNext(LearnVocabularyRequest.newBuilder().setDocument(
        OpenNlpDocument.newBuilder().setDocId("one")
            .setRawText("Liberty and habeas corpus protect people.")).build());
    learnRequests.onCompleted();

    assertNull(learned.error);
    assertTrue(learned.completed);
    final CapturingObserver<ListVocabulariesResponse> vocabularies = new CapturingObserver<>();
    service.listVocabularies(ListVocabulariesRequest.getDefaultInstance(), vocabularies);
    assertEquals(List.of(learned.values.getFirst()),
        vocabularies.values.getFirst().getVocabulariesList());
    final CapturingObserver<VocabularyArtifactChunk> downloaded = new CapturingObserver<>();
    service.downloadVocabulary(DownloadVocabularyRequest.newBuilder()
        .setArtifactId(learned.values.getFirst().getArtifactId()).build(), downloaded);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (VocabularyArtifactChunk chunk : downloaded.values) {
      bytes.write(chunk.getData().toByteArray());
    }
    final String tsv = bytes.toString(StandardCharsets.UTF_8);
    assertTrue(tsv.contains("habeas corpus\t1\tdictionary"));
    assertTrue(tsv.contains("protect\t1\tcorpus"));
    assertTrue(downloaded.completed);
  }

  @Test
  void learnsCorpusOnlyVocabularyThroughStreamingRpcBoundary() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(
        formats, enabledStore(formats, Map.of()));
    final CapturingObserver<VocabularyArtifactDescriptor> learned = new CapturingObserver<>();
    final StreamObserver<LearnVocabularyRequest> requests = service.learnVocabulary(learned);
    requests.onNext(LearnVocabularyRequest.newBuilder().setStart(
        LearnVocabularyStart.newBuilder()
            .setDisplayName("Corpus vocabulary")
            .setMinFrequency(1)
            .setMaxTerms(20)
            .setProvenanceSummary("Pasted corpus")).build());
    requests.onNext(LearnVocabularyRequest.newBuilder().setDocument(
        OpenNlpDocument.newBuilder().setDocId("one")
            .setRawText("Liberty and justice protect people.")).build());
    requests.onCompleted();

    assertNull(learned.error);
    assertTrue(learned.completed);
    assertEquals(1, learned.values.size());
    assertEquals("", learned.values.getFirst().getDictionaryArtifactId());
    assertEquals(0, learned.values.getFirst().getDictionaryTermCount());
    assertTrue(learned.values.getFirst().getCorpusTermCount() > 0);
  }

  @Test
  void rejectsOutOfOrderAndOversizedImportFramesOnce() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats,
        Map.of("vocabulary.max_dictionary_bytes", "8"));
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(formats, store);
    final CapturingObserver<DictionaryArtifactDescriptor> outOfOrder = new CapturingObserver<>();

    service.importDictionary(outOfOrder).onNext(ImportDictionaryRequest.newBuilder()
        .setData(ByteString.copyFromUtf8("early")).build());
    assertEquals(Status.Code.INVALID_ARGUMENT,
        Status.fromThrowable(outOfOrder.error).getCode());
    assertEquals(1, outOfOrder.errorCount);

    final CapturingObserver<DictionaryArtifactDescriptor> oversized = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> requests = service.importDictionary(oversized);
    requests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());
    requests.onNext(ImportDictionaryRequest.newBuilder()
        .setData(ByteString.copyFrom(new byte[9])).build());
    requests.onCompleted();
    assertEquals(Status.Code.INVALID_ARGUMENT,
        Status.fromThrowable(oversized.error).getCode());
    assertEquals(1, oversized.errorCount);
  }

  @Test
  void rejectsWritesWhenArtifactStorageIsDisabled() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(
        formats, VocabularyArtifactStore.fromConfiguration(Map.of(), formats));
    final CapturingObserver<DictionaryArtifactDescriptor> response = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> requests = service.importDictionary(response);

    requests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(response.error).getCode());
  }

  @Test
  void rejectsRepeatedStartsAndUnknownArtifactsWithTypedStatuses() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(
        formats, enabledStore(formats, Map.of()));
    final CapturingObserver<DictionaryArtifactDescriptor> repeated = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> importRequests =
        service.importDictionary(repeated);
    importRequests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());
    importRequests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());
    importRequests.onCompleted();
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(repeated.error).getCode());
    assertEquals(1, repeated.errorCount);

    final CapturingObserver<VocabularyArtifactDescriptor> unknownDictionary =
        new CapturingObserver<>();
    service.learnVocabulary(unknownDictionary).onNext(LearnVocabularyRequest.newBuilder()
        .setStart(LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId("dictionary-00000000-0000-0000-0000-000000000000")
            .setDisplayName("Unknown dictionary")
            .setMinFrequency(1)
            .setMaxTerms(10)
            .setProvenanceSummary("test"))
        .build());
    assertEquals(Status.Code.NOT_FOUND,
        Status.fromThrowable(unknownDictionary.error).getCode());

    final CapturingObserver<VocabularyArtifactChunk> unknownVocabulary = new CapturingObserver<>();
    service.downloadVocabulary(DownloadVocabularyRequest.newBuilder()
        .setArtifactId("vocabulary-00000000-0000-0000-0000-000000000000")
        .build(), unknownVocabulary);
    assertEquals(Status.Code.NOT_FOUND,
        Status.fromThrowable(unknownVocabulary.error).getCode());
  }

  @Test
  void admitsOnlyTheConfiguredNumberOfConcurrentWritesAndReleasesOnCancellation()
      throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(
        formats, enabledStore(formats, Map.of("vocabulary.max_concurrent_writes", "1")));
    final CapturingObserver<DictionaryArtifactDescriptor> first = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> firstRequests = service.importDictionary(first);
    firstRequests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());

    final CapturingObserver<DictionaryArtifactDescriptor> rejected = new CapturingObserver<>();
    service.importDictionary(rejected).onNext(
        ImportDictionaryRequest.newBuilder().setStart(importStart()).build());
    assertEquals(Status.Code.RESOURCE_EXHAUSTED,
        Status.fromThrowable(rejected.error).getCode());

    firstRequests.onError(Status.CANCELLED.asRuntimeException());
    final CapturingObserver<DictionaryArtifactDescriptor> admitted = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> admittedRequests =
        service.importDictionary(admitted);
    admittedRequests.onNext(ImportDictionaryRequest.newBuilder().setStart(importStart()).build());
    admittedRequests.onNext(ImportDictionaryRequest.newBuilder()
        .setData(ByteString.copyFromUtf8("liberty\tA right.\n")).build());
    admittedRequests.onCompleted();
    assertNull(admitted.error);
    assertTrue(admitted.completed);
  }

  @Test
  void mapsUnexpectedExtensionFailureToInternalWithoutLeakingDetails() throws Exception {
    final DictionaryFormatProvider failingProvider = new DictionaryFormatProvider() {
      @Override
      public DictionaryFormatDescriptor descriptor() {
        return DictionaryFormatDescriptor.newBuilder()
            .setFormat(DictionaryFormatSelector.newBuilder().setCustom("failing"))
            .setDisplayName("Failing fixture")
            .addMediaTypes("application/x-test")
            .build();
      }

      @Override
      public void read(java.io.InputStream input, DictionaryEntryConsumer entries) {
        throw new IllegalStateException("secret extension detail");
      }
    };
    final DictionaryFormatRegistry formats =
        new DictionaryFormatRegistry(java.util.List.of(failingProvider));
    final OpenNlpVocabularyServiceImpl service = new OpenNlpVocabularyServiceImpl(
        formats, enabledStore(formats, Map.of()));
    final CapturingObserver<DictionaryArtifactDescriptor> response = new CapturingObserver<>();
    final StreamObserver<ImportDictionaryRequest> requests = service.importDictionary(response);
    requests.onNext(ImportDictionaryRequest.newBuilder().setStart(
        ImportDictionaryStart.newBuilder()
            .setFormat(DictionaryFormatSelector.newBuilder().setCustom("failing"))
            .setDisplayName("Failing dictionary")
            .setProvenanceSummary("Authored fixture")).build());
    requests.onNext(ImportDictionaryRequest.newBuilder()
        .setData(ByteString.copyFromUtf8("input")).build());

    assertDoesNotThrow(requests::onCompleted);
    assertEquals(Status.Code.INTERNAL, Status.fromThrowable(response.error).getCode());
    assertFalse(Status.fromThrowable(response.error).getDescription()
        .contains("secret extension detail"));
  }

  private VocabularyArtifactStore enabledStore(
      DictionaryFormatRegistry formats, Map<String, String> overrides) throws Exception {
    final java.util.HashMap<String, String> configuration = new java.util.HashMap<>(overrides);
    configuration.put("vocabulary.artifact_root", temporaryDirectory.toString());
    return VocabularyArtifactStore.fromConfiguration(configuration, formats);
  }

  private static ImportDictionaryStart importStart() {
    return ImportDictionaryStart.newBuilder()
        .setFormat(DictionaryFormatSelector.newBuilder().setStandard(
            StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV))
        .setDisplayName("Demo dictionary")
        .setProvenanceSummary("Authored fixture")
        .build();
  }

  private static final class CapturingObserver<T> implements StreamObserver<T> {
    private final java.util.List<T> values = new java.util.ArrayList<>();
    private Throwable error;
    private int errorCount;
    private boolean completed;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
      errorCount++;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
