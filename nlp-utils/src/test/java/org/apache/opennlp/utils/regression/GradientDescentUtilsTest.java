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

import org.apache.opennlp.utils.TestUtils;
import org.apache.opennlp.utils.TrainingSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testcase for {@link org.apache.opennlp.utils.regression.GradientDescentUtils}
 */
class GradientDescentUtilsTest {

  private TrainingSet trainingSet;
  
  @BeforeEach
  public void setup() {
    trainingSet = new TrainingSet();
    TestUtils.fillTrainingSet(trainingSet, 100, 5);
  }

  @Test // @RepeatedTest(1000)
  void testConvergence() {
    GradientDescentUtils.batchGradientDescent(trainingSet, 0.00001);
  }

}
