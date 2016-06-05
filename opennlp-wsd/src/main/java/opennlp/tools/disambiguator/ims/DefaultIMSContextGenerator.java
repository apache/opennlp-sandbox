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
import java.util.Arrays;
import java.util.HashSet;

import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.ims.WTDIMS;

/**
 * The default Context Generator of IMS
 */
// TODO remove this class later
public class DefaultIMSContextGenerator implements IMSContextGenerator {

  public DefaultIMSContextGenerator() {
  }

  private String[] extractPosOfSurroundingWords(int index, String[] tags,
    int windowSize) {

    String[] windowTags = new String[2 * windowSize + 1];

    int j = 0;

    for (int i = index - windowSize; i < index + windowSize; i++) {
      if (i < 0 || i >= tags.length) {
        windowTags[j] = "null";
      } else {
        windowTags[j] = tags[i].toLowerCase();
      }
      j++;
    }

    return windowTags;
  }

  public String[] extractSurroundingWords(int index, String[] toks,
    String[] lemmas, int windowSize) {

    // TODO consider the windowSize
    ArrayList<String> contextWords = new ArrayList<String>();

    for (int i = 0; i < toks.length; i++) {
      if (lemmas != null) {
        if (!WSDHelper.stopWords.contains(toks[i].toLowerCase()) && (index
          != i)) {

          String lemma = lemmas[i].toLowerCase().replaceAll("[^a-z_]", "")
            .trim();

          if (lemma.length() > 1) {
            contextWords.add(lemma);
          }

        }
      }
    }

    return contextWords.toArray(new String[contextWords.size()]);
  }

  private String[] extractLocalCollocations(int index, String[] sentence,
    int ngram) {
    /**
     * Here the author used only 11 features of this type. the range was set to
     * 3 (bigrams extracted in a way that they are at max separated by 1 word).
     */

    ArrayList<String> localCollocations = new ArrayList<String>();

    for (int i = index - ngram; i <= index + ngram; i++) {

      if (!(i < 0 || i > sentence.length - 2)) {
        if ((i != index) && (i + 1 != index) && (i + 1 < index + ngram)) {
          String lc = sentence[i] + " " + sentence[i + 1];
          localCollocations.add(lc);
        }
        if ((i != index) && (i + 2 != index) && (i + 2 < index + ngram)) {
          String lc = sentence[i] + " " + sentence[i + 2];
          localCollocations.add(lc);
        }
      }

    }
    String[] res = new String[localCollocations.size()];
    res = localCollocations.toArray(new String[localCollocations.size()]);

    return res;
  }

  /**
   * Get Context of a word To disambiguate
   *
   * @return The IMS context of the word to disambiguate
   */
  @Override public String[] getContext(int index, String[] toks, String[] tags,
    String[] lemmas, int ngram, int windowSize, ArrayList<String> model) {

    String[] posOfSurroundingWords = extractPosOfSurroundingWords(index, toks,
      windowSize);

    HashSet<String> surroundingWords = new HashSet<>();
    surroundingWords.addAll(
      Arrays.asList(extractSurroundingWords(index, toks, lemmas, windowSize)));

    String[] localCollocations = extractLocalCollocations(index, toks, ngram);

    String[] serializedFeatures = new String[posOfSurroundingWords.length
      + localCollocations.length + model.size()];

    int i = 0;

    for (String feature : posOfSurroundingWords) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    for (String feature : localCollocations) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }
    for (String word : model) {

      if (surroundingWords.contains(word.toString())) {
        serializedFeatures[i] = "F" + i + "=1";
      } else {
        serializedFeatures[i] = "F" + i + "=0";
      }
      i++;

    }

    return serializedFeatures;

  }

  public String[] getContext(WSDSample sample, int ngram, int windowSize,
    ArrayList<String> model) {

    return getContext(sample.getTargetPosition(), sample.getSentence(),
      sample.getTags(), sample.getLemmas(), ngram, windowSize, model);
  }

}
