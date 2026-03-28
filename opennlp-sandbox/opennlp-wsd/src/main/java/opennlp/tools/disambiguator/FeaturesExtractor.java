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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for the extraction of features for the different Supervised
 * Disambiguation approaches.<br>
 * Each set of methods refer to one approach
 * <ul>
 * <li>IMS (It Makes Sense) approach: see <a href="https://aclanthology.org/P10-4014.pdf">
 *   It Makes Sense: A Wide-Coverage Word Sense Disambiguation System for Free Text</a>
 * </li>
 * <li>SST (SuperSense Tagging) approach: check
 * <a href="https://ttic.uchicago.edu/~altun/pubs/CiaAlt_EMNLP06.pdf">
 *   https://ttic.uchicago.edu/~altun/pubs/CiaAlt_EMNLP06.pdf</a>
 * </li>
 * </ul>
 * 
 * The first methods serve to extract the features for the IMS algorithm. Three
 * families of features are to be extracted:
 * <ul>
 *   <li>PoS of Surrounding Words: it requires one parameter: "Window size"</li>
 *   <li>Surrounding Words: no parameters are required</li>
 *   <li>Local Collocations: it requires one parameter: the "n-gram" number</li>
 * </ul>
 *
 * @see WTDIMS
 * @see <a href="https://aclanthology.org/P10-4014.pdf">
 *   It Makes Sense: A Wide-Coverage Word Sense Disambiguation System for Free Text</a>
 * for details about this approach.
 */
public class FeaturesExtractor {

  /*
   * Extracts POS tags of surrounding words of a given wordToDisambiguate instance.
   */
  private String[] extractPosOfSurroundingWords(WTDIMS wordToDisambiguate, int windowSize) {

    String[] taggedSentence = wordToDisambiguate.getPosTags();
    String[] tags = new String[2 * windowSize + 1];

    int j = 0;

    for (int i = wordToDisambiguate.getWordIndex() - windowSize; i < wordToDisambiguate
        .getWordIndex() + windowSize; i++) {
      if (i < 0 || i >= wordToDisambiguate.getSentence().length) {
        tags[j] = "null";
      } else {
        tags[j] = taggedSentence[i].toLowerCase();
      }
      j++;
    }

    return tags;
  }

  /*
   * Extracts surrounding lemmas of a given wordToDisambiguate instance.
   * Irrelevant stop words are skipped.
   */
  private String[] extractSurroundingWords(WTDIMS wordToDisambiguate) {

    List<String> contextWords = new ArrayList<>();

    for (int i = 0; i < wordToDisambiguate.getSentence().length; i++) {
      if (wordToDisambiguate.getLemmas() != null) {
        if (!WSDHelper.STOP_WORDS.contains(wordToDisambiguate.getSentence()[i]
            .toLowerCase()) && (wordToDisambiguate.getWordIndex() != i)) {

          String lemma = wordToDisambiguate.getLemmas()[i].toLowerCase()
              .replaceAll("[^a-z_]", "").trim();

          if (lemma.length() > 1) {
            contextWords.add(lemma);
          }
        }
      }
    }

    return contextWords.toArray(new String[0]);
  }

  private String[] extractLocalCollocations(WTDIMS wtd, int ngram) {
    /*
     * Here the author used only 11 features of this type. the range was set to
     * 3 (bigrams extracted in a way that they are at max separated by 1 word).
     */
    List<String> localCollocations = new ArrayList<>();

    for (int i = wtd.getWordIndex() - ngram; i <= wtd.getWordIndex() + ngram; i++) {

      if (!(i < 0 || i > wtd.getSentence().length - 3)) {
        if ((i != wtd.getWordIndex()) && (i + 1 != wtd.getWordIndex())
            && (i + 1 < wtd.getWordIndex() + ngram)) {
          String lc = (wtd.getSentence()[i] + " " + wtd.getSentence()[i + 1]).toLowerCase();
          localCollocations.add(lc);
        }
        if ((i != wtd.getWordIndex()) && (i + 2 != wtd.getWordIndex())
            && (i + 2 < wtd.getWordIndex() + ngram)) {
          String lc = (wtd.getSentence()[i] + " " + wtd.getSentence()[i + 2]).toLowerCase();
          localCollocations.add(lc);
        }
      }

    }

    String[] res = new String[localCollocations.size()];
    res = localCollocations.toArray(res);

    return res;
  }

  /**
   * Generates the full list of surrounding words for the specified
   * {@code trainingData}.
   * These data will be used for the generation of features
   * qualified for "Surrounding words".
   * 
   * @param trainingData A list of the training samples (type {@link WTDIMS}.
   *                     Must not be {@code null}.
   *
   * @return A list of all the surrounding words for the {@code trainingData}.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public List<String> extractTrainingSurroundingWords(List<WTDIMS> trainingData) {
    if (trainingData == null) {
      throw new IllegalArgumentException("TrainingData must not be null!");
    }
    Map<String, Object> words = new HashMap<>();
    for (WTDIMS word : trainingData) {
      for (String sWord : word.getSurroundingWords()) {
        if (!words.containsKey(sWord.toLowerCase()))
          ;
        words.put(sWord.toLowerCase(), null);
      }
    }

    return new ArrayList<>(words.keySet());
  }

  /**
   * Generates the different set of features related to the IMS
   * approach and puts them in the corresponding attributes of
   * the {@link WTDIMS word to disambiguate} object.
   * 
   * @param wtd The {@link WTDIMS word to disambiguate}. Must not be {@code null}.
   * @param windowSize The parameter required to generate the features qualified of
   *                   "PoS of Surrounding Words". Must be greater or equal to {@code 1}
   * @param ngram The parameter required to generate the features qualified of
   *              "Local Collocations". Must be greater or equal to {@code 1}.
   *
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public void extractIMSFeatures(WTDIMS wtd, int windowSize, int ngram) {
    if (wtd == null) {
      throw new IllegalArgumentException("Parameter wtd must not be null!");
    }
    if (windowSize < 1 || ngram < 1) {
      throw new IllegalArgumentException("Parameter windowSize or ngram must be at least 1!");
    }
    wtd.setPosOfSurroundingWords(extractPosOfSurroundingWords(wtd, windowSize));
    wtd.setSurroundingWords(extractSurroundingWords(wtd));
    wtd.setLocalCollocations(extractLocalCollocations(wtd, ngram));
  }

  /**
   * Generates the context for the {@link WTDIMS word to disambiguate}.
   *
   * @implNote It is assumed that the features have already been extracted and
   * wrapped in the {@link WTDIMS word to disambiguate}.
   * Therefore, it doesn't require any parameters.
   *
   * @param wtd The {@link WTDIMS word to disambiguate}. Must not be {@code null}.
   * @param listSurrWords The full list of surrounding words of the training data.
   *                      Must not be {@code null}.
   *
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public void serializeIMSFeatures(WTDIMS wtd, List<String> listSurrWords) {
    if (wtd == null || listSurrWords == null) {
      throw new IllegalArgumentException("Parameters must not be null!");
    }
    String[] posOfSurroundingWords = wtd.getPosOfSurroundingWords();
    List<String> surroundingWords = new ArrayList<>(
        Arrays.asList(wtd.getSurroundingWords()));
    String[] localCollocations = wtd.getLocalCollocations();

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
    wtd.setFeatures(serializedFeatures);

  }

}
