/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.webapp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.grpc.Status;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DownloadVocabularyRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryUpload;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelUpdate;
import org.apache.opennlp.grpc.v1.LearnVocabularyUpload;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsResponse;
import org.apache.opennlp.grpc.v1.ListDictionariesResponse;
import org.apache.opennlp.grpc.v1.ListVocabulariesResponse;
import org.apache.opennlp.grpc.v1.ListInstalledModelsResponse;
import org.apache.opennlp.grpc.v1.ListModelCatalogResponse;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.TeacherDescriptor;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrpcJsonVocabularyApiTest {

  @Test
  void composesDictionaryImportFromOneJsonUpload() {
    final StubVocabularyRpc vocabulary = new StubVocabularyRpc();
    final GrpcJsonApi api = api(vocabulary, new StubTrainingRpc());
    final byte[] request = """
        {"start":{"displayName":"Legal dictionary","provenanceSummary":"fixture",
         "format":{"standard":"STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV"}},
         "data":"aGVhZHdvcmQJZGVmaW5pdGlvbgo="}
        """.getBytes(StandardCharsets.UTF_8);

    final WebHttpResponse response = api.handle("POST", "/api/v1/import-dictionary", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"artifactId\":\"dictionary-1\""));
    assertEquals("Legal dictionary", vocabulary.importUpload.getStart().getDisplayName());
    assertEquals("headword\tdefinition\n",
        vocabulary.importUpload.getData().toStringUtf8());
  }

  @Test
  void composesVocabularyLearningFromOneJsonUpload() {
    final StubVocabularyRpc vocabulary = new StubVocabularyRpc();
    final GrpcJsonApi api = api(vocabulary, new StubTrainingRpc());
    final byte[] request = """
        {"start":{"dictionaryArtifactId":"dictionary-1","displayName":"Legal vocabulary",
         "minFrequency":1,"maxTerms":10,"provenanceSummary":"fixture"},
         "documents":[{"docId":"one","rawText":"Liberty matters."}]}
        """.getBytes(StandardCharsets.UTF_8);

    final WebHttpResponse response = api.handle("POST", "/api/v1/learn-vocabulary", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"artifactId\":\"vocabulary-1\""));
    assertEquals(1, vocabulary.learnUpload.getDocumentsCount());
    assertEquals("Liberty matters.",
        vocabulary.learnUpload.getDocuments(0).getRawText());
  }

  @Test
  void downloadsVocabularyTsvBytes() {
    final GrpcJsonApi api = api(new StubVocabularyRpc(), new StubTrainingRpc());
    final byte[] request = "{\"artifactId\":\"vocabulary-1\"}"
        .getBytes(StandardCharsets.UTF_8);

    final WebHttpResponse response = api.handle("POST", "/api/v1/download-vocabulary", request);

    assertEquals(200, response.status());
    assertEquals(GrpcJsonApi.TSV_CONTENT_TYPE, response.contentType());
    assertEquals("liberty\t3\tcorpus\n", response.bodyUtf8());
  }

  @Test
  void listsFormatsTeachersAndModels() {
    final GrpcJsonApi api = api(new StubVocabularyRpc(), new StubTrainingRpc());

    assertTrue(api.handle("GET", "/api/v1/dictionary-formats", new byte[0])
        .bodyUtf8().contains("\"writesEnabled\":true"));
    assertTrue(api.handle("GET", "/api/v1/dictionaries", new byte[0])
        .bodyUtf8().contains("\"artifactId\":\"dictionary-large\""));
    assertTrue(api.handle("GET", "/api/v1/vocabularies", new byte[0])
        .bodyUtf8().contains("\"artifactId\":\"vocabulary-legal\""));
    assertTrue(api.handle("GET", "/api/v1/teachers", new byte[0])
        .bodyUtf8().contains("\"teacherId\":\"mini\""));
    assertTrue(api.handle("GET", "/api/v1/static-models", new byte[0])
        .bodyUtf8().contains("\"artifactId\":\"static-model-1\""));
    assertTrue(api.handle("GET", "/api/v1/model-catalog", new byte[0])
        .bodyUtf8().contains("\"catalogId\":\"potion-base-8m\""));
    assertTrue(api.handle("GET", "/api/v1/installed-models", new byte[0])
        .bodyUtf8().contains("\"loaded\":true"));
  }

  @Test
  void deletesStaticModelsThroughJson() {
    final GrpcJsonApi api = api(new StubVocabularyRpc(), new StubTrainingRpc());
    final byte[] request = "{\"artifactId\":\"static-model-1\"}"
        .getBytes(StandardCharsets.UTF_8);

    final WebHttpResponse response = api.handle("POST", "/api/v1/delete-static-model", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"deleted\":true"));
  }

  @Test
  void streamsTrainingProgressLinesThenTheTerminalModel() throws Exception {
    final GrpcJsonApi api = api(new StubVocabularyRpc(), new StubTrainingRpc());
    final List<String> lines = new ArrayList<>();
    final byte[] request = trainRequest();

    final WebHttpResponse buffered = api.trainStaticModel(request, lines::add);

    assertNull(buffered);
    assertEquals(3, lines.size());
    assertTrue(lines.get(0).contains("\"progress\":\"resolving teacher\""));
    assertTrue(lines.get(1).contains("\"progress\":\"distilling\""));
    assertTrue(lines.get(2).contains("\"artifactId\":\"static-model-1\""));
  }

  @Test
  void streamsModelInstallationProgressThenTheInstalledModel() throws Exception {
    final GrpcJsonApi api = api(new StubVocabularyRpc(), new StubTrainingRpc());
    final List<String> lines = new ArrayList<>();
    final byte[] request = """
        {"catalogId":"potion-base-8m","revision":"revision-1",
         "licenseName":"MIT","licenseAcknowledged":true}
        """.getBytes(StandardCharsets.UTF_8);

    final WebHttpResponse buffered = api.installModel(request, lines::add);

    assertNull(buffered);
    assertEquals(2, lines.size());
    assertTrue(lines.get(0).contains("INSTALL_MODEL_STAGE_DOWNLOADING"));
    assertTrue(lines.get(1).contains("\"catalogId\":\"potion-base-8m\""));
  }

  @Test
  void returnsBufferedErrorWhenTrainingFailsBeforeStreaming() throws Exception {
    final StubTrainingRpc training = new StubTrainingRpc();
    training.failure = Status.NOT_FOUND.withDescription("Unknown vocabulary artifact")
        .asRuntimeException();
    final GrpcJsonApi api = api(new StubVocabularyRpc(), training);
    final List<String> lines = new ArrayList<>();

    final WebHttpResponse buffered = api.trainStaticModel(trainRequest(), lines::add);

    assertEquals(404, buffered.status());
    assertTrue(buffered.bodyUtf8().contains("Unknown vocabulary artifact"));
    assertTrue(lines.isEmpty());
  }

  @Test
  void appendsAnErrorLineWhenTrainingFailsMidStream() throws Exception {
    final StubTrainingRpc training = new StubTrainingRpc();
    training.failAfterFirstUpdate = true;
    final GrpcJsonApi api = api(new StubVocabularyRpc(), training);
    final List<String> lines = new ArrayList<>();

    final WebHttpResponse buffered = api.trainStaticModel(trainRequest(), lines::add);

    assertNull(buffered);
    assertEquals(2, lines.size());
    assertTrue(lines.get(0).contains("\"progress\":\"resolving teacher\""));
    assertTrue(lines.get(1).contains("\"code\":\"INTERNAL\""));
    assertTrue(lines.get(1).contains("teacher crashed"));
  }

  private static byte[] trainRequest() {
    return """
        {"vocabularyArtifactId":"vocabulary-1","teacherId":"mini",
         "displayName":"Legal static model","provenanceSummary":"fixture"}
        """.getBytes(StandardCharsets.UTF_8);
  }

  private static GrpcJsonApi api(VocabularyRpc vocabulary, TrainingRpc training) {
    return new GrpcJsonApi(new EmptyAnalysisRpc(), new EmptySearchRpc(), vocabulary, training);
  }

  private static final class EmptyAnalysisRpc implements AnalysisRpc {

    @Override
    public org.apache.opennlp.grpc.v1.ListOutputFormatsResponse listOutputFormats() {
      return org.apache.opennlp.grpc.v1.ListOutputFormatsResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.FormatDocumentResponse formatDocument(
        org.apache.opennlp.grpc.v1.FormatDocumentRequest request) {
      return org.apache.opennlp.grpc.v1.FormatDocumentResponse.getDefaultInstance();
    }
    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.AnalyzeStreamResponse> analyzeStream(
        java.util.List<org.apache.opennlp.grpc.v1.AnalyzeStreamRequest> frames) {
      return java.util.Collections.emptyIterator();
    }


    @Override
    public org.apache.opennlp.grpc.v1.GetServiceInfoResponse getServiceInfo() {
      return org.apache.opennlp.grpc.v1.GetServiceInfoResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ListModelBundlesResponse listModelBundles() {
      return org.apache.opennlp.grpc.v1.ListModelBundlesResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse analyze(
        org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest request) {
      return org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse.getDefaultInstance();
    }
  }

  private static final class StubVocabularyRpc implements VocabularyRpc {

    private ImportDictionaryUpload importUpload;
    private LearnVocabularyUpload learnUpload;

    @Override
    public ListDictionaryFormatsResponse listDictionaryFormats() {
      return ListDictionaryFormatsResponse.newBuilder().setWritesEnabled(true).build();
    }

    @Override
    public ListDictionariesResponse listDictionaries() {
      return ListDictionariesResponse.newBuilder()
          .addDictionaries(DictionaryArtifactDescriptor.newBuilder()
              .setArtifactId("dictionary-large")
              .setDisplayName("Large English dictionary")
              .setEntryCount(80_000))
          .build();
    }

    @Override
    public ListVocabulariesResponse listVocabularies() {
      return ListVocabulariesResponse.newBuilder()
          .addVocabularies(VocabularyArtifactDescriptor.newBuilder()
              .setArtifactId("vocabulary-legal")
              .setDisplayName("Legal vocabulary")
              .setTermCount(4_812))
          .build();
    }

    @Override
    public DictionaryArtifactDescriptor importDictionary(ImportDictionaryUpload upload) {
      this.importUpload = upload;
      return DictionaryArtifactDescriptor.newBuilder().setArtifactId("dictionary-1").build();
    }

    @Override
    public VocabularyArtifactDescriptor learnVocabulary(LearnVocabularyUpload upload) {
      this.learnUpload = upload;
      return VocabularyArtifactDescriptor.newBuilder().setArtifactId("vocabulary-1").build();
    }

    @Override
    public byte[] downloadVocabulary(DownloadVocabularyRequest request) {
      return "liberty\t3\tcorpus\n".getBytes(StandardCharsets.UTF_8);
    }
  }

  private static final class StubTrainingRpc implements TrainingRpc {

    private RuntimeException failure;
    private boolean failAfterFirstUpdate;

    @Override
    public ListTeachersResponse listTeachers() {
      return ListTeachersResponse.newBuilder()
          .addTeachers(TeacherDescriptor.newBuilder().setTeacherId("mini"))
          .setWritesEnabled(true)
          .build();
    }

    @Override
    public ListModelCatalogResponse listModelCatalog() {
      return ListModelCatalogResponse.newBuilder()
          .addModels(org.apache.opennlp.grpc.v1.ModelCatalogDescriptor.newBuilder()
              .setCatalogId("potion-base-8m"))
          .setInstallsEnabled(true)
          .build();
    }

    @Override
    public ListInstalledModelsResponse listInstalledModels() {
      return ListInstalledModelsResponse.newBuilder()
          .addModels(org.apache.opennlp.grpc.v1.InstalledModelDescriptor.newBuilder()
              .setCatalog(org.apache.opennlp.grpc.v1.ModelCatalogDescriptor.newBuilder()
                  .setCatalogId("potion-base-8m"))
              .setLoaded(true))
          .setInstallsEnabled(true)
          .build();
    }

    @Override
    public Iterator<InstallModelUpdate> installModel(InstallModelRequest request) {
      return List.of(
          InstallModelUpdate.newBuilder().setProgress(
              org.apache.opennlp.grpc.v1.InstallModelProgress.newBuilder()
                  .setStage(org.apache.opennlp.grpc.v1.InstallModelStage
                      .INSTALL_MODEL_STAGE_DOWNLOADING))
              .build(),
          InstallModelUpdate.newBuilder().setModel(
              org.apache.opennlp.grpc.v1.InstalledModelDescriptor.newBuilder()
                  .setCatalog(org.apache.opennlp.grpc.v1.ModelCatalogDescriptor.newBuilder()
                      .setCatalogId("potion-base-8m")))
              .build()).iterator();
    }

    @Override
    public Iterator<TrainStaticModelUpdate> trainStaticModel(TrainStaticModelRequest request) {
      if (failure != null) {
        throw failure;
      }
      final List<TrainStaticModelUpdate> updates = List.of(
          TrainStaticModelUpdate.newBuilder().setProgress("resolving teacher").build(),
          TrainStaticModelUpdate.newBuilder().setProgress("distilling").build(),
          TrainStaticModelUpdate.newBuilder().setModel(
              StaticModelDescriptor.newBuilder().setArtifactId("static-model-1")).build());
      final Iterator<TrainStaticModelUpdate> iterator = updates.iterator();
      if (!failAfterFirstUpdate) {
        return iterator;
      }
      return new Iterator<>() {
        private int served;

        @Override
        public boolean hasNext() {
          if (served >= 1) {
            throw Status.INTERNAL.withDescription("teacher crashed").asRuntimeException();
          }
          return iterator.hasNext();
        }

        @Override
        public TrainStaticModelUpdate next() {
          served++;
          return iterator.next();
        }
      };
    }

    @Override
    public ListStaticModelsResponse listStaticModels() {
      return ListStaticModelsResponse.newBuilder()
          .addModels(StaticModelDescriptor.newBuilder().setArtifactId("static-model-1"))
          .setWritesEnabled(true)
          .build();
    }

    @Override
    public DeleteStaticModelResponse deleteStaticModel(DeleteStaticModelRequest request) {
      return DeleteStaticModelResponse.newBuilder()
          .setArtifactId(request.getArtifactId())
          .setDeleted(true)
          .build();
    }
  }
}
