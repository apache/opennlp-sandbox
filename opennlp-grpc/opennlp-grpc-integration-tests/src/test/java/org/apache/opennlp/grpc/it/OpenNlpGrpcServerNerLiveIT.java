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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ModelBundleInfo;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box NER integration test against the deployable server. A tiny person model is
 * trained in-memory (see {@link TinyNerModel}), written to disk, and handed to the
 * spawned {@code opennlp-grpc-server} process via {@code model.name_finder.person.path}.
 * The test then exercises named-entity recognition over real network sockets.
 *
 * <p>Fully hermetic: no model download, and the test fails (rather than silently skips)
 * if anything in the chain is broken.</p>
 */
class OpenNlpGrpcServerNerLiveIT {

  private static final String TEXT =
      "Pierre Vinken , 61 years old , will join the board as a nonexecutive director Nov. 29 . "
          + "Mr . Vinken is chairman of Elsevier N.V. , the Dutch publishing group .";

  private static final String NER_BUNDLE_ID = "en-ner";

  @TempDir
  static Path modelDir;

  private static LiveServerHarness harness;
  private static OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub client;

  @BeforeAll
  static void startServerWithNerModel() throws Exception {
    final Path personModel = TinyNerModel.trainPersonModel(modelDir.resolve("person-ner.bin"));
    assertTrue(Files.size(personModel) > 0, "trained NER model is empty");

    final Properties config = new Properties();
    config.setProperty("model.name_finder.person.path", personModel.toString());
    harness = LiveServerHarness.start(config);
    client = harness.client();
  }

  @AfterAll
  static void stopServer() {
    if (harness != null) {
      harness.close();
    }
  }

  @Test
  void modelCatalogReportsNerBundleWithoutClaimingModelLanguage() {
    final var bundles = client.listModelBundles(ListModelBundlesRequest.getDefaultInstance());
    final ModelBundleInfo nerBundle = bundles.getBundlesList().stream()
        .filter(b -> NER_BUNDLE_ID.equals(b.getBundleId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no en-ner bundle in catalog: " + bundles));

    assertTrue(nerBundle.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_NER));

    final ModelDescriptor nameFinder = nerBundle.getModelsList().stream()
        .filter(m -> m.getComponentType() == ComponentType.COMPONENT_TYPE_NAME_FINDER)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no name finder in bundle: " + nerBundle));

    assertEquals("person", nameFinder.getName());
    assertEquals("opennlp-me", nameFinder.getBackendId());
    // The operator-supplied model's language is unknown to the server: it must not claim one.
    assertTrue(nameFinder.getLocale().isEmpty(),
        "name finder descriptor must not claim a locale, was: " + nameFinder.getLocale());
    assertEquals(0, nameFinder.getLanguagesCount(),
        "name finder descriptor must not claim a language");
  }

  @Test
  void detectsPersonEntitiesAcrossProcess() {
    final AnalyzeDocumentResponse response = client.analyzeDocument(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setDocId("ner-1").setRawText(TEXT).build())
            .setProfile(nerProfile("person"))
            .build());

    int entityCount = 0;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      for (NamedEntity entity : sentence.getEntitiesList()) {
        assertEquals("person", entity.getEntityType());
        assertTrue(entity.getAnnotationSpan().getEnd() > entity.getAnnotationSpan().getStart());
        entityCount++;
      }
    }
    assertTrue(entityCount > 0, "expected at least one person entity in: " + response.getDocument());
  }

  @Test
  void enNerProfileIsAdvertisedAndUsableById() {
    // With NER models configured, the en-ner profile must be advertised and resolvable
    // by id alone (no inline profile) — the positive counterpart to hiding it otherwise.
    final var info = client.getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
    assertTrue(info.getAvailableProfileIdsList().contains("en-ner"),
        "en-ner not advertised: " + info.getAvailableProfileIdsList());

    final AnalyzeDocumentResponse response = client.analyzeDocument(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfileId("en-ner")
            .build());

    int entityCount = 0;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      entityCount += sentence.getEntitiesCount();
    }
    assertTrue(entityCount > 0, "en-ner profile-by-id found no entities");
  }

  @Test
  void nerEntityTypeFilterIsCaseInsensitive() {
    // Upper-case "PERSON" must resolve the same finder as the lower-cased config key.
    final AnalyzeDocumentResponse response = client.analyzeDocument(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(nerProfile("PERSON"))
            .build());

    int entityCount = 0;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      entityCount += sentence.getEntitiesCount();
    }
    assertTrue(entityCount > 0, "case-insensitive 'PERSON' filter found no entities");
  }

  @Test
  void unknownNerEntityTypeRejectedWithNotFound() {
    final StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
        () -> client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(nerProfile("location"))
            .build()));
    assertEquals(Status.Code.NOT_FOUND, error.getStatus().getCode());
  }

  private static AnalysisProfile nerProfile(String... entityTypes) {
    return AnalysisProfile.newBuilder()
        .setProfileId("live-ner")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_NER)
        .addAllNerEntityTypes(List.of(entityTypes))
        .build();
  }
}
