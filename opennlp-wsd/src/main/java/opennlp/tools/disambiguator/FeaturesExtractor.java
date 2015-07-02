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

import opennlp.tools.disambiguator.ims.WTDIMS;

public class FeaturesExtractor {

  public FeaturesExtractor() {
    super();
  }

  /**
   * @Algorithm: IMS (It Makes Sense)
   * 
   *             The following methods serve to extract the features for the
   *             algorithm IMS.
   * 
   *             Three families of features are to be extracted: - PoS of
   *             Surrounding Words: it requires one parameter: "Window size" -
   *             Surrounding Words: no parameters are required - Local
   *             Collocations: it requires one parameter: "the n-gram"
   * 
   */
  private String[] extractPosOfSurroundingWords(String[] sentence,
      int wordIndex, int windowSize) {

    String[] taggedSentence = Loader.getTagger().tag(sentence);

    String[] tags = new String[2 * windowSize + 1];

    int j = 0;

    for (int i = wordIndex - windowSize; i < wordIndex + windowSize; i++) {
      if (i < 0 || i >= sentence.length) {
        tags[j] = "null";
      } else {
        tags[j] = taggedSentence[i].toLowerCase();
      }
      j++;
    }

    return tags;
  }

  private String[] extractSurroundingWords(String[] sentence, int wordIndex) {

    String[] posTags = Loader.getTagger().tag(sentence);

    ArrayList<String> contextWords = new ArrayList<String>();

    for (int i = 0; i < sentence.length; i++) {

      if (!Constants.stopWords.contains(sentence[i].toLowerCase())
          && (wordIndex != i)) {

        String word = sentence[i].toLowerCase().replaceAll("[^a-z]", "").trim();

        if (!word.equals("")) {
          String lemma = Loader.getLemmatizer().lemmatize(sentence[i],
              posTags[i]);
          contextWords.add(lemma);
        }

      }
    }

    return contextWords.toArray(new String[contextWords.size()]);
  }

  private String[] extractLocalCollocations(String[] sentence, int wordIndex,
      int ngram) {
    /**
     * Here the author used only 11 features of this type. the range was set to
     * 3 (bigrams extracted in a way that they are at max separated by 1 word).
     */

    ArrayList<String> localCollocations = new ArrayList<String>();

    for (int i = wordIndex - ngram; i <= wordIndex + ngram; i++) {

      if (!(i < 0 || i > sentence.length - 3)) {
        if ((i != wordIndex) && (i + 1 != wordIndex)
            && (i + 1 < wordIndex + ngram)) {
          String lc = (sentence[i] + " " + sentence[i + 1]).toLowerCase();
          localCollocations.add(lc);
        }
        if ((i != wordIndex) && (i + 2 != wordIndex)
            && (i + 2 < wordIndex + ngram)) {
          String lc = (sentence[i] + " " + sentence[i + 2]).toLowerCase();
          localCollocations.add(lc);
        }
      }

    }

    String[] res = new String[localCollocations.size()];
    res = localCollocations.toArray(res);

    return res;
  }

  // public method
  /**
   * This method generates the different set of features related to the IMS
   * approach and store them in the corresponding attributes of the WTDIMS
   * 
   * @param word
   *          the word to disambiguate [object: WTDIMS]
   * @param windowSize
   *          the parameter required to generate the features qualified of
   *          "PoS of Surrounding Words"
   * @param ngram
   *          the parameter required to generate the features qualified of
   *          "Local Collocations"
   */
  public void extractIMSFeatures(WTDIMS word, int windowSize, int ngram) {

    word.setPosOfSurroundingWords(extractPosOfSurroundingWords(
        word.getSentence(), word.getWordIndex(), windowSize));
    word.setSurroundingWords(extractSurroundingWords(word.getSentence(),
        word.getWordIndex()));
    word.setLocalCollocations(extractLocalCollocations(word.getSentence(),
        word.getWordIndex(), ngram));

  }

  /**
   * This generates the context of IMS. It supposes that the features have
   * already been extracted and stored in the WTDIMS object, therefore it
   * doesn't require any parameters.
   * 
   * @param word
   * @return the Context of the wordToDisambiguate
   */
  public String[] serializeIMSFeatures(WTDIMS word) {

    String[] posOfSurroundingWords = word.getPosOfSurroundingWords();
    String[] surroundingWords = word.getSurroundingWords();
    String[] localCollocations = word.getLocalCollocations();

    String[] serializedFeatures = new String[posOfSurroundingWords.length
        + surroundingWords.length + localCollocations.length];

    int i = 0;

    for (String feature : posOfSurroundingWords) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    for (String feature : surroundingWords) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    for (String feature : localCollocations) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    return serializedFeatures;

  }
}
