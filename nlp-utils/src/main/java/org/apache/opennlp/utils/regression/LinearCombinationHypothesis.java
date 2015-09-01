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

/**
 * Simplest {@link Hypothesis} which just linearly combines inputs with weights
 */
public class LinearCombinationHypothesis implements Hypothesis {
  private final double[] weights;

  public LinearCombinationHypothesis(double... weights) {
    this.weights = weights;
  }

  @Override
  public double calculateOutput(double[] inputs) {
    double output = 0d;
    for (int i = 0; i < weights.length; i++) {
      output += weights[i] * inputs[i];
    }
    return output;
  }

 }
