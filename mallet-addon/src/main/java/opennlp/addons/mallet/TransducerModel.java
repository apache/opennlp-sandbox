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
import java.util.Arrays;
import java.util.List;

import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.model.SerializableArtifact;
import cc.mallet.fst.MaxLatticeDefault;
import cc.mallet.fst.Transducer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Sequence;

public class TransducerModel<T> implements SequenceClassificationModel<T>, SerializableArtifact {

  private Transducer model;

  public TransducerModel(Transducer model) {
    this.model = model;
  }
  
  Transducer getModel() {
    return model;
  }
  
  public opennlp.tools.util.Sequence bestSequence(T[] sequence,
      Object[] additionalContext, BeamSearchContextGenerator<T> cg,
      SequenceValidator<T> validator) {
    return bestSequences(1, sequence, additionalContext, cg, validator)[0];
  }

  @Override
  public opennlp.tools.util.Sequence[] bestSequences(int numSequences,
      T[] sequence, Object[] additionalContext, double minSequenceScore,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {
    // TODO: How to implement min score filtering here? 
    return bestSequences(numSequences, sequence, additionalContext, cg, validator);
  }
  
  public opennlp.tools.util.Sequence[] bestSequences(int numSequences,
      T[] sequence, Object[] additionalContext,
      BeamSearchContextGenerator<T> cg, SequenceValidator<T> validator) {

    // TODO: CRF.getInputAlphabet
    Alphabet dataAlphabet = model.getInputPipe().getAlphabet();
    
    FeatureVector featureVectors[] = new FeatureVector[sequence.length];
    
    // TODO:: The feature generator needs to get the detected sequence in the end
    // to update the adaptive data!
    String prior[] = new String[sequence.length];
    Arrays.fill(prior, "s"); // <- HACK, this will degrade performance!
    
    // TODO: Put together a feature generator which doesn't fail if outcomes is null!
    for (int i = 0; i < sequence.length; i++) {
      String features[] = cg.getContext(i, sequence, null, additionalContext);
      
      List<Integer> malletFeatureList = new ArrayList<>(features.length);
      
      for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
        if (dataAlphabet.contains(features[featureIndex])) {
          malletFeatureList.add(dataAlphabet.lookupIndex(features[featureIndex]));
        }
      }

      int malletFeatures[] = new int[malletFeatureList.size()];
      for (int k = 0; k < malletFeatureList.size(); k++) {
        malletFeatures[k] = malletFeatureList.get(k);
      }
      
      // Note: Might contain a feature more than once ... will that work ?!
      featureVectors[i] = new FeatureVector(dataAlphabet, malletFeatures);
    }
    
    FeatureVectorSequence malletSequence = new FeatureVectorSequence(featureVectors);
    
    Sequence[] answers = null;
    if (numSequences == 1) {
      answers = new Sequence[1];
      answers[0] = model.transduce(malletSequence);
    } else {
      MaxLatticeDefault lattice = new MaxLatticeDefault(model, malletSequence, null, 3);

      answers = lattice.bestOutputSequences(numSequences).toArray(new Sequence[0]);
    }

    opennlp.tools.util.Sequence[] outcomeSequences = new opennlp.tools.util.Sequence[answers.length];
    
    for (int i = 0; i < answers.length; i++) {
      Sequence seq = answers[i];
      
      List<String> outcomes = new ArrayList<>(seq.size());
      
      for (int j = 0; j < seq.size(); j++) {
        outcomes.add(seq.get(j).toString());
      }
      
      outcomeSequences[i] = new opennlp.tools.util.Sequence(outcomes);
    }
    
    return outcomeSequences;
  }

  @Override
  public Class<?> getArtifactSerializerClass() {
    return TransducerModelSerializer.class;
  }



  @Override
  public String[] getOutcomes() {
    
    Alphabet targetAlphabet = model.getInputPipe().getTargetAlphabet();
    
    String outcomes[] = new String[targetAlphabet.size()];
    
    for (int i = 0; i < targetAlphabet.size(); i++) {
      outcomes[i] = targetAlphabet.lookupObject(i).toString();
    }
    
    return outcomes;
  }
}
