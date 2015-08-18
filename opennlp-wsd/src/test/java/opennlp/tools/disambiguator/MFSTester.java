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

import opennlp.tools.disambiguator.mfs.MFS;

/**
 * This is a typical example of how to call the disambiguation function in the
 * MFS class.
 */
public class MFSTester {

  public static void main(String[] args) {
    
    String modelsDir = "src\\test\\resources\\models\\";
    WSDHelper.loadTokenizer(modelsDir+"en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir+"en-lemmatizer.dict");
    WSDHelper.loadTagger(modelsDir+"en-pos-maxent.bin");
    
    
    MFS mfs = new MFS();

    String test1 = "I went fishing for some sea bass.";
    String[] sentence = WSDHelper.getTokenizer().tokenize(test1);
    List<WordPOS> words = WSDHelper.getAllRelevantWords(sentence);
    int targetWordIndex = 2;
    String[] tags = new String[words.size()];
    String[] tokens = new String[words.size()];
    for (int i=0;i<words.size();i++){
      tags[i] = words.get(i).getPosTag();
      tokens[i] = words.get(i).getWord();
      
     // Constants.print("token : "+ tokens[i]  + "_" + tags[i]);
    }
    String targetLemma = WSDHelper.getLemmatizer().lemmatize(
        tokens[targetWordIndex], tags[targetWordIndex]);
   // Constants.print("lemma  : "+ targetLemma);
    
    WSDHelper.print(mfs.disambiguate(tokens, tags, targetWordIndex,targetLemma));
    WSDHelper.printResults(mfs,
        mfs.disambiguate(tokens, tags, targetWordIndex, targetLemma));
    WSDHelper.print("----------------------------------------");
    
    String test2 = "it was a strong argument that his hypothesis was true";
    sentence = WSDHelper.getTokenizer().tokenize(test2);
    words = WSDHelper.getAllRelevantWords(sentence);
    targetWordIndex = 1;
    tags = new String[words.size()];
    tokens = new String[words.size()];
    for (int i=0;i<words.size();i++){
      tags[i] = words.get(i).getPosTag();
      tokens[i] = words.get(i).getWord();
      
      //Constants.print("token : "+ tokens[i]  + "_" + tags[i]);
    }
    targetLemma = WSDHelper.getLemmatizer().lemmatize(
        tokens[targetWordIndex], tags[targetWordIndex]);
    //Constants.print("lemma  : "+ targetLemma);
    
    WSDHelper.print(mfs.disambiguate(tokens, tags, targetWordIndex,targetLemma));
    WSDHelper.printResults(mfs,
        mfs.disambiguate(tokens, tags, targetWordIndex, targetLemma));
    WSDHelper.print("----------------------------------------");
    
    String test3 = "the component was highly radioactive to the point that it has been activated the second it touched water";
   
    sentence = WSDHelper.getTokenizer().tokenize(test3);
    words = WSDHelper.getAllRelevantWords(sentence);
    targetWordIndex = 4;
    tags = new String[words.size()];
    tokens = new String[words.size()];
    for (int i=0;i<words.size();i++){
      tags[i] = words.get(i).getPosTag();
      tokens[i] = words.get(i).getWord();
      
      //Constants.print("token : "+ tokens[i]  + "_" + tags[i]);
    }
    targetLemma = WSDHelper.getLemmatizer().lemmatize(
        tokens[targetWordIndex], tags[targetWordIndex]);
    //Constants.print("lemma  : "+ targetLemma);
    
    WSDHelper.print(mfs.disambiguate(tokens, tags, targetWordIndex,targetLemma));
    WSDHelper.printResults(mfs,
        mfs.disambiguate(tokens, tags, targetWordIndex, targetLemma));
    WSDHelper.print("----------------------------------------");
  }

}