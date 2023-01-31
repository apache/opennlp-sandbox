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
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import opennlp.tools.disambiguator.WSDHelper;
import opennlp.tools.disambiguator.WSDParameters;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.WordPOS;

/**
 * Implementation of the <b>Most Frequent Sense</b> baseline approach. This
 * approach returns the senses in order of frequency in WordNet. The first sense
 * is the most frequent.
 */
public class MFS extends WSDisambiguator {

  public MFS() {
    super();
  }

  /*
   * @return the most frequent senses from wordnet
   */
  public static String getMostFrequentSense(WSDSample sample) {

    List<Synset> synsets = sample.getSynsets();
    for (Word wd : synsets.get(0).getWords()) {
      if (wd.getLemma()
          .equalsIgnoreCase((sample.getLemmas()[sample.getTargetPosition()]))) {
        try {
          return WSDParameters.SenseSource.WORDNET.name() + " "
              + wd.getSenseKey();
        } catch (JWNLException e) {
          e.printStackTrace();
        }
      }
    }
    return "nonesense";

  }

  public static String[] getMostFrequentSenses(WSDSample sample) {

    List<Synset> synsets = sample.getSynsets();
    String[] senseKeys = new String[synsets.size()];

    for (int i = 0; i < synsets.size(); i++) {
      for (Word wd : synsets.get(i).getWords()) {
        if (wd.getLemma().equalsIgnoreCase(
            (sample.getLemmas()[sample.getTargetPosition()]))) {
          try {
            senseKeys[i] = WSDParameters.SenseSource.WORDNET.name() + " "
                + wd.getSenseKey();
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

    String word = wordTag.split("\\.")[0];
    String tag = wordTag.split("\\.")[1];

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

      ArrayList<Synset> synsets = wordPOS.getSynsets();

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

  }
}
