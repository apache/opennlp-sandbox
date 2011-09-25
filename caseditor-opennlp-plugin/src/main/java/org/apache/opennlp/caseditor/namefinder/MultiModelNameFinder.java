/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.caseditor.namefinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.opennlp.caseditor.ModelUtil;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameFinderSequenceValidator;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;

public class MultiModelNameFinder implements TokenNameFinder {

  static class RestrictedSequencesValidator extends NameFinderSequenceValidator {
    
    private Map<Integer, String> nameIndex = new HashMap<Integer, String>();
    
    private Set<String> nameOnlyTokens;
    
    // also give it a no-name index
    void setRestriction(Map<Integer, String> nameIndex) {
      this.nameIndex = nameIndex;
    }
    
    void setNameOnlyTokens(Set<String> nameOnlyTokens) {
      this.nameOnlyTokens = nameOnlyTokens;
    }
    
    @Override
    public boolean validSequence(int i, String[] inputSequence,
        String[] outcomesSequence, String outcome) {
      boolean valid = super.validSequence(i, inputSequence, outcomesSequence, outcome);
      
      if (valid && nameIndex.get(i) != null) {
        String desiredOutcome = nameIndex.get(i);
        return outcome.endsWith(desiredOutcome);
      }
      
      // if token part of name only token, then 
      // its either start, or cont
      if (valid && nameOnlyTokens.contains(inputSequence[i])) {
          return outcome.endsWith(NameFinderME.START) || 
                  outcome.endsWith(NameFinderME.CONTINUE); 
      }
      
      return valid;
    }
  }
  
  
  private NameFinderME nameFinders[];
  private String typeNames[]; // renmame to modelTypes
  
  private RestrictedSequencesValidator sequenceValidator;
  
  MultiModelNameFinder(String modelPathes[], String typeNames[]) {
    
    this.typeNames = typeNames;
    
    nameFinders = new NameFinderME[modelPathes.length];
        
    for (int i = 0; i < modelPathes.length; i++) {
      
      String modelPath = modelPathes[i];
      
      InputStream modelIn = ModelUtil.openModelIn(modelPath);
      
      try {
        TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
//        sequenceValidator = new RestrictedSequencesValidator();
//        nameFinders[i] = new NameFinderME(model, null, 5, sequenceValidator);
        nameFinders[i] = new NameFinderME(model, null, 5);
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (modelIn != null) {
          try {
            modelIn.close();
          } catch (IOException e) {
          }
        }
      }
    }
    
  }
  
  // Needs to be changed, so different models are supported
  void setRestriction(Map<Integer, String> nameIndex) {
//    sequenceValidator.setRestriction(nameIndex);
  }

  // Needs to be changed, so it can be about different categories ... 
  void setNameOnlyTokens(Set<String> nameOnlyTokens) {
//    sequenceValidator.setNameOnlyTokens(nameOnlyTokens);
  }
  
  @Override
  public void clearAdaptiveData() {
    for (NameFinderME nameFinder : nameFinders) {
      nameFinder.clearAdaptiveData();
    }
  }

  @Override
  public ConfidenceSpan[] find(String[] sentence) {
    
    List<ConfidenceSpan> names = new ArrayList<ConfidenceSpan>();
    
    for (int i = 0; i < nameFinders.length; i++) {
      NameFinderME nameFinder = nameFinders[i];
      Span detectedNames[] = nameFinder.find(sentence);
      double confidence[] = nameFinder.probs();
      
      for (int j = 0; j < detectedNames.length; j++) {
        // TODO: Also add type ...
        names.add(new ConfidenceSpan(detectedNames[j].getStart(), detectedNames[j].getEnd(),
            confidence[j], typeNames[i]));
      }
    }
    
    // TODO: Merge names here ...
    
    return names.toArray(new ConfidenceSpan[names.size()]);
  }
}
