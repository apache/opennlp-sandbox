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

/**
 * A utility class for calculating various regression models costs.
 */
public class RegressionModelUtils {

  /**
   * Calculates the ordinary least squares (OLS) cost in the given training set for a given hypothesis.
   *
   * @param trainingSet The {@link TrainingSet} used.
   * @param hypothesis  The {@link Hypothesis} function representing the model.
   * @return The cost of the hypothesis for the given training set using OLS.
   */
  public static double ordinaryLeastSquares(TrainingSet trainingSet, Hypothesis hypothesis) {
    double output = 0;
    for (TrainingExample trainingExample : trainingSet) {
      double difference = hypothesis.calculateOutput(trainingExample.getInputs()) - trainingExample.getOutput();
      output += Math.pow(difference, 2);
    }
    return output / 2d;
  }

  /**
   * Calculates the least mean square (LMS) update for a given weight vector.
   *
   * @param thetas      The array of weights.
   * @param alpha       The learning rate alpha.
   * @param trainingSet The {@link TrainingSet} to use for learning.
   * @param hypothesis  The {@link Hypothesis} representing the model.
   * @return the updated weights vector
   */
  public static double[] batchLeastMeanSquareUpdate(double[] thetas, double alpha, TrainingSet trainingSet, Hypothesis hypothesis) {
    double[] updatedWeights = new double[thetas.length];
    for (int i = 0; i < updatedWeights.length; i++) {
      double errors = 0;
      for (TrainingExample trainingExample : trainingSet) {
        errors += (trainingExample.getOutput() - hypothesis.calculateOutput(trainingExample.getInputs())) * trainingExample.getInputs()[i];
      }
      updatedWeights[i] = thetas[i] + alpha * errors;
    }
    return updatedWeights;
  }

  /**
   * Calculates the Least Mean Square update for a given training example for the j-th input.
   *
   * @param thetas          The array of weights.
   * @param alpha           The learning rate alpha.
   * @param trainingExample The {@link TrainingExample} to use for learning.
   * @param hypothesis      The {@link Hypothesis} representing the model.
   * @param j               The index of the j-th input.
   * @return The updated weight for the j-th element of the weights vector.
   */
  public static double singleLeastMeanSquareUpdate(double[] thetas, double alpha, TrainingExample trainingExample, Hypothesis hypothesis, int j) {
    return thetas[j] + alpha * (trainingExample.getOutput() - hypothesis.calculateOutput(trainingExample.getInputs())) * trainingExample.getInputs()[j];
  }

}
