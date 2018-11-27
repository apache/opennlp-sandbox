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

import java.util.ArrayList;
import java.util.List;

import net.sf.extjwnl.data.POS;
import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;

public class WTDIMS {

  // Attributes related to the context
  protected String[] sentence;
  protected String[] posTags;
  protected String[] lemmas;
  protected int wordIndex;
  protected int sense;
  protected String[] senseIDs;

  // Attributes related to IMS features
  protected String[] posOfSurroundingWords;
  protected String[] surroundingWords;
  protected String[] localCollocations;
  protected String[] features;

  public WTDIMS(String[] sentence, String[] posTags, String[] lemmas,
    int wordIndex) {
    this.sentence = sentence;
    this.posTags = posTags;
    this.wordIndex = wordIndex;
    this.lemmas = lemmas;
  }

  public WTDIMS(String[] sentence, String[] posTags, String[] lemmas,
    int wordIndex, String[] senseIDs) {
    this.sentence = sentence;
    this.posTags = posTags;
    this.wordIndex = wordIndex;
    this.lemmas = lemmas;
    this.senseIDs = senseIDs;

  }

  public WTDIMS(String[] sentence, String[] posTags, String[] lemmas,
    String word, String[] senseIDs) {
    super();

    this.sentence = sentence;
    this.posTags = posTags;
    this.lemmas = lemmas;

    for (int i = 0; i < sentence.length; i++) {
      if (word.equals(sentence[i])) {
        this.wordIndex = i;
        break;
      }
    }

    this.senseIDs = senseIDs;

  }

  public WTDIMS(WSDSample sample) {
    this.sentence = sample.getSentence();
    this.posTags = sample.getTags();
    this.lemmas = sample.getLemmas();
    this.wordIndex = sample.getTargetPosition();
    this.senseIDs = sample.getSenseIDs();

  }

  public String[] getSentence() {
    return sentence;
  }

  public void setSentence(String[] sentence) {
    this.sentence = sentence;
  }

  public String[] getPosTags() {
    return posTags;
  }

  public void setPosTags(String[] posTags) {
    this.posTags = posTags;
  }

  public int getWordIndex() {
    return wordIndex;
  }

  public void setWordIndex(int wordIndex) {
    this.wordIndex = wordIndex;
  }

  public String[] getLemmas() {
    return lemmas;
  }

  public void setLemmas(String[] lemmas) {
    this.lemmas = lemmas;
  }

  public int getSense() {
    return sense;
  }

  public void setSense(int sense) {
    this.sense = sense;
  }

  public String[] getSenseIDs() {
    return senseIDs;
  }

  public void setSenseIDs(String[] senseIDs) {
    this.senseIDs = senseIDs;
  }

  public String getWord() {
    return this.getSentence()[this.getWordIndex()];
  }

  public String getWordTag() {

    String wordBaseForm = this.getLemmas()[this.getWordIndex()];

    String ref = "";

    if ((WSDHelper.getPOS(this.getPosTags()[this.getWordIndex()]) != null)) {
      if (WSDHelper.getPOS(this.getPosTags()[this.getWordIndex()])
        .equals(POS.VERB)) {
        ref = wordBaseForm + ".v";
      } else if (WSDHelper.getPOS(this.getPosTags()[this.getWordIndex()])
        .equals(POS.NOUN)) {
        ref = wordBaseForm + ".n";
      } else if (WSDHelper.getPOS(this.getPosTags()[this.getWordIndex()])
        .equals(POS.ADJECTIVE)) {
        ref = wordBaseForm + ".a";
      } else if (WSDHelper.getPOS(this.getPosTags()[this.getWordIndex()])
        .equals(POS.ADVERB)) {
        ref = wordBaseForm + ".r";
      }
    }

    return ref;
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
