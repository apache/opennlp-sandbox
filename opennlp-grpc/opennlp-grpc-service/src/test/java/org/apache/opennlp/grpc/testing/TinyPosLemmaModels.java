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
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains tiny classic pipeline models (sentence detector, tokenizer, UD-tagged
 * {@link POSModel}, probe {@link LemmatizerModel}) from in-memory corpora, entirely offline. The probe lemmatizer maps the same token to a different lemma per
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

  /**
   * Trains a sentence model over two-sentence documents and serializes it to {@code target}.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainSentenceModel(Path target) throws IOException {
    final List<SentenceSample> corpus = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      corpus.add(new SentenceSample("The cats sleep. The dogs run.",
          new Span(0, 15), new Span(16, 29)));
      // The abbreviation periods supply the trainer's no-split outcome.
      corpus.add(new SentenceSample("Dr. Cat naps. Dr. Dog runs.",
          new Span(0, 13), new Span(14, 27)));
    }
    try (ObjectStream<SentenceSample> samples = ObjectStreamUtils.createObjectStream(corpus)) {
      final SentenceModel model = SentenceDetectorME.train("eng", samples,
          new SentenceDetectorFactory("eng", true, null, null), trainingParams());
      model.serialize(target);
    }
    return target;
  }

  /**
   * Trains a tokenizer model over the tiny corpus sentences and serializes it to
   * {@code target}.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainTokenizerModel(Path target) throws IOException {
    final List<TokenSample> corpus = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      corpus.add(new TokenSample("The cats sleep.",
          new Span[] {new Span(0, 3), new Span(4, 8), new Span(9, 14), new Span(14, 15)}));
      corpus.add(new TokenSample("The dogs run.",
          new Span[] {new Span(0, 3), new Span(4, 8), new Span(9, 12), new Span(12, 13)}));
    }
    try (ObjectStream<TokenSample> samples = ObjectStreamUtils.createObjectStream(corpus)) {
      final TokenizerModel model =
          TokenizerME.train(samples,
          new TokenizerFactory("eng", null, false, null), trainingParams());
      model.serialize(target);
    }
    return target;
  }

  /**
   * Trains a marker lemmatizer that maps every UD-tagged token to
   * {@code <token>-<marker>} and serializes it to {@code target}, so the predicted lemma
   * alone reveals which pipeline's lemmatizer served a request.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   * @param marker The lemma suffix identifying this model. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainMarkerLemmaModel(Path target, String marker) throws IOException {
    final List<LemmaSample> corpus = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      for (String[] tokens : TAGGED_SENTENCES) {
        final String[] lemmas = new String[tokens.length];
        for (int t = 0; t < tokens.length; t++) {
          // Punctuation keeps its identity lemma so training sees two outcomes.
          lemmas[t] = ".".equals(tokens[t]) ? "." : tokens[t] + "-" + marker;
        }
        corpus.add(new LemmaSample(tokens, UD_TAGS, lemmas));
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
