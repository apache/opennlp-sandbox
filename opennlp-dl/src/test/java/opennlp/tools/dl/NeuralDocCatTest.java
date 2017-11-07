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

import java.util.Arrays;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link NeuralDocCat}
 */
@Ignore
public class NeuralDocCatTest {

  @Test
  public void testDocCatTrainingOnTweets() throws Exception {
    NeuralDocCatTrainer.Args args = new NeuralDocCatTrainer.Args();
    args.glovesPath = "/path/to/glove.6B/glove.6B.50d.txt";
    args.labels = Arrays.asList("0", "1");
    String modelPathPrefix = "target/ndcmodel";
    args.modelPath = modelPathPrefix + ".out";
    args.trainDir = getClass().getResource("/ltweets").getFile();
    NeuralDocCatTrainer trainer = new NeuralDocCatTrainer(args);
    trainer.train();
    String modelPath = modelPathPrefix + ".zip";
    trainer.saveModel(modelPath);

    /* TODO : this fails with:
     * java.lang.AssertionError
     * at opennlp.tools.dl.GlobalVectors.<init>(GlobalVectors.java:92)
     */
    NeuralDocCatModel neuralDocCatModel = NeuralDocCatModel.loadModel(modelPath);
    assertNotNull(neuralDocCatModel);

    NeuralDocCat neuralDocCat = new NeuralDocCat(NeuralDocCatModel.loadModel(modelPathPrefix));
    Map<String, Double> scoreMap = neuralDocCat.scoreMap(new String[] {"u r so dumb"});
    assertNotNull(scoreMap);
    Double negativeScore = scoreMap.get("0");
    assertNotNull(negativeScore);
    Double positiveScore = scoreMap.get("1");
    assertNotNull(positiveScore);
    assertTrue(negativeScore > positiveScore);
  }

}

