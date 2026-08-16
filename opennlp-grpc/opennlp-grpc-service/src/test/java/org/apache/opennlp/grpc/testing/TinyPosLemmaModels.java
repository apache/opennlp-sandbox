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

import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.lemmatizer.LemmatizerFactory;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.ml.AlgorithmType;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains a tiny UD-tagged {@link POSModel} and a probe {@link LemmatizerModel} from in-memory
 * corpora, entirely offline. The probe lemmatizer maps the same token to a different lemma per
 * tagset ("cats"/NOUN to "cat", "cats"/NN to "cats-penn"), so a test can tell from the output
 * lemma alone which tagset the lemmatizer was fed. Fixtures for wiring assertions, not models
 * of any real-world quality.
 */
public final class TinyPosLemmaModels {

  private TinyPosLemmaModels() {
  }

  private static final String[][] TAGGED_SENTENCES = {
      {"the", "cats", "sleep", "."},
      {"the", "dogs", "sleep", "."},
      {"the", "cats", "run", "."},
      {"the", "dogs", "run", "."},
  };

  private static final String[] UD_TAGS = {"DET", "NOUN", "VERB", "PUNCT"};

  /**
   * Trains a POS model whose native tagset is UD and serializes it to {@code target}.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainPosModel(Path target) throws IOException {
    final List<POSSample> corpus = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      for (String[] tokens : TAGGED_SENTENCES) {
        corpus.add(new POSSample(tokens, UD_TAGS));
      }
    }
    try (ObjectStream<POSSample> samples = ObjectStreamUtils.createObjectStream(corpus)) {
      final POSModel model =
          POSTaggerME.train("eng", samples, trainingParams(), new POSTaggerFactory());
      model.serialize(target);
    }
    return target;
  }

  /**
   * Trains the probe lemmatizer and serializes it to {@code target}. For every sentence the
   * corpus holds one sample with the native UD tags (yielding true lemmas) and one with the
   * PENN conversion of those tags (yielding {@code -penn} marker lemmas), so the predicted
   * lemma reveals the tagset the decoder received.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainLemmaModel(Path target) throws IOException {
    final String[] pennTags = {"DT", "NN", "VB", "."};
    final List<LemmaSample> corpus = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      for (String[] tokens : TAGGED_SENTENCES) {
        final String[] udLemmas = new String[tokens.length];
        final String[] pennLemmas = new String[tokens.length];
        for (int t = 0; t < tokens.length; t++) {
          final String lemma = tokens[t].endsWith("s") && !tokens[t].equals(".")
              ? tokens[t].substring(0, tokens[t].length() - 1) : tokens[t];
          udLemmas[t] = lemma;
          pennLemmas[t] = lemma + "-penn";
        }
        corpus.add(new LemmaSample(tokens, UD_TAGS, udLemmas));
        corpus.add(new LemmaSample(tokens, pennTags, pennLemmas));
      }
    }
    try (ObjectStream<LemmaSample> samples = ObjectStreamUtils.createObjectStream(corpus)) {
      final LemmatizerModel model =
          LemmatizerME.train("eng", samples, trainingParams(), new LemmatizerFactory());
      model.serialize(target);
    }
    return target;
  }

  private static TrainingParameters trainingParams() {
    final TrainingParameters params = new TrainingParameters();
    // MAXENT (GIS) is the trainer bundled with the inference runtime; the train()
    // default (PERCEPTRON) lives in a module the server does not depend on.
    params.put(Parameters.ALGORITHM_PARAM, AlgorithmType.MAXENT.getAlgorithmType());
    params.put(Parameters.ITERATIONS_PARAM, 300);
    params.put(Parameters.CUTOFF_PARAM, 0);
    return params;
  }
}
