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
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.dictionary.Dictionary;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;

public class WSDSample {

  private List<String> sentence;
  private List<String> tags;
  private int senseID;
  private List<String> senseIDs;
  private int targetPosition;
  private String targetLemma;

  public WSDSample(String sentence[], String tags[], int targetPosition,
      String targetLemma, int senseID) {
    this.sentence = Collections.unmodifiableList(new ArrayList<String>(Arrays
        .asList(sentence)));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(Arrays
        .asList(tags)));
    this.targetPosition = targetPosition;
    this.targetLemma = targetLemma;
    this.senseID = senseID;
    checkArguments();
  }

  public WSDSample(String sentence[], String tags[], int targetPosition,
      String targetLemma, String senseIDs[]) {
    this.sentence = Collections.unmodifiableList(new ArrayList<String>(Arrays
        .asList(sentence)));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(Arrays
        .asList(tags)));
    this.targetPosition = targetPosition;
    this.targetLemma = targetLemma;
    this.senseIDs = Collections.unmodifiableList(new ArrayList<String>(Arrays
        .asList(senseIDs)));
    ;
    checkArguments();
  }

  public WSDSample(List<String> sentence, List<String> tags,
      int targetPosition, String targetLemma, int senseID) {
    this.sentence = Collections
        .unmodifiableList(new ArrayList<String>(sentence));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
    this.targetPosition = targetPosition;
    this.targetLemma = targetLemma;
    this.senseID = senseID;
    checkArguments();
  }

  public WSDSample(List<String> sentence, List<String> tags,
      int targetPosition, String targetLemma, List<String> senseIDs) {
    this.sentence = Collections
        .unmodifiableList(new ArrayList<String>(sentence));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
    this.targetPosition = targetPosition;
    this.targetLemma = targetLemma;
    this.senseIDs = senseIDs;
    checkArguments();
  }

  public WSDSample(String sentence[], String tags[], int targetPosition,
      String targetLemma) {
    this(sentence, tags, targetPosition, targetLemma, -1);
  }

  public WSDSample(List<String> sentence, List<String> tags,
      int targetPosition, String targetLemma) {
    this(sentence, tags, targetPosition, targetLemma, -1);
  }

  private void checkArguments() {
    if (sentence.size() != tags.size() || targetPosition < 0
        || targetPosition >= tags.size())
      throw new IllegalArgumentException(
          "There must be exactly one tag for each token!");

    if (sentence.contains(null) || tags.contains(null))
      throw new IllegalArgumentException("null elements are not allowed!");
  }

  public String[] getSentence() {
    return sentence.toArray(new String[sentence.size()]);
  }

  public String[] getTags() {
    return tags.toArray(new String[tags.size()]);
  }

  public int getTargetPosition() {
    return targetPosition;
  }

  public int getSenseID() {
    return senseID;
  }

  public List<String> getSenseIDs() {
    return senseIDs;
  }

  public String getTargetWord() {
    return sentence.get(targetPosition);
  }

  public String getTargetTag() {
    return tags.get(targetPosition);
  }

  public String getTargetLemma() {
    return targetLemma;
  }
  
  public void setSentence(List<String> sentence) {
    this.sentence = sentence;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public void setSenseID(int senseID) {
    this.senseID = senseID;
  }

  public void setSenseIDs(List<String> senseIDs) {
    this.senseIDs = senseIDs;
  }

  public void setTargetPosition(int targetPosition) {
    this.targetPosition = targetPosition;
  }

  public void setTargetLemma(String targetLemma) {
    this.targetLemma = targetLemma;
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

  public static WSDSample parse(String sentenceString)
      throws InvalidFormatException {

    String tokenTags[] = WhitespaceTokenizer.INSTANCE.tokenize(sentenceString);

    int position = Integer.parseInt(tokenTags[0]);
    String lemma = tokenTags[1];
    String sentence[] = new String[tokenTags.length - 2];
    String tags[] = new String[tokenTags.length - 2];

    for (int i = 2; i < tokenTags.length; i++) {
      int split = tokenTags[i].lastIndexOf("_");

      if (split == -1) {
        throw new InvalidFormatException("Cannot find \"_\" inside token!");
      }

      sentence[i] = tokenTags[i].substring(0, split);
      tags[i] = tokenTags[i].substring(split + 1);
    }

    return new WSDSample(sentence, tags, position, lemma);
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
      return Dictionary
          .getDefaultResourceInstance()
          .lookupIndexWord(Constants.getPOS(this.getTargetTag()),
              this.getTargetWord()).getSenses();
    } catch (JWNLException e) {
      e.printStackTrace();
    }
    return null;
  }
}