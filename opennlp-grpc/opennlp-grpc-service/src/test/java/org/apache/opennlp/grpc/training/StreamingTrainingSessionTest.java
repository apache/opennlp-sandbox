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
package org.apache.opennlp.grpc.training;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.GrpcStatusCode;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingCompleted;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexDurability;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingModelPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingRequest;
import org.apache.opennlp.grpc.v1.StreamingTrainingStage;
import org.apache.opennlp.grpc.v1.StreamingTrainingStart;
import org.apache.opennlp.grpc.v1.StreamingTrainingUpdate;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingTrainingSessionTest {

  @Test
  void streamsDocumentShapesThenPublishesVocabularyModelAndIndex() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession session = new StreamingTrainingSession(pipeline, response);

    session.onNext(StreamingTrainingRequest.newBuilder().setStart(start(true)).build());
    session.onNext(document(41, document("doc-41", "Alice visits Oxford.")));
    session.onCompleted();

    assertNull(response.error);
    assertTrue(response.completed);
    assertEquals(6, response.values.size());
    assertTrue(response.values.get(0).hasAccepted());
    assertEquals(StreamingTrainingUpdate.UpdateCase.DOCUMENT,
        response.values.get(1).getUpdateCase());
    assertEquals(41, response.values.get(1).getDocument().getResult().getSequence());
    assertEquals("eng", response.values.get(1).getDocument().getResult()
        .getOk().getDocument().getDetectedLanguage());
    assertEquals(1, response.values.get(1).getDocument().getAcceptedDocuments());
    assertEquals(20, response.values.get(1).getDocument().getAcceptedCorpusBytes());
    assertEquals(StreamingTrainingStage.STREAMING_TRAINING_STAGE_VOCABULARY,
        response.values.get(2).getProgress().getStage());
    assertEquals(StreamingTrainingStage.STREAMING_TRAINING_STAGE_MODEL,
        response.values.get(3).getProgress().getStage());
    assertEquals(StreamingTrainingStage.STREAMING_TRAINING_STAGE_INDEX,
        response.values.get(4).getProgress().getStage());
    final StreamingTrainingCompleted completed = response.values.get(5).getCompleted();
    assertEquals("vocabulary-test", completed.getVocabulary().getArtifactId());
    assertEquals("model-test", completed.getModel().getArtifactId());
    assertEquals("workspace-test", completed.getIndex().getIndex().getIndexId());
    assertEquals(1, completed.getAcceptedDocuments());
    assertEquals(20, completed.getAcceptedCorpusBytes());

    assertEquals(1, pipeline.retained.size());
    final OpenNlpDocument retained = pipeline.retained.getFirst();
    assertEquals("doc-41", retained.getDocId());
    assertEquals("Alice visits Oxford.", retained.getRawText());
    assertEquals("source-test", retained.getMetadata().getFieldsOrThrow("source").getStringValue());
    assertEquals(0, retained.getSentencesCount());
    assertEquals("model-test", pipeline.indexedModelId);
  }

  @Test
  void returnsDocumentLocalAnalysisFailuresWithoutPoisoningTheSession() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    pipeline.analysis = document -> {
      if (document.getRawText().equals("bad")) {
        throw AnalysisException.invalidArgument("bad document");
      }
      return AnalyzeDocumentResponse.newBuilder().setDocument(document).build();
    };
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession session = new StreamingTrainingSession(pipeline, response);

    session.onNext(StreamingTrainingRequest.newBuilder().setStart(start(false)).build());
    session.onNext(document(1, document("bad", "bad")));
    session.onNext(document(2, document("good", "usable text")));
    session.onCompleted();

    assertNull(response.error);
    assertTrue(response.completed);
    assertTrue(response.values.get(1).getDocument().getResult().hasError());
    assertEquals("bad document",
        response.values.get(1).getDocument().getResult().getError().getMessage());
    assertEquals(0, response.values.get(1).getDocument().getAcceptedDocuments());
    assertTrue(response.values.get(2).getDocument().getResult().hasOk());
    assertEquals(1, response.values.get(2).getDocument().getAcceptedDocuments());
    assertEquals(1, pipeline.retained.size());
  }

  @Test
  void sanitizesUnexpectedAnalysisProviderRuntimeFailures() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    pipeline.analysis = document -> {
      throw new UnsupportedOperationException("provider implementation detail");
    };
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession session = new StreamingTrainingSession(pipeline, response);

    session.onNext(StreamingTrainingRequest.newBuilder().setStart(start(false)).build());
    session.onNext(document(1, document("bad", "provider failure")));

    assertNull(response.error);
    assertFalse(response.completed);
    assertEquals(2, response.values.size());
    assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INTERNAL,
        response.values.get(1).getDocument().getResult().getError().getCode());
    assertEquals("Internal server error",
        response.values.get(1).getDocument().getResult().getError().getMessage());
  }

  @Test
  void mapsUnexpectedVocabularyProviderRuntimeFailuresToPublicationFailure() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    pipeline.vocabularyFailure =
        new UnsupportedOperationException("provider implementation detail");
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession session = new StreamingTrainingSession(pipeline, response);

    session.onNext(StreamingTrainingRequest.newBuilder().setStart(start(false)).build());
    session.onNext(document(1, document("one", "usable text")));
    session.onCompleted();

    assertEquals(Status.Code.INTERNAL, status(response));
    assertEquals("StreamingTraining publication failed",
        ((StatusRuntimeException) response.error).getStatus().getDescription());
    assertFalse(response.error.getMessage().contains("provider implementation detail"));
  }

  @Test
  void rejectsMalformedFrameOrderAndCorpusOverflowBeforeAnalysis() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    pipeline.limits = new StreamingTrainingPipeline.Limits(1, 4, true, true);
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> missingStart =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession first = new StreamingTrainingSession(pipeline, missingStart);
    first.onNext(document(1, document("one", "text")));
    assertEquals(Status.Code.INVALID_ARGUMENT, status(missingStart));

    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> overflow =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession second = new StreamingTrainingSession(pipeline, overflow);
    second.onNext(StreamingTrainingRequest.newBuilder().setStart(start(false)).build());
    second.onNext(document(1, document("one", "abcde")));

    assertEquals(Status.Code.RESOURCE_EXHAUSTED, status(overflow));
    assertEquals(0, pipeline.analysisCalls);
  }

  @Test
  void rollsBackPublishedStagesWhenIndexingFails() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    pipeline.indexFailure = new IOException("index publication failed");
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamingTrainingSession session = new StreamingTrainingSession(pipeline, response);

    session.onNext(StreamingTrainingRequest.newBuilder().setStart(start(true)).build());
    session.onNext(document(1, document("one", "usable text")));
    session.onCompleted();

    assertEquals(Status.Code.INTERNAL, status(response));
    assertEquals(List.of("model:model-test", "vocabulary:vocabulary-test"), pipeline.rollbacks);
    assertFalse(response.completed);
  }

  @Test
  void rollsBackEveryPublicationAndReleasesAdmissionWhenTerminalWriteFails() {
    final RecordingPipeline pipeline = new RecordingPipeline();
    final AtomicInteger releases = new AtomicInteger();
    final StreamObserver<StreamingTrainingUpdate> response = new StreamObserver<>() {
      @Override
      public void onNext(StreamingTrainingUpdate update) {
        if (update.hasCompleted()) {
          throw new IllegalStateException("transport closed");
        }
      }

      @Override
      public void onError(Throwable throwable) {
      }

      @Override
      public void onCompleted() {
      }
    };
    final StreamingTrainingSession session =
        new StreamingTrainingSession(pipeline, response, releases::incrementAndGet);

    session.onNext(StreamingTrainingRequest.newBuilder().setStart(start(true)).build());
    session.onNext(document(1, document("one", "usable text")));
    session.onCompleted();
    session.onError(new IOException("late transport error"));

    assertEquals(List.of(
        "index:workspace-test", "model:model-test", "vocabulary:vocabulary-test"),
        pipeline.rollbacks);
    assertEquals(1, releases.get());
  }

  private static StreamingTrainingStart start(boolean index) {
    final StreamingTrainingStart.Builder start = StreamingTrainingStart.newBuilder()
        .setVocabulary(org.apache.opennlp.grpc.v1.LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId("dictionary-test")
            .setDisplayName("Session vocabulary")
            .setMinFrequency(1)
            .setMaxTerms(100)
            .setProvenanceSummary("Session test"))
        .setModel(StreamingTrainingModelPlan.newBuilder()
            .setTeacherId("teacher-test")
            .setDisplayName("Session model")
            .setProvenanceSummary("Session test"));
    if (index) {
      start.setIndex(StreamingTrainingIndexPlan.newBuilder()
          .setDisplayName("Session index")
          .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
              .setConfigId("sentences"))
          .setDurability(StreamingTrainingIndexDurability
              .STREAMING_TRAINING_INDEX_DURABILITY_PROCESS_LOCAL));
    }
    return start.build();
  }

  private static StreamingTrainingRequest document(
      long sequence, OpenNlpDocument document) {
    return StreamingTrainingRequest.newBuilder()
        .setDocument(AnalyzeStreamDocument.newBuilder()
            .setSequence(sequence)
            .setDocument(document))
        .build();
  }

  private static OpenNlpDocument document(String id, String text) {
    return OpenNlpDocument.newBuilder()
        .setDocId(id)
        .setRawText(text)
        .setMetadata(Struct.newBuilder().putFields("source",
            Value.newBuilder().setStringValue("source-test").build()))
        .addSentences(org.apache.opennlp.grpc.v1.AnnotatedSentence.getDefaultInstance())
        .build();
  }

  private static Status.Code status(
      TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> response) {
    return assertInstanceOf(StatusRuntimeException.class, response.error).getStatus().getCode();
  }

  private static final class RecordingPipeline implements StreamingTrainingPipeline {
    private Limits limits = new Limits(10, 1_000, true, true);
    private DocumentAnalysisSession analysis = document -> AnalyzeDocumentResponse.newBuilder()
        .setDocument(document.toBuilder().setDetectedLanguage("eng"))
        .build();
    private final List<OpenNlpDocument> retained = new ArrayList<>();
    private final List<String> rollbacks = new ArrayList<>();
    private int analysisCalls;
    private String indexedModelId;
    private IOException indexFailure;
    private RuntimeException vocabularyFailure;

    @Override
    public Limits limits() {
      return limits;
    }

    @Override
    public DocumentAnalysisSession openAnalysis(
        org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration configuration) {
      return document -> {
        analysisCalls++;
        return analysis.analyze(document);
      };
    }

    @Override
    public VocabularyArtifactDescriptor learnVocabulary(
        org.apache.opennlp.grpc.v1.LearnVocabularyStart start,
        List<OpenNlpDocument> documents) {
      if (vocabularyFailure != null) {
        throw vocabularyFailure;
      }
      retained.addAll(documents);
      return VocabularyArtifactDescriptor.newBuilder()
          .setArtifactId("vocabulary-test")
          .build();
    }

    @Override
    public StaticModelDescriptor trainModel(
        StreamingTrainingModelPlan plan, String vocabularyArtifactId,
        Consumer<String> progress, BooleanSupplier cancelled) {
      progress.accept("distilled");
      return StaticModelDescriptor.newBuilder().setArtifactId("model-test").build();
    }

    @Override
    public IndexPublication createIndex(
        StreamingTrainingStart start, StaticModelDescriptor model,
        List<OpenNlpDocument> documents, BooleanSupplier cancelled) throws IOException {
      if (indexFailure != null) {
        throw indexFailure;
      }
      indexedModelId = model.getArtifactId();
      final IndexDocumentsResponse response = IndexDocumentsResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder().setIndexId("workspace-test"))
          .build();
      return new IndexPublication(response,
          () -> rollbacks.add("index:" + response.getIndex().getIndexId()));
    }

    @Override
    public void deleteModel(String artifactId) {
      rollbacks.add("model:" + artifactId);
    }

    @Override
    public void deleteVocabulary(String artifactId) {
      rollbacks.add("vocabulary:" + artifactId);
    }
  }
}
