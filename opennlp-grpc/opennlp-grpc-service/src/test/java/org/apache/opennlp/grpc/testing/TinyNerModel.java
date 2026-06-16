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
import java.util.Collections;
import java.util.List;

import opennlp.tools.ml.AlgorithmType;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains a tiny English {@code person} {@link TokenNameFinderModel} from an in-memory
 * annotated corpus, entirely offline. Tests use this instead of downloading legacy
 * SourceForge models, so the suite is hermetic (no network, no silent skip) and carries
 * no third-party model provenance into the build.
 *
 * <p>The corpus deliberately contains the exact names used by the NER tests so the
 * trained model reliably recognizes them; it is a fixture for wiring/integration
 * assertions, not a model of any real-world quality.</p>
 */
public final class TinyNerModel {

  private TinyNerModel() {
  }

  /** Annotated training sentences in the OpenNLP {@code <START:type> ... <END>} format. */
  private static final String[] TRAINING_SENTENCES = {
      "<START:person> Pierre Vinken <END> , 61 years old , will join the board "
          + "as a nonexecutive director Nov. 29 .",
      "Mr . <START:person> Vinken <END> is chairman of Elsevier N.V. , "
          + "the Dutch publishing group .",
      "<START:person> John Smith <END> went to Washington yesterday .",
      "The report was written by <START:person> Mary Jones <END> last week .",
      "<START:person> Barack Obama <END> met <START:person> Angela Merkel <END> in Berlin .",
      "Yesterday <START:person> Pierre Vinken <END> spoke briefly to the board .",
  };

  /**
   * Trains the person model and serializes it to {@code target}.
   *
   * @param target Destination {@code .bin} path. Must not be {@code null}.
   *
   * @return {@code target}, for call-site convenience.
   *
   * @throws IOException If training or serialization fails.
   */
  public static Path trainPersonModel(Path target) throws IOException {
    final List<String> lines = new ArrayList<>();
    // Repeat the small corpus so the perceptron trainer converges on the fixture names.
    for (int i = 0; i < 80; i++) {
      Collections.addAll(lines, TRAINING_SENTENCES);
    }

    final TrainingParameters params = new TrainingParameters();
    // MAXENT (GIS) is the trainer bundled with the inference runtime; the train()
    // default (PERCEPTRON) lives in a module the server does not depend on.
    params.put(Parameters.ALGORITHM_PARAM, AlgorithmType.MAXENT.getAlgorithmType());
    params.put(Parameters.ITERATIONS_PARAM, 300);
    params.put(Parameters.CUTOFF_PARAM, 0);

    try (ObjectStream<String> lineStream = ObjectStreamUtils.createObjectStream(lines);
        ObjectStream<NameSample> samples = new NameSampleDataStream(lineStream)) {
      final TokenNameFinderModel model =
          NameFinderME.train("eng", null, samples, params, new TokenNameFinderFactory());
      model.serialize(target);
    }
    return target;
  }
}
