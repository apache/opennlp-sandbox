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
import java.util.Arrays;

import opennlp.tools.disambiguator.ims.WTDIMS;

/**
 * Class for the extraction of features for the different Supervised
 * Disambiguation apporaches.<br>
 * Each set of methods refer to one approach
 * <ul>
 * <li>IMS (It Makes Sense): check {@link https
 * ://www.comp.nus.edu.sg/~nght/pubs/ims.pdf} for details about this approach</li>
 * <li>SST (SuperSense Tagging): check {@link http
 * ://ttic.uchicago.edu/~altun/pubs/CiaAlt_EMNLP06.pdf} for details about this
 * approach</li>
 * </ul>
 * 
 * The first methods serve to extract the features for the algorithm IMS. Three
 * families of features are to be extracted: - PoS of Surrounding Words: it
 * requires one parameter: "Window size" - Surrounding Words: no parameters are
 * required - Local Collocations: it requires one parameter: "the n-gram"
 * 
 * check {@link https://www.comp.nus.edu.sg/~nght/pubs/ims.pdf} for details
 * about this approach
 */

public class FeaturesExtractor {

  /**
   * Constructor
   */
  public FeaturesExtractor() {
    super();
  }

  // IMS approach

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

        // if (!word.equals("") /*&& Constants.isRelevant(posTags[i])*/) {
        if (Loader.getEnglishWords().containsKey(word)) {
          String lemma = Loader.getLemmatizer().lemmatize(word, posTags[i]);
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

  /**
   * This methods generates the full list of Surrounding words, from the
   * training data. These data will be later used for the generation of the
   * features qualified of "Surrounding words
   * 
   * @param trainingData
   *          list of the training samples (type {@link WTDIMS}
   * @return the list of all the surrounding words from all the training data
   */
  public ArrayList<String> extractTrainingSurroundingWords(
      ArrayList<WTDIMS> trainingData) {

    ArrayList<String> list = new ArrayList<String>();

    for (WTDIMS word : trainingData) {
      for (String sWord : word.getSurroundingWords()) {
        list.add(sWord);
      }
    }

    return list;

  }

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
   *          the word to disambiguate
   * @param listSurrWords
   *          the full list of surrounding words of the training data
   * @return the Context of the wordToDisambiguate
   */
  public void serializeIMSFeatures(WTDIMS word, ArrayList<String> listSurrWords) {

    String[] posOfSurroundingWords = word.getPosOfSurroundingWords();
    ArrayList<String> surroundingWords = new ArrayList<String>(
        Arrays.asList((word.getSurroundingWords())));
    String[] localCollocations = word.getLocalCollocations();

    String[] serializedFeatures = new String[posOfSurroundingWords.length
        + localCollocations.length + listSurrWords.size()];

    int i = 0;

    for (String feature : posOfSurroundingWords) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    for (String feature : localCollocations) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    for (String feature : listSurrWords) {
      serializedFeatures[i] = "F" + i + "=0";
      if (surroundingWords.contains(feature)) {
        serializedFeatures[i] = "F" + i + "=1";
      }
      i++;

    }

    word.setFeatures(serializedFeatures);

  }

  // SST approach

}