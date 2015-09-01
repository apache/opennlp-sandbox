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
package org.apache.opennlp.utils.regression;

import org.apache.opennlp.utils.TrainingExample;
import org.apache.opennlp.utils.TrainingSet;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.regression.RegressionModelUtils}
 */
public class RegressionModelUtilsTest {

  @Test
  public void testLMS() throws Exception {
    TrainingSet trainingSet = new TrainingSet();
    trainingSet.add(new TrainingExample(new double[]{10, 10}, 1));
    LinearCombinationHypothesis hypothesis = new LinearCombinationHypothesis(1, 1);
    double[] updatedParameters = RegressionModelUtils.batchLeastMeanSquareUpdate(new double[]{1, 1}, 0.1, trainingSet, hypothesis);
    assertNotNull(updatedParameters);
    assertTrue(updatedParameters.length == 2);
    assertTrue(updatedParameters[0] == -18d);
    assertTrue(updatedParameters[1] == -18d);
  }
}
