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

import org.junit.Test;

import org.apache.opennlp.utils.TestUtils;
import org.apache.opennlp.utils.TrainingExample;
import org.apache.opennlp.utils.TrainingSet;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.anomalydetection.AnomalyDetectionUtils}
 */
public class AnomalyDetectionUtilsTest {
  @Test
  public void testGaussianDistributionProbability() throws Exception {
    TrainingSet trainingSet = new TrainingSet();
    TestUtils.fillTrainingSet(trainingSet, 100, 5);
    double[] mus = AnomalyDetectionUtils.fitMus(trainingSet);
    assertNotNull(mus);
    double[] sigmas = AnomalyDetectionUtils.fitSigmas(mus, trainingSet);
    assertNotNull(sigmas);
    TrainingExample newInput = new TrainingExample(new double[]{1d, 2d, 1000d, 123d, 0.1d}, 0d);
    double probability = AnomalyDetectionUtils.getGaussianProbability(newInput, mus, sigmas);
    assertTrue("negative probability " + probability, 0 <= probability);
    assertTrue("probability bigger than 1 " + probability, 1 >= probability);
  }

  @Test
  public void testGaussianDistributionProbability2() throws Exception {
    TrainingSet trainingSet = new TrainingSet();
    TestUtils.fillTrainingSet(trainingSet, 100, 5);
    TrainingExample newInput = new TrainingExample(new double[]{1d, 2d, 1000d, 123d, 0.1d}, 0d);
    double probability = AnomalyDetectionUtils.getGaussianProbability(newInput, trainingSet);
    assertTrue("negative probability " + probability, 0 <= probability);
    assertTrue("probability bigger than 1 " + probability, 1 >= probability);
  }
}
