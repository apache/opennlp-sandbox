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
import java.util.HashSet;

import net.sf.extjwnl.data.Synset;

/**
 * The default Context Generator of the OSCC approach
 */
public class OSCCWSDContextGenerator implements WSDContextGenerator {

  public String[] extractSurroundingContextClusters(int index, String[] toks,
    String[] tags, String[] lemmas, int windowSize) {

    // TODO consider windowSize
    ArrayList<String> contextClusters = new ArrayList<String>();

    for (int i = 0; i < toks.length; i++) {
      if (lemmas != null) {

        if (!WSDHelper.stopWords.contains(toks[i].toLowerCase()) && (index
          != i)) {

          String lemma = lemmas[i].toLowerCase().replaceAll("[^a-z_]", "")
            .trim();

          WordPOS word = new WordPOS(lemma, tags[i]);

          if (lemma.length() > 1) {
            try {
              ArrayList<Synset> synsets = word.getSynsets();
              if (synsets != null && synsets.size() > 0) {
                for (Synset syn : synsets) {
                  contextClusters.add(syn.getOffset() + "");
                }
              }
            } catch (NullPointerException ex) {
              // TODO tagger mistake add proper exception
            }
          }

        }
      }
    }

    return contextClusters.toArray(new String[contextClusters.size()]);

  }

  /**
   * Get Context of a word To disambiguate
   *
   * @return The OSCC context of the word to disambiguate
   */
  @Override public String[] getContext(int index, String[] toks, String[] tags,
    String[] lemmas, int ngram, int windowSize, ArrayList<String> model) {

    HashSet<String> surroundingContextClusters = new HashSet<>();
    surroundingContextClusters.addAll(Arrays.asList(
      extractSurroundingContextClusters(index, toks, tags, lemmas,
        windowSize)));

    String[] serializedFeatures = new String[model.size()];

    int i = 0;
    for (String word : model) {
      if (surroundingContextClusters.contains(word.toString())) {
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
      sample.getTags(), sample.getLemmas(), 0, windowSize, model);
  }

}




