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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.opennlp.grpc.model.ClassicNerBackendFactory;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinyNerModel;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies PIPELINE_STEP_GEOCODE: location entities resolved against the library's
 * bundled Natural Earth gazetteer, enriching {@code NamedEntity.geo} and emitting the
 * {@code opennlp:geo} document-shape layer.
 */
class BasicDocumentAnalyzerGeocodeTest {

  private static final String TEXT = "The mayor visited Paris last spring .";

  @TempDir
  static Path modelDir;

  private static Path locationModelPath;

  @BeforeAll
  static void trainLocationModel() throws IOException {
    locationModelPath = TinyNerModel.trainLocationModel(modelDir.resolve("location-ner.bin"));
  }

  private static BasicDocumentAnalyzer analyzerWithLocationModel() {
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), new ModelBundleCache(
        Map.of(ClassicNerBackendFactory.KEY_PREFIX + "location" + ClassicNerBackendFactory.KEY_SUFFIX,
            locationModelPath.toString())));
  }

  private static AnalyzeDocumentRequest request(PipelineStep... steps) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder().setProfileId("geo");
    for (PipelineStep step : steps) {
      profile.addSteps(step);
    }
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile.build())
        .build();
  }

  private static Optional<AnnotationLayer> geoLayer(AnalyzeDocumentResponse response) {
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(l -> "opennlp:geo".equals(l.getId()))
        .findFirst();
  }

  @Test
  void locationEntitiesResolveAgainstTheBundledGazetteer() {
    final AnalyzeDocumentResponse response = analyzerWithLocationModel().analyze(request(
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_NER,
        PipelineStep.PIPELINE_STEP_GEOCODE));

    final List<NamedEntity> entities = new ArrayList<>();
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      entities.addAll(sentence.getEntitiesList());
    }
    assertFalse(entities.isEmpty(), "fixture produced no entities");
    final NamedEntity paris = entities.stream()
        .filter(e -> "Paris".equals(e.getText()))
        .findFirst().orElseThrow();
    assertTrue(paris.hasGeo(), "the Paris entity carries no geo resolution");
    assertEquals("Paris", paris.getGeo().getName());
    assertTrue(paris.getGeo().getLatitude() > 40.0d && paris.getGeo().getLatitude() < 55.0d,
        "Paris latitude is implausible: " + paris.getGeo().getLatitude());
    assertTrue(paris.getGeo().getConfidence() > 0.0d);
    assertFalse(paris.getGeo().getRecordId().isBlank());

    final AnnotationLayer geo = geoLayer(response).orElseThrow();
    assertTrue(geo.getGeoValues().getAnnotationsCount() > 0);
    assertEquals(paris.getAnnotationSpan().getStart(),
        geo.getGeoValues().getAnnotations(0).getSpan().getStart());
    assertEquals("Paris", geo.getGeoValues().getAnnotations(0).getResolution().getName());
  }

  @Test
  void geocodeWithoutNerFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzerWithLocationModel().analyze(request(
            PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_TOKENIZE,
            PipelineStep.PIPELINE_STEP_GEOCODE)));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
  }
}
