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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;
import org.apache.opennlp.grpc.model.DependencyParserRegistry;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;
import org.apache.opennlp.grpc.v1.TokenizerSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicDocumentAnalyzerDependencyTest {

  @TempDir
  Path tempDir;

  @Test
  void configuredDependencyParserEmitsTypedLayer() throws IOException {
    final Path modelPath = tempDir.resolve("dependency.bin");
    trainModel(modelPath);
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        DependencyParserRegistry.KEY_PREFIX + "tiny" + DependencyParserRegistry.KEY_SUFFIX,
        modelPath.toString()));
    final var bundle = cache.listBundles().stream()
        .filter(candidate -> candidate.getBundleId().equals(ProfileRegistry.DEPENDENCY_BUNDLE_ID))
        .findFirst()
        .orElseThrow();
    assertEquals(List.of(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_POS_TAG,
        PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE), bundle.getSupportedStepsList());
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
        cache.createProfileRegistry(), cache);
    final AnalysisProfile profile = AnalysisProfile.newBuilder()
        .setProfileId("dependency-test")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
        .addSteps(PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE)
        .setTokenizer(TokenizerSelector.newBuilder()
            .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE))
        .build();

    final OpenNlpDocument document = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("she eats fish"))
        .setProfile(profile)
        .build()).getDocument();

    final AnnotationLayer layer = document.getLayers().getLayersList().stream()
        .filter(candidate -> candidate.getIdentity().getStandard()
            == StandardLayer.STANDARD_LAYER_DEPENDENCIES)
        .findFirst()
        .orElseThrow();
    assertEquals("tiny", layer.getDependencyValues().getParserId());
    assertEquals(3, layer.getDependencyValues().getAnnotationsCount());
  }

  private static void trainModel(Path destination) throws IOException {
    final List<DependencySample> samples = new ArrayList<>();
    final DependencySample sample = new DependencySample(
        new String[] {"she", "eats", "fish"},
        new String[] {"PRP", "VBZ", "NN"},
        DependencyGraph.of(
            new int[] {1, -1, 1},
            new String[] {"nsubj", "root", "obj"}));
    for (int i = 0; i < 40; i++) {
      samples.add(sample);
    }
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    final DependencyModel model = DependencyParserME.train(
        "eng", ObjectStreamUtils.createObjectStream(samples), parameters);
    try (OutputStream output = Files.newOutputStream(destination)) {
      model.serialize(output);
    }
  }
}
