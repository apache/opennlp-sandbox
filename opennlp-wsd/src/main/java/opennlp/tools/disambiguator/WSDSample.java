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

package opennlp.tools.disambiguator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.dictionary.Dictionary;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;

public class WSDSample {

  private String[] sentence;
  private String[] tags;
  private String[] lemmas;
  private int senseID;
  private String[] senseIDs;
  private int targetPosition;

  public WSDSample(String[] sentence, String[] tags, String[] lemmas,
      int targetPosition, int senseID) {
    this.sentence = sentence;
    this.tags = tags;
    this.targetPosition = targetPosition;
    this.lemmas = lemmas;
    this.senseID = senseID;
    checkArguments();
  }

  public WSDSample(String[] sentence, String[] tags, String[] lemmas,
      int targetPosition) {
    this(sentence, tags, lemmas, targetPosition, null);
  }

  public WSDSample(String[] sentence, String[] tags, String[] lemmas,
      int targetPosition, String[] senseIDs) {
    this.sentence = sentence;
    this.tags = tags;
    this.targetPosition = targetPosition;
    this.lemmas = lemmas;
    this.senseIDs = senseIDs;
    checkArguments();
  }

  private void checkArguments() {
    if (sentence.length != tags.length || tags.length != lemmas.length
        || targetPosition < 0 || targetPosition >= tags.length)
      throw new IllegalArgumentException("Some inputs are not correct");
  }

  public String[] getSentence() {
    return sentence;
  }

  public String[] getTags() {
    return tags;
  }

  public String[] getLemmas() {
    return lemmas;
  }

  public int getTargetPosition() {
    return targetPosition;
  }

  public int getSenseID() {
    return senseID;
  }

  public String[] getSenseIDs() {
    return senseIDs;
  }

  public String getTargetWord() {
    return sentence[targetPosition];
  }

  public String getTargetTag() {
    return tags[targetPosition];
  }

  public void setSentence(String[] sentence) {
    this.sentence = sentence;
  }

  public void setTags(String[] tags) {
    this.tags = tags;
  }

  public void setLemmas(String[] lemmas) {
    this.lemmas = lemmas;
  }

  public void setSenseID(int senseID) {
    this.senseID = senseID;
  }

  public void setSenseIDs(String[] senseIDs) {
    this.senseIDs = senseIDs;
  }

  public void setTargetPosition(int targetPosition) {
    this.targetPosition = targetPosition;
  }

  @Override
  public String toString() {

    StringBuilder result = new StringBuilder();
    result.append("target at : " + this.targetPosition + " in : ");
    for (int i = 0; i < getSentence().length; i++) {
      result.append(i);
      result.append(".");
      result.append(getSentence()[i]);
      result.append('_');
      result.append(getTags()[i]);
      result.append(' ');
    }

    if (result.length() > 0) {
      // get rid of last space
      result.setLength(result.length() - 1);
    }

    return result.toString();
  }

  /*
   * Parses a sample of format : TargetIndex TargetLemma Token Tag Token Tag ...
   */
  public static WSDSample parse(String sentenceString)
      throws InvalidFormatException {

    String tokenTags[] = WhitespaceTokenizer.INSTANCE.tokenize(sentenceString);

    int position = Integer.parseInt(tokenTags[0]);
    String sentence[] = new String[tokenTags.length - 1];
    String tags[] = new String[tokenTags.length - 1];
    String lemmas[] = new String[tokenTags.length - 1];

    for (int i = 1; i < tokenTags.length; i++) {
      int split = tokenTags[i].lastIndexOf("_");

      if (split == -1) {
        throw new InvalidFormatException("Cannot find \"_\" inside token!");
      }

      sentence[i] = tokenTags[i].substring(0, split);
      tags[i] = tokenTags[i].substring(split + 1);
      lemmas[i] = tokenTags[i].substring(split + 2);
    }

    return new WSDSample(sentence, tags, lemmas, position);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof WSDSample) {
      WSDSample a = (WSDSample) obj;

      return Arrays.equals(getSentence(), a.getSentence())
          && Arrays.equals(getTags(), a.getTags())
          && getTargetPosition() == a.getTargetPosition();
    } else {
      return false;
    }
  }

  // Return the synsets (thus the senses) of the current target word
  public List<Synset> getSynsets() {
    try {
      return Dictionary.getDefaultResourceInstance()
          .lookupIndexWord(WSDHelper.getPOS(this.getTargetTag()),
              this.getTargetWord())
          .getSenses();
    } catch (JWNLException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String getTargetWordTag() {

    String wordBaseForm = this.getLemmas()[this.getTargetPosition()];

    String ref = "";

    if ((WSDHelper.getPOS(this.getTargetTag()) != null)) {
      if (WSDHelper.getPOS(this.getTargetTag()).equals(POS.VERB)) {
        ref = wordBaseForm + ".v";
      } else if (WSDHelper.getPOS(this.getTargetTag()).equals(POS.NOUN)) {
        ref = wordBaseForm + ".n";
      } else if (WSDHelper.getPOS(this.getTargetTag()).equals(POS.ADJECTIVE)) {
        ref = wordBaseForm + ".a";
      } else if (WSDHelper.getPOS(this.getTargetTag()).equals(POS.ADVERB)) {
        ref = wordBaseForm + ".r";
      }
    }

    return ref;
  }

}