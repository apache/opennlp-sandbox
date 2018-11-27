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
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.MaxentModel;

import org.apache.mahout.classifier.sgd.AdaptiveLogisticRegression;
import org.apache.mahout.classifier.sgd.L1;
import org.apache.mahout.classifier.sgd.OnlineLogisticRegression;
import org.apache.mahout.classifier.sgd.PassiveAggressive;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;

public class LogisticRegressionTrainer extends AbstractOnlineLearnerTrainer {
  
  public LogisticRegressionTrainer(Map<String, String> trainParams,
      Map<String, String> reportMap) {
  }

  @Override
  public MaxentModel doTrain(DataIndexer indexer) throws IOException {
    
    // TODO: Lets use the predMap here as well for encoding
    
    int outcomes[] = indexer.getOutcomeList();
    
    int cardinality = indexer.getPredLabels().length;
    
    
    AdaptiveLogisticRegression pa = new AdaptiveLogisticRegression(indexer.getOutcomeLabels().length,
        cardinality, new L1());
    
    pa.setInterval(800);
    pa.setAveragingWindow(500);
    
//    PassiveAggressive pa = new PassiveAggressive(indexer.getOutcomeLabels().length, cardinality);
//    pa.learningRate(10000);
    
//    OnlineLogisticRegression pa = new OnlineLogisticRegression(indexer.getOutcomeLabels().length, cardinality,
//        new L1());
//    
//    pa.alpha(1).stepOffset(250)
//    .decayExponent(0.9)
//    .lambda(3.0e-5)
//    .learningRate(3000);
    
    // TODO: Should we do both ?! AdaptiveLogisticRegression ?! 
    
    for (int k = 0; k < iterations; k++) {
      trainOnlineLearner(indexer, pa);
      
      // What should be reported at the end of every iteration ?!
      System.out.println("Iteration " + (k + 1));
    }
    
    pa.close();
    
    Map<String, Integer> predMap = new HashMap<String, Integer>();
    
    String predLabels[] = indexer.getPredLabels();
    for (int i = 0; i < predLabels.length; i++) {
      predMap.put(predLabels[i], i);
    }
    
    return new VectorClassifierModel(pa.getBest().getPayload().getLearner(), indexer.getOutcomeLabels(), predMap);
    
//    return new VectorClassifierModel(pa, indexer.getOutcomeLabels(), predMap);
  }

  @Override
  public boolean isSortAndMerge() {
    return true;
  }
}
