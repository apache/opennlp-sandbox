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
package opennlp.addons.mahout;

import java.util.Map;

import opennlp.tools.ml.model.MaxentModel;

import org.apache.mahout.classifier.AbstractVectorClassifier;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;

// TODO: Would be nice to have an abstract maxent model impl ..

public class VectorClassifierModel implements MaxentModel {

  private final AbstractVectorClassifier classifier;
  private final String[] outcomeLabels;
  private final Map<String, Integer> predMap;
  
  public VectorClassifierModel(AbstractVectorClassifier pa, String outcomeLabels[],
      Map<String, Integer> predMap) {
    this.classifier = pa;
    // TODO: We should make a copy, so the model is immutable ...
    this.outcomeLabels = outcomeLabels;
    this.predMap = predMap;
  }

  public double[] eval(String[] features) {
    Vector vector = new RandomAccessSparseVector(predMap.size());
    
    for (String feature : features) {
      Integer featureId = predMap.get(feature);
      
      if (featureId != null) {
        vector.set(featureId, vector.get(featureId) + 1);
      }
    }
    
    Vector resultVector = classifier.classifyFull(vector);
    
    double outcomes[] = new double[classifier.numCategories()];
    
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = resultVector.get(i);
    }
    
    return outcomes;
  }

  public double[] eval(String[] context, double[] probs) {
    return eval(context);
  }

  public double[] eval(String[] context, float[] values) {
    return eval(context);
  }

  @Override
  public String getBestOutcome(double[] ocs) {
    int best = 0;
    for (int i = 1; i < ocs.length; i++)
        if (ocs[i] > ocs[best]) best = i;
    return outcomeLabels[best];
  }

  @Override
  public String getAllOutcomes(double[] outcomes) {
    return null;
  }

  @Override
  public String getOutcome(int i) {
    return outcomeLabels[i];
  }

  @Override
  public int getIndex(String outcome) {
    for (int i = 0; i < outcomeLabels.length; i++) {
      if (outcomeLabels[i].equals(outcome)) {
        return i;
      }
    }
    
    return -1;
  }

  @Override
  public int getNumOutcomes() {
    return outcomeLabels.length;
  }
}
