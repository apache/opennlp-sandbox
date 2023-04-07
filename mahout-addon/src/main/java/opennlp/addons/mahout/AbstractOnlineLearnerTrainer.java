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

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.model.DataIndexer;

import opennlp.tools.util.TrainingParameters;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;

abstract class AbstractOnlineLearnerTrainer extends AbstractEventTrainer {

  protected int iterations;
  
  public AbstractOnlineLearnerTrainer() {
  }

  @Override
  public void init(TrainingParameters trainParams, Map<String,String> reportMap) {
	  iterations = trainParams.getIntParameter("Iterations", 20);
  }
  
  protected void trainOnlineLearner(DataIndexer indexer, org.apache.mahout.classifier.OnlineLearner pa) {
    int cardinality = indexer.getPredLabels().length;
    int[] outcomes = indexer.getOutcomeList();
    
    for (int i = 0; i < indexer.getContexts().length; i++) {

      Vector vector = new RandomAccessSparseVector(cardinality);
      
      int[] features = indexer.getContexts()[i];

      for (int feature : features) {
        vector.set(feature, indexer.getNumTimesEventsSeen()[i]);
      } 
      
      pa.train(outcomes[i], vector);
    }
  }

  protected Map<String, Integer> createPrepMap(DataIndexer indexer) {
    Map<String, Integer> predMap = new HashMap<>();
    
    String[] predLabels = indexer.getPredLabels();
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
