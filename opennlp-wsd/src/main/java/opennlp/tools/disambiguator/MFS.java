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

import java.util.List;
import java.util.regex.Pattern;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;

/**
 * Implementation of the <b>Most Frequent Sense</b> baseline approach. This
 * approach returns the senses in order of frequency in WordNet. The first sense
 * is the most frequent.
 */
public class MFS extends WSDisambiguator {

  private static final Pattern SPLIT = Pattern.compile("\\.");

  public MFS() {
    super();
  }

  /*
   * @return the most frequent senses from wordnet
   */
  public static String getMostFrequentSense(WSDSample sample) {

    List<Synset> synsets = sample.getSynsets();
    if (!synsets.isEmpty()) {
      String sampleLemma = sample.getLemmas()[sample.getTargetPosition()];
      for (Word wd : synsets.get(0).getWords()) {
        if (wd.getLemma().equalsIgnoreCase(sampleLemma)) {
          try {
            return WSDParameters.SenseSource.WORDNET.name() + " " + wd.getSenseKey();
          } catch (JWNLException e) {
            e.printStackTrace();
          }
        }
      }
    }
    return "nonesense";
  }

  public static String[] getMostFrequentSenses(WSDSample sample) {

    List<Synset> synsets = sample.getSynsets();
    String[] senseKeys = new String[synsets.size()];

    String sampleLemma = sample.getLemmas()[sample.getTargetPosition()];
    for (int i = 0; i < synsets.size(); i++) {
      for (Word wd : synsets.get(i).getWords()) {
        if (wd.getLemma().equalsIgnoreCase(sampleLemma)) {
          try {
            senseKeys[i] = WSDParameters.SenseSource.WORDNET.name() + " " + wd.getSenseKey();
            break;
          } catch (JWNLException e) {
            e.printStackTrace();
          }
          break;

        }
      }
    }
    return senseKeys;

  }

  @Override
  public String disambiguate(WSDSample sample) {

    if (WSDHelper.isRelevantPOSTag(sample.getTargetTag())) {
      return disambiguate(sample.getTargetWordTag());

    } else {
      if (WSDHelper.getNonRelevWordsDef(sample.getTargetTag()) != null) {
        return WSDParameters.SenseSource.WSDHELPER.name() + " "
            + sample.getTargetTag();
      } else {
        return null;
      }
    }
  }


  public String disambiguate(String wordTag) {

    String[] splitWordTag = SPLIT.split(wordTag);

    String word = splitWordTag[0];
    String tag = splitWordTag[1];

    POS pos;
    if (tag.equalsIgnoreCase("a")) {
      pos = POS.ADJECTIVE;
    } else if (tag.equalsIgnoreCase("r")) {
      pos = POS.ADVERB;
    } else if (tag.equalsIgnoreCase("n")) {
      pos = POS.NOUN;
    } else if (tag.equalsIgnoreCase("v")) {
      pos = POS.VERB;
    } else
      pos = null;

    if (pos != null) {

      WordPOS wordPOS = new WordPOS(word, pos);

      List<Synset> synsets = wordPOS.getSynsets();
      if (synsets != null) {
        String sense = WSDParameters.SenseSource.WORDNET.name();

        for (Word wd : synsets.get(0).getWords()) {
          if (wd.getLemma().equals(word)) {
            try {
              sense = sense + " " + wd.getSenseKey();
              break;
            } catch (JWNLException e) {
              e.printStackTrace();
            }
          }
        }
        return sense;
      } else {
        WSDHelper.print(word + "    " + pos);
        WSDHelper.print("The word has no definitions in WordNet !");
        return null;
      }

    } else {
      WSDHelper.print(word + "    " + pos);
      WSDHelper.print("The word has no definitions in WordNet !");
      return null;
    }

  }
}
