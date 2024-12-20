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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The default Context Generator of the
 * <a href="https://aclanthology.org/P10-4014.pdf"> IMS (It Makes Sense)</a> approach.
 *
 * @see WSDContextGenerator
 */
public class IMSWSDContextGenerator implements WSDContextGenerator {

  /*
   * Extracts POS tags of surrounding words for the word at the specified index
   * within the windowSize.
   */
  private String[] extractPosOfSurroundingWords(int index, String[] tags, int windowSize) {

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

  // TODO consider the windowSize
  String[] extractSurroundingContext(int index, String[] toks, String[] lemmas, int windowSize) {

    List<String> contextWords = new ArrayList<>();

    for (int i = 0; i < toks.length; i++) {
      if (lemmas != null) {
        if (!WSDHelper.STOP_WORDS.contains(toks[i].toLowerCase()) && (index != i)) {

          String lemma = lemmas[i].toLowerCase();
          lemma = PATTERN.matcher(lemma).replaceAll("").trim();

          if (lemma.length() > 1) {
            contextWords.add(lemma);
          }
        }
      }
    }
    return contextWords.toArray(new String[0]);
  }

  private String[] extractLocalCollocations(int index, String[] sentence, int ngram) {

    /*
     * Here the author used only 11 features of this type. the range was set to
     * 3 (bigrams extracted in a way that they are at max separated by 1 word).
     */
    List<String> localCollocations = new ArrayList<>();

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
    String[] res;
    res = localCollocations.toArray(new String[0]);

    return res;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getContext(int index, String[] tokens,
    String[] tags, String[] lemmas, int ngram, int windowSize, List<String> model) {

    String[] posOfSurroundingWords =
            extractPosOfSurroundingWords(index, tokens, windowSize);
    Set<String> surroundingWords = new HashSet<>(Arrays.asList(
            extractSurroundingContext(index, tokens, lemmas, windowSize)));
    String[] localCollocations = extractLocalCollocations(index, tokens, ngram);

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
      if (surroundingWords.contains(word)) {
        serializedFeatures[i] = "F" + i + "=1";
      } else {
        serializedFeatures[i] = "F" + i + "=0";
      }
      i++;
    }
    return serializedFeatures;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getContext(WSDSample sample, int ngram, int windowSize,
                             List<String> model) {
    return getContext(sample.getTargetPosition(), sample.getSentence(),
      sample.getTags(), sample.getLemmas(), ngram, windowSize, model);
  }
}
