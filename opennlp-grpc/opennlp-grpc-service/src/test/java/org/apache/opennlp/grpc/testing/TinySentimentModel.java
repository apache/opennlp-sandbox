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
 * Trains a tiny two-category ({@code positive} / {@code negative}) {@link DoccatModel} from an
 * in-memory corpus, entirely offline. Because sentiment is document categorization applied per
 * sentence, the server's sentiment path reuses the doccat backends, so this fixture is the same
 * shape as {@link TinyDoccatModel} but with polarity labels. Tests use it instead of downloading a
 * real sentiment model, keeping the suite hermetic and free of third-party model provenance.
 *
 * <p>The vocabulary of each class is deliberately disjoint so the trained model confidently
 * separates the fixture sentences; it is a wiring/integration fixture, not a model of any
 * real-world quality.</p>
 */
public final class TinySentimentModel {

  private TinySentimentModel() {
  }

  /** Strongly positive-flavored training documents. */
  private static final String[] POSITIVE_DOCS = {
      "great wonderful excellent amazing fantastic delightful brilliant superb lovely perfect",
      "i absolutely love this it is wonderful and made me so happy and grateful",
      "an excellent and delightful experience that exceeded every hope we truly enjoyed it",
      "fantastic service brilliant quality and a superb friendly team highly recommended",
  };

  /** Strongly negative-flavored training documents. */
  private static final String[] NEGATIVE_DOCS = {
      "terrible awful horrible disappointing bad dreadful miserable worst broken useless",
      "i absolutely hate this it is awful and made me angry frustrated and upset",
      "a terrible and disappointing experience that ruined the day we regret it completely",
      "horrible service dreadful quality and a rude unhelpful team strongly discouraged",
  };

  /**
   * Trains the sentiment categorizer and serializes it to {@code target}.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainPolarityModel(Path target) throws IOException {
    final List<DocumentSample> samples = new ArrayList<>();
    // Repeat the small corpus so the maxent trainer converges on the fixture vocabulary.
    for (int i = 0; i < 60; i++) {
      for (String doc : POSITIVE_DOCS) {
        samples.add(new DocumentSample("positive", doc.split(" ")));
      }
      for (String doc : NEGATIVE_DOCS) {
        samples.add(new DocumentSample("negative", doc.split(" ")));
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
