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

package opennlp.tools.disambiguator;


import java.util.List;

import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.disambiguator.lesk.LeskParameters;
import opennlp.tools.disambiguator.lesk.LeskParameters.LESK_TYPE;
import opennlp.tools.disambiguator.mfs.MFS;

import org.junit.Test;

public class LeskTester {

  @Test
  public static void main(String[] args) {

    Lesk lesk = new Lesk();
    LeskParameters params = new LeskParameters();
    params.setLeskType(LESK_TYPE.LESK_EXT_EXP_CTXT_WIN);
    lesk.setParams(params);

    String test1 = "I went fishing for some sea bass.";
    String[] sentence = Loader.getTokenizer().tokenize(test1);
    List<WordPOS> words = PreProcessor.getAllRelevantWords(sentence);
    int targetWordIndex = 2;
    String[] tags = new String[words.size()];
    String[] tokens = new String[words.size()];
    for (int i=0;i<words.size();i++){
      tags[i] = words.get(i).getPosTag();
      tokens[i] = words.get(i).getWord();
      
     // Constants.print("token : "+ tokens[i]  + "_" + tags[i]);
    }
    String targetLemma = Loader.getLemmatizer().lemmatize(
        tokens[targetWordIndex], tags[targetWordIndex]);
   // Constants.print("lemma  : "+ targetLemma);
    Constants.print(lesk.disambiguate(tokens, tags, targetWordIndex,targetLemma));
    Constants.printResults(lesk,
        lesk.disambiguate(tokens, tags, targetWordIndex, targetLemma));
    
    Constants.print("----------------------------------------");
    
    String test2 = "it was a strong argument that his hypothesis was true";
    sentence = Loader.getTokenizer().tokenize(test2);
    words = PreProcessor.getAllRelevantWords(sentence);
    targetWordIndex = 1;
    tags = new String[words.size()];
    tokens = new String[words.size()];
    for (int i=0;i<words.size();i++){
      tags[i] = words.get(i).getPosTag();
      tokens[i] = words.get(i).getWord();
      
      //Constants.print("token : "+ tokens[i]  + "_" + tags[i]);
    }
    targetLemma = Loader.getLemmatizer().lemmatize(
        tokens[targetWordIndex], tags[targetWordIndex]);
    //Constants.print("lemma  : "+ targetLemma);
    
    Constants.print(lesk.disambiguate(tokens, tags, targetWordIndex,targetLemma));
    Constants.printResults(lesk,
        lesk.disambiguate(tokens, tags, targetWordIndex, targetLemma));
    Constants.print("----------------------------------------");
    
    String test3 = "the component was highly radioactive to the point that it has been activated the second it touched water";
    
    sentence = Loader.getTokenizer().tokenize(test3);
    words = PreProcessor.getAllRelevantWords(sentence);
    targetWordIndex = 4;
    tags = new String[words.size()];
    tokens = new String[words.size()];
    for (int i=0;i<words.size();i++){
      tags[i] = words.get(i).getPosTag();
      tokens[i] = words.get(i).getWord();
      
      //Constants.print("token : "+ tokens[i]  + "_" + tags[i]);
    }
    targetLemma = Loader.getLemmatizer().lemmatize(
        tokens[targetWordIndex], tags[targetWordIndex]);
    //Constants.print("lemma  : "+ targetLemma);
    
    Constants.print(lesk.disambiguate(tokens, tags, targetWordIndex,targetLemma));
    Constants.printResults(lesk,
        lesk.disambiguate(tokens, tags, targetWordIndex, targetLemma));
    Constants.print("----------------------------------------");
  }

}