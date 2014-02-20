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

import java.io.IOException;
import java.util.Map;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.MaxentModel;

import org.apache.mahout.classifier.sgd.AdaptiveLogisticRegression;
import org.apache.mahout.classifier.sgd.L1;

public class AdaptiveLogisticRegressionTrainer extends AbstractOnlineLearnerTrainer {
  
  public AdaptiveLogisticRegressionTrainer(Map<String, String> trainParams,
      Map<String, String> reportMap) {
  }

  @Override
  public MaxentModel doTrain(DataIndexer indexer) throws IOException {
    
    // TODO: Lets use the predMap here as well for encoding
    int numberOfOutcomes = indexer.getOutcomeLabels().length;
    int numberOfFeatures = indexer.getPredLabels().length;
    
    AdaptiveLogisticRegression pa = new AdaptiveLogisticRegression(numberOfOutcomes,
        numberOfFeatures, new L1());
    
    // TODO: Make these parameters configurable ...
    //  what are good values ?! 
    pa.setInterval(800);
    pa.setAveragingWindow(500);
    
    for (int k = 0; k < iterations; k++) {
      trainOnlineLearner(indexer, pa);
      
      // What should be reported at the end of every iteration ?!
      System.out.println("Iteration " + (k + 1));
    }
    
    pa.close();
    
    return new VectorClassifierModel(pa.getBest().getPayload().getLearner(),
        indexer.getOutcomeLabels(), createPrepMap(indexer));
  }

  @Override
  public boolean isSortAndMerge() {
    return true;
  }
}
