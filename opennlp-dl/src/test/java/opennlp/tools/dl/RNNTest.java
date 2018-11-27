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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * CV tests for {@link RNN}
 */
@RunWith(Parameterized.class)
public class RNNTest {

  private float learningRate;
  private int seqLength;
  private int hiddenLayerSize;
  private int epochs;

  private Random r = new Random();
  private String text;
  private List<String> words;

  public RNNTest(float learningRate, int seqLength, int hiddenLayerSize, int epochs) {
    this.learningRate = learningRate;
    this.seqLength = seqLength;
    this.hiddenLayerSize = hiddenLayerSize;
    this.epochs = epochs;
  }

  @Before
  public void setUp() throws Exception {
    InputStream stream = getClass().getResourceAsStream("/text/sentences.txt");
    text = IOUtils.toString(stream);
    words = Arrays.asList(text.split("\\s"));
    stream.close();
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {1e-3f, 25, 50, 5},
    });
  }

  @Test
  public void testVanillaCharRNNLearn() throws Exception {
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