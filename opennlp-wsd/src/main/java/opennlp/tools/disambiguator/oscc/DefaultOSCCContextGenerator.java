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

package opennlp.tools.disambiguator.oscc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import net.sf.extjwnl.data.Synset;
import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WordPOS;

/**
 * The default Context Generator of IMS
 */
public class DefaultOSCCContextGenerator implements OSCCContextGenerator {

  public DefaultOSCCContextGenerator() {
  }

  public String[] extractSurroundingContextClusters(int index, String[] toks,
      String[] tags, String[] lemmas, int windowSize) {

    ArrayList<String> contextClusters = new ArrayList<String>();

    for (int i = 0; i < toks.length; i++) {
      if (lemmas != null) {

        if (!WSDHelper.stopWords.contains(toks[i].toLowerCase())
            && (index != i)) {

          String lemma = lemmas[i].toLowerCase().replaceAll("[^a-z_]", "")
              .trim();
          
          WordPOS word = new WordPOS(lemma, tags[i]);

          // TODO check fix for "_" and null pointers
          if (lemma.length() > 1 && !lemma.contains("_")) {
            try{
            ArrayList<Synset> synsets = word.getSynsets();
            if (synsets!=null && synsets.size() > 0 ){
              contextClusters.add(synsets.get(0).getOffset() + "");
            }
            }catch(NullPointerException ex)
            {
              //TODO tagger mistake add proper exception
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
  @Override
  public String[] getContext(int index, String[] toks, String[] tags,
      String[] lemmas, int windowSize) {

    HashSet<String> surroundingContextClusters = new HashSet<>();
    surroundingContextClusters.addAll(Arrays
        .asList(extractSurroundingContextClusters(index, toks, tags, lemmas,
            windowSize)));

    String[] serializedFeatures = new String[surroundingContextClusters.size()];

    int i = 0;

    for (String feature : surroundingContextClusters) {
      serializedFeatures[i] = "F" + i + "=" + feature;
      i++;
    }

    return serializedFeatures;

  }

  public String[] getContext(WSDSample sample, int windowSize) {

    return getContext(sample.getTargetPosition(), sample.getSentence(),
        sample.getTags(), sample.getLemmas(), windowSize);
  }

}
