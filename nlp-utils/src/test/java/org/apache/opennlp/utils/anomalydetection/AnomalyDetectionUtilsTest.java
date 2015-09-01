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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.utils.anomalydetection;

import org.apache.opennlp.utils.TestUtils;
import org.apache.opennlp.utils.TrainingExample;
import org.apache.opennlp.utils.TrainingSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.anomalydetection.AnomalyDetectionUtils}
 */
public class AnomalyDetectionUtilsTest {

  @Test
  public void testGaussianDistributionProbabilityFromFitParameters() throws Exception {
    TrainingSet trainingSet = new TrainingSet();
    TestUtils.fillTrainingSet(trainingSet, 100, 5);
    double[] mus = AnomalyDetectionUtils.fitMus(trainingSet);
    assertNotNull(mus);
    double[] sigmas = AnomalyDetectionUtils.fitSigmas(mus, trainingSet);
    assertNotNull(sigmas);
    TrainingExample newInput = new TrainingExample(new double[]{0.4d,0.5d,0.5d,0.5d,0.2d}, 0d);
    double probability = AnomalyDetectionUtils.getGaussianProbability(newInput, mus, sigmas);
    assertEquals(0.5d, probability, 0.5d);
  }

  @Test
  public void testGaussianDistributionProbabilityFromTrainingSet() throws Exception {
    TrainingSet trainingSet = new TrainingSet();
    TestUtils.fillTrainingSet(trainingSet, 100, 5);
    TrainingExample newInput = new TrainingExample(new double[]{0.4d,0.5d,0.5d,0.5d,0.2d}, 0d);
    double probability = AnomalyDetectionUtils.getGaussianProbability(newInput, trainingSet);
    assertEquals(0.5d, probability, 0.5d);
  }
}
