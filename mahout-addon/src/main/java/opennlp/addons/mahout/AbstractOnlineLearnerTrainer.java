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
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;

abstract class AbstractOnlineLearnerTrainer extends AbstractEventTrainer {

  protected int iterations;
  
  public AbstractOnlineLearnerTrainer() {
  }

  public void init(Map<String, String> trainParams,
	      Map<String, String> reportMap) {
	  String iterationsValue = trainParams.get("Iterations");
	  
	  if (iterationsValue != null) {
		  iterations = Integer.parseInt(iterationsValue);
	  }
	  else {
		  iterations = 20;
	  }
  }
  
  protected void trainOnlineLearner(DataIndexer indexer, org.apache.mahout.classifier.OnlineLearner pa) {
    int cardinality = indexer.getPredLabels().length;
    int outcomes[] = indexer.getOutcomeList();
    
    for (int i = 0; i < indexer.getContexts().length; i++) {

      Vector vector = new RandomAccessSparseVector(cardinality);
      
      int features[] = indexer.getContexts()[i];
      
      for (int fi = 0; fi < features.length; fi++) {
        vector.set(features[fi], indexer.getNumTimesEventsSeen()[i]);
      } 
      
      pa.train(outcomes[i], vector);
    }
  }

  protected Map<String, Integer> createPrepMap(DataIndexer indexer) {
    Map<String, Integer> predMap = new HashMap<String, Integer>();
    
    String predLabels[] = indexer.getPredLabels();
    for (int i = 0; i < predLabels.length; i++) {
      predMap.put(predLabels[i], i);
    }
    
    return predMap;
  }
  
  @Override
  public boolean isSortAndMerge() {
    return true;
  }
}
