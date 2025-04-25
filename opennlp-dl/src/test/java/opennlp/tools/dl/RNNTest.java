/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.dl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CV tests for {@link RNN}
 */
public class RNNTest {

  private final Random r = new Random(42);
  private String text;
  private List<String> words;

  @BeforeEach
  public void setUp() throws Exception {
    try (InputStream stream = getClass().getResourceAsStream("/text/sentences.txt")) {
      text = IOUtils.toString(stream, StandardCharsets.UTF_8);
      words = Arrays.asList(text.split("\\s"));
    }
  }

  private static Stream<Arguments> provideRNNParams() {
    return Stream.of(
        Arguments.of(1e-3f, 25, 50, 5)
    );
  }
  
  @ParameterizedTest
  @MethodSource("provideRNNParams")
  @DisabledIfSystemProperty(named = "os.arch", matches = "aarch64")
  public void testVanillaCharRNNLearn(float learningRate, int seqLength, int hiddenLayerSize, int epochs) throws Exception {
    RNN rnn = new RNN(learningRate, seqLength, hiddenLayerSize, epochs, text, 10, true);
    evaluate(rnn, true);
    rnn.serialize("target/crnn-weights-");
  }

  private void evaluate(RNN rnn, boolean checkRatio) {
    System.out.println(rnn);
    rnn.learn();
    double c = 0;
    for (int i = 0; i < 2; i++) {
      int seed = r.nextInt(rnn.getVocabSize());
      String sample = rnn.sample(seed);
      if (checkRatio && rnn.useChars) {
        String[] sampleWords = sample.split(" ");
        for (String sw : sampleWords) {
          if (words.contains(sw)) {
            c++;
          }
        }
        if (c > 0) {
          c /= sampleWords.length;
        }
      }
    }
    if (checkRatio && rnn.useChars) {
      System.out.println("average correct word ratio: " + (c / 10d));
    }
  }

}