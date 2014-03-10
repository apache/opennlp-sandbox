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

import java.util.Arrays;

import org.apache.opennlp.utils.TrainingSet;

/**
 * Utility class for calculating gradient descent
 */
public class GradientDescentUtils {

  private static final double THRESHOLD = 0.5;
  private static final int MAX_ITERATIONS = 100000;

  /**
   * Calculates batch gradient descent on the give hypothesis, training set and learning rate alpha.
   * The algorithms iteratively adjusts the hypothesis parameters
   *
   * @param hypothesis  the hypothesis representing the model used
   * @param trainingSet the training set used to fit the parameters
   * @param alpha       the learning rate alpha used to define how big the descent steps are
   */
  public static void batchGradientDescent(Hypothesis hypothesis, TrainingSet trainingSet, double alpha) {
    // set initial random weights
    double[] parameters = initializeRandomWeights(trainingSet.iterator().next().getInputs().length);
    hypothesis.updateParameters(parameters);

    int iterations = 0;

    double cost = Double.MAX_VALUE;
    while (true) {
      // calculate cost
      double newCost = RegressionModelUtils.ordinaryLeastSquares(trainingSet, hypothesis);

      if (newCost > cost) {
        throw new RuntimeException("failed to converge at iteration " + iterations + " with cost going from " + cost + " to " + newCost);
      } else if (cost == newCost || newCost < THRESHOLD || iterations > MAX_ITERATIONS) {
        System.out.println(cost + " with parameters " + Arrays.toString(parameters));
        break;
      }

      // update registered cost
      cost = newCost;

      // calculate the updated parameters
      parameters = RegressionModelUtils.batchLeastMeanSquareUpdate(parameters, alpha, trainingSet, hypothesis);

      // update weights in the hypothesis
      hypothesis.updateParameters(parameters);

      iterations++;
    }
  }

  private static double[] initializeRandomWeights(int size) {
    double[] doubles = new double[size];
    for (int i = 0; i < doubles.length; i++) {
      doubles[i] = Math.random() * 0.1d;
    }
    return doubles;
  }

}
