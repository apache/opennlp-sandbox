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
package org.apache.opennlp.grpc.testing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.AlgorithmType;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains a tiny two-category ({@code weather} / {@code finance}) {@link DoccatModel} from an
 * in-memory corpus, entirely offline. Tests use this instead of downloading a real categorizer,
 * so the suite is hermetic (no network, no silent skip) and carries no third-party model
 * provenance into the build.
 *
 * <p>The vocabulary of each class is deliberately disjoint so the trained model confidently
 * separates the fixture documents; it is a wiring/integration fixture, not a model of any
 * real-world quality.</p>
 */
public final class TinyDoccatModel {

  private TinyDoccatModel() {
  }

  /** Strongly weather-flavored training documents. */
  private static final String[] WEATHER_DOCS = {
      "rain storm clouds thunder forecast sunny snow wind temperature humidity",
      "the forecast predicts heavy rain and strong wind across the coast tomorrow",
      "snow and freezing temperature with icy wind expected this winter weekend",
      "sunny skies clear clouds warm temperature perfect beach weather today",
  };

  /** Strongly finance-flavored training documents. */
  private static final String[] FINANCE_DOCS = {
      "stocks market shares dividend investor portfolio earnings revenue profit bank",
      "the company reported record quarterly earnings lifting its share price sharply",
      "investors bought bonds and shares as the central bank cut interest rates",
      "the merger boosted revenue profit and the dividend paid to shareholders",
  };

  /**
   * Trains the categorizer and serializes it to {@code target}.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainTopicModel(Path target) throws IOException {
    final List<DocumentSample> samples = new ArrayList<>();
    // Repeat the small corpus so the maxent trainer converges on the fixture vocabulary.
    for (int i = 0; i < 60; i++) {
      for (String doc : WEATHER_DOCS) {
        samples.add(new DocumentSample("weather", doc.split(" ")));
      }
      for (String doc : FINANCE_DOCS) {
        samples.add(new DocumentSample("finance", doc.split(" ")));
      }
    }

    final TrainingParameters params = new TrainingParameters();
    params.put(Parameters.ALGORITHM_PARAM, AlgorithmType.MAXENT.getAlgorithmType());
    params.put(Parameters.ITERATIONS_PARAM, 100);
    params.put(Parameters.CUTOFF_PARAM, 0);

    try (ObjectStream<DocumentSample> stream = ObjectStreamUtils.createObjectStream(samples)) {
      final DoccatModel model =
          DocumentCategorizerME.train("eng", stream, params, new DoccatFactory());
      model.serialize(target);
    }
    return target;
  }
}
