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

import java.util.List;
import java.util.Properties;

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in end-to-end test against a <em>real</em> HuggingFace TEI server, exercising
 * genuine model inference through the full deployment topology (test client → spawned
 * OpenNLP server jar → TEI container). Beyond plumbing, it asserts semantic properties
 * only a real embedding model can deliver: unit-norm vectors and topical similarity
 * ordering.
 *
 * <p>Start TEI with the helper script, then run with the target set:</p>
 *
 * <pre>
 * ./scripts/tei-server.sh start --cpu     # or --gpu
 * OPENNLP_TEI_TARGET=localhost:18080 mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify
 * </pre>
 *
 * <p>Without {@code OPENNLP_TEI_TARGET} the test is skipped, keeping the default build
 * Docker-free.</p>
 */
@EnabledIfEnvironmentVariable(named = "OPENNLP_TEI_TARGET", matches = ".+")
class RealTeiServerLiveIT {

  /** Three sentences: the first two share a topic, the third is unrelated. */
  private static final String TEXT = "The cat sat quietly on the mat. "
      + "A kitten was resting on the rug. "
      + "The stock market crashed badly yesterday.";

  private static LiveServerHarness harness;
  private static OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub client;

  @BeforeAll
  static void startServerAgainstRealTei() throws Exception {
    final Properties config = new Properties();
    config.setProperty("model.embedder.backend", "tei");
    config.setProperty("model.embedder.real.tei.target", System.getenv("OPENNLP_TEI_TARGET"));
    config.setProperty("model.embedder.tei.deadline_ms", "60000");
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
  void catalogReportsRealModelDimension() {
    final var bundles = client.listModelBundles(ListModelBundlesRequest.getDefaultInstance());
    final ModelDescriptor embedder = bundles.getBundles(0).getModelsList().stream()
        .filter(m -> m.getComponentType() == ComponentType.COMPONENT_TYPE_EMBEDDER)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no embedder in catalog"));
    assertEquals("tei", embedder.getBackendId());
    assertTrue(embedder.getEmbeddingDimension() >= 128,
        "real embedding models have substantial dimensions, got "
            + embedder.getEmbeddingDimension());
  }

  @Test
  void realEmbeddingsAreUnitNormalizedAndTopicallyCoherent() {
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("real-tei").setRawText(TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("real-embed")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
            .build())
        .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("real").build())
        .build());

    assertEquals(3, response.getDocument().getSentencesCount());
    final List<EmbeddingResult> embeddings = response.getDocument().getEmbeddingsList();
    assertEquals(3, embeddings.size());

    final float[][] vectors = new float[3][];
    for (int i = 0; i < 3; i++) {
      vectors[i] = toArray(embeddings.get(i));
      // TEI normalizes by default; a unit norm proves a real model produced the vector.
      assertEquals(1.0, norm(vectors[i]), 1e-2,
          "embedding " + i + " is not L2-normalized");
    }

    final double catKitten = cosine(vectors[0], vectors[1]);
    final double catMarket = cosine(vectors[0], vectors[2]);
    final double kittenMarket = cosine(vectors[1], vectors[2]);
    assertTrue(catKitten > catMarket,
        "expected cat~kitten (" + catKitten + ") > cat~market (" + catMarket + ")");
    assertTrue(catKitten > kittenMarket,
        "expected cat~kitten (" + catKitten + ") > kitten~market (" + kittenMarket + ")");
  }

  private static float[] toArray(EmbeddingResult embedding) {
    final float[] vector = new float[embedding.getVectorCount()];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = embedding.getVector(i);
    }
    return vector;
  }

  private static double norm(float[] vector) {
    double sum = 0;
    for (float value : vector) {
      sum += (double) value * value;
    }
    return Math.sqrt(sum);
  }

  private static double cosine(float[] a, float[] b) {
    double dot = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
    }
    return dot / (norm(a) * norm(b));
  }
}
