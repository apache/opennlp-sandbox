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

package opennlp.tools.disambiguator.ims;

import java.util.ArrayList;

import opennlp.tools.disambiguator.PreProcessor;
import opennlp.tools.disambiguator.WordToDisambiguate;

public class WTDIMS extends WordToDisambiguate {

  protected String[] posOfSurroundingWords;
  protected String[] surroundingWords;
  protected String[] localCollocations;

  protected String[] features;

  public WTDIMS(String[] sentence, int word, int sense) {
    super(sentence, word, sense);

  }

  public WTDIMS(String[] sentence, int word) {
    super(sentence, word);
  }

  public WTDIMS(String xmlWord, ArrayList<String> xmlAnswers,
      String xmlSentence, String xmlrawWord) {
    super();

    // this.word = xmlWord;

    this.sentence = PreProcessor.tokenize(xmlSentence);
    this.posTags = PreProcessor.tag(this.sentence);

    for (int i = 0; i < sentence.length; i++) {
      if (xmlrawWord.equals(sentence[i])) {
        this.wordIndex = i;
        break;
      }
    }

    this.senseIDs = xmlAnswers;

  }

  public String[] getPosOfSurroundingWords() {
    return posOfSurroundingWords;
  }

  public void setPosOfSurroundingWords(String[] posOfSurroundingWords) {
    this.posOfSurroundingWords = posOfSurroundingWords;
  }

  public String[] getSurroundingWords() {
    return surroundingWords;
  }

  public void setSurroundingWords(String[] surroundingWords) {
    this.surroundingWords = surroundingWords;
  }

  public String[] getLocalCollocations() {
    return localCollocations;
  }

  public void setLocalCollocations(String[] localCollocations) {
    this.localCollocations = localCollocations;
  }

  public String[] getFeatures() {
    return this.features;
  }

  public void setFeatures(String[] features) {
    this.features = features;
  }

}
