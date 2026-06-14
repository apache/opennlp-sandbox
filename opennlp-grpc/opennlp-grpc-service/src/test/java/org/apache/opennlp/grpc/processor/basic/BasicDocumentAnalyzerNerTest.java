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
package org.apache.opennlp.grpc.processor.basic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.model.ClassicNerBackendFactory;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinyNerModel;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies classic {@link opennlp.tools.namefind.NameFinderME} integration through the
 * analyzer, using a person model trained in-memory from a fixture corpus
 * (see {@link TinyNerModel}). Fully offline; no model is downloaded.
 */
class BasicDocumentAnalyzerNerTest {

  private static final String TEXT =
      "Pierre Vinken , 61 years old , will join the board as a nonexecutive director Nov. 29 . "
          + "Mr . Vinken is chairman of Elsevier N.V. , the Dutch publishing group .";

  @TempDir
  static Path modelDir;

  private static Path personModelPath;

  @BeforeAll
  static void trainPersonModel() throws IOException {
    personModelPath = TinyNerModel.trainPersonModel(modelDir.resolve("person-ner.bin"));
  }

  private static String personKey() {
    return ClassicNerBackendFactory.KEY_PREFIX + "person" + ClassicNerBackendFactory.KEY_SUFFIX;
  }

  private static BasicDocumentAnalyzer analyzerWithPersonModel() {
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(personKey(), personModelPath.toString()));
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(true), modelBundleCache);
  }

  @Test
  void detectsPersonEntitiesWithConfiguredModel() {
    final AnalyzeDocumentResponse response = analyzerWithPersonModel().analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId(ProfileRegistry.NER_PROFILE_ID)
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_NER)
                .setModelBundle(ModelBundleRef.newBuilder()
                    .setBundleId(ProfileRegistry.NER_BUNDLE_ID)
                    .build())
                .addNerEntityTypes("person")
                .build())
            // Request UTF-16 code-unit offsets so the spans are directly usable as Java
            // String indices below, independent of the default UTF-8 byte encoding.
            .setOptions(AnalysisOptions.newBuilder()
                .setIncludeProbabilities(true)
                .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
                .build())
            .build());

    int entityCount = 0;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      for (NamedEntity entity : sentence.getEntitiesList()) {
        assertEquals("person", entity.getEntityType());
        assertTrue(entity.hasAnnotationSpan());
        assertTrue(entity.getAnnotationSpan().getEnd() > entity.getAnnotationSpan().getStart());
        // The matched text really is "Vinken" — span maps back to the document correctly.
        final String matched = TEXT.substring(
            entity.getAnnotationSpan().getStart(), entity.getAnnotationSpan().getEnd());
        assertTrue(matched.contains("Vinken"), "unexpected entity text: '" + matched + "'");
        if (entity.hasProbability()) {
          assertTrue(entity.getProbability() > 0.0d);
        }
        entityCount++;
      }
    }
    assertTrue(entityCount > 0, "expected at least one person entity");
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_NER));
  }

  @Test
  void entityTypeFilterIsCaseInsensitive() {
    // "PERSON" (upper-case) must resolve the same finder as the lower-cased config key.
    final AnalyzeDocumentResponse response = analyzerWithPersonModel().analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_NER)
                .addNerEntityTypes("PERSON")
                .build())
            .build());

    int entityCount = 0;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      entityCount += sentence.getEntitiesCount();
    }
    assertTrue(entityCount > 0, "case-insensitive 'PERSON' filter found no entities");
  }

  @Test
  void enNerProfileIsListedWhenModelsConfigured() {
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(personKey(), personModelPath.toString()));

    assertTrue(modelBundleCache.listBundles().stream()
        .anyMatch(bundle -> ProfileRegistry.NER_BUNDLE_ID.equals(bundle.getBundleId())));
    assertTrue(modelBundleCache.getNameFinderRegistry().supportsEntityType("person"));
  }

  @Test
  void rejectsUnknownNerEntityTypeFilter() {
    final BasicDocumentAnalyzer analyzer = analyzerWithPersonModel();

    final AnalysisException error = assertThrows(
        AnalysisException.class,
        () -> analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_NER)
                .addNerEntityTypes("location")
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
    assertTrue(error.getMessage().contains("location"));
  }
}
