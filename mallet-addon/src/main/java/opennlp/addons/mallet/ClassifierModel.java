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

package opennlp.addons.mallet;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.model.SerializableArtifact;
import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelVector;

class ClassifierModel implements MaxentModel, SerializableArtifact {

  private Classifier classifer;

  public ClassifierModel(Classifier classifer) {
    this.classifer = classifer;
  }

  Classifier getClassifer() {
    return classifer;
  }
  
  public double[] eval(String[] features) {
    Alphabet dataAlphabet = classifer.getAlphabet();

    List<Integer> malletFeatureList = new ArrayList<>(features.length);

    for (String feature : features) {
      int featureId = dataAlphabet.lookupIndex(feature);
      if (featureId != -1) {
        malletFeatureList.add(featureId);
      }
    }

    int malletFeatures[] = new int[malletFeatureList.size()];
    for (int i = 0; i < malletFeatureList.size(); i++) {
      malletFeatures[i] = malletFeatureList.get(i);
    }

    FeatureVector fv = new FeatureVector(classifer.getAlphabet(),
        malletFeatures);
    Instance instance = new Instance(fv, null, null, null);

    Classification result = classifer.classify(instance);

    LabelVector labeling = result.getLabelVector();

    LabelAlphabet targetAlphabet = classifer.getLabelAlphabet();

    double outcomes[] = new double[targetAlphabet.size()];
    for (int i = 0; i < outcomes.length; i++) {

      Label label = targetAlphabet.lookupLabel(i);

      int rank = labeling.getRank(label);
      outcomes[i] = labeling.getValueAtRank(rank);
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
      if (ocs[i] > ocs[best])
        best = i;
    
    return getOutcome(best);
  }

  @Override
  public String getAllOutcomes(double[] outcomes) {
    return null;
  }

  @Override
  public String getOutcome(int i) {
    return classifer.getLabelAlphabet().lookupLabel(i).getEntry().toString();
  }

  @Override
  public int getIndex(String outcome) {
    return classifer.getLabelAlphabet().lookupIndex(outcome);
  }

  @Override
  public int getNumOutcomes() {
    return classifer.getLabelAlphabet().size();
  }

  @Override
  public Class<?> getArtifactSerializerClass() {
    return ClassifierModelSerializer.class;
  }
}
