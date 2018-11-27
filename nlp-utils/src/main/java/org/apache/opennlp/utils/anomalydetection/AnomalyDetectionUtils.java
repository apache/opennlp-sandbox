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

import java.math.BigDecimal;
import org.apache.opennlp.utils.TrainingExample;
import org.apache.opennlp.utils.TrainingSet;

/**
 * Utility class for anomaly detection
 */
public class AnomalyDetectionUtils {

  /**
   * calculate Mu distribution parameters for a {@link org.apache.opennlp.utils.TrainingSet}'s set of features
   *
   * @param inputs the {@link org.apache.opennlp.utils.TrainingSet} to fit
   * @return the <code>double[]</code> containing the Mu parameters for each feature
   */
  public static double[] fitMus(TrainingSet inputs) {
    assert inputs != null && inputs.size() > 0 : "empty dataset";
    int size = inputs.iterator().next().getInputs().length;
    double[] result = new double[size];
    for (int i = 0; i < size; i++) {
      for (TrainingExample trainingExample : inputs) {
        result[i] += trainingExample.getInputs()[i];
      }
      result[i] /= inputs.size();
    }
    return result;
  }

  /**
   * calculates (squared) standard deviation parameters for the given {@link TrainingSet}
   *
   * @param mus    mean parameters
   * @param inputs the {@link TrainingSet} to fit
   * @return the <code>double[]</code> containing the standard deviations
   */
  public static double[] fitSigmas(double[] mus, TrainingSet inputs) {
    assert inputs != null && inputs.size() > 0 : "empty dataset";
    int size = inputs.iterator().next().getInputs().length;
    double[] result = new double[size];
    for (int i = 0; i < size; i++) {
      for (TrainingExample trainingExample : inputs) {
        result[i] += Math.pow(trainingExample.getInputs()[i] - mus[i], 2);
      }
      result[i] /= inputs.size();
    }
    return result;
  }

  /**
   * calculate the probability of a certain input
   *
   * @param x      the input
   * @param mus    the means for the modeled features
   * @param sigmas the standard deviations for the modeled features
   * @return the probability of the given input
   */
  public static double getGaussianProbability(TrainingExample x, double[] mus, double[] sigmas) {
    return calculateGaussianProbability(x, mus, sigmas);
  }

  /**
   * calculate the probability of a certain input in a certain training set
   *
   * @param x   the input
   * @param set the training set
   * @return the probability of the given input
   * @throws Exception
   */
  public static double getGaussianProbability(TrainingExample x, TrainingSet set) throws Exception {
    double[] mus = fitMus(set);
    double[] sigmas = fitSigmas(mus, set);
    return calculateGaussianProbability(x, mus, sigmas);
  }

  private static double calculateGaussianProbability(TrainingExample x, double[] mus,
                                                     double[] sigmas) {
    assert mus.length == sigmas.length : "parameters not aligned";
    BigDecimal px = new BigDecimal(1d);
    for (int i = 0; i < mus.length; i++) {
      BigDecimal firstTerm = BigDecimal.ONE.divide(BigDecimal.valueOf(Math.sqrt(2d * Math.PI * sigmas[i])), BigDecimal.ROUND_CEILING);
      BigDecimal secondTerm = BigDecimal.valueOf(Math.exp(-1 * (Math.pow(x.getInputs()[i] - mus[i], 2) / (2 * Math.pow(sigmas[i], 2)))));
      px = px.multiply(firstTerm.multiply(secondTerm));
    }
    return px.doubleValue();
  }

}
