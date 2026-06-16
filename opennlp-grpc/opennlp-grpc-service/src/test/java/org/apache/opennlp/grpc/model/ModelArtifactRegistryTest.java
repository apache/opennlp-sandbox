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
package org.apache.opennlp.grpc.model;

import java.util.Map;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.basic.BasicDocumentAnalyzer;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ComponentModelRef;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelArtifactRegistryTest {

  @Test
  void bundleCatalogIncludesClassicModelHashes() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of());
    final var bundle = cache.listBundles().stream()
        .filter(info -> info.getBundleId().equals("en-basic"))
        .findFirst()
        .orElseThrow();
    assertFalse(bundle.getModelsList().isEmpty());
    for (var model : bundle.getModelsList()) {
      assertFalse(model.getHash().isBlank(), model.getName());
    }
  }

  @Test
  void componentModelsPinningAcceptsLoadedPosTaggerHash() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of());
    final String posHash = cache.getArtifactRegistry().artifacts(
        ComponentType.COMPONENT_TYPE_POS_TAGGER).get(0).hash();

    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("The dog barked.").build())
        .setProfile(AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
            .setModelBundle(ModelBundleRef.newBuilder()
                .addComponentModels(ComponentModelRef.newBuilder()
                    .setComponentType(ComponentType.COMPONENT_TYPE_POS_TAGGER)
                    .setModelHash(posHash)
                    .build())
                .build())
            .build())
        .build());

    assertTrue(response.getDocument().getSentences(0).getTokens(0).hasPosTag());
  }

  @Test
  void componentModelsRejectsUnknownHash() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello.").build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .setModelBundle(ModelBundleRef.newBuilder()
                    .addComponentModels(ComponentModelRef.newBuilder()
                        .setComponentType(ComponentType.COMPONENT_TYPE_TOKENIZER)
                        .setModelHash("deadbeef")
                        .build())
                    .build())
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
