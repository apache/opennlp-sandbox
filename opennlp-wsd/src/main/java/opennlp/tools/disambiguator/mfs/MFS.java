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

package opennlp.tools.disambiguator.mfs;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import opennlp.tools.disambiguator.Constants;
import opennlp.tools.disambiguator.WSDParameters;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.WordPOS;
import opennlp.tools.disambiguator.WordToDisambiguate;
import opennlp.tools.util.Span;

/**
 * Implementation of the <b>Most Frequent Sense</b> baseline approach. This
 * approach returns the first sense retreived in WordNet which is supposed to be
 * the most frequent sense:
 * <ul>
 * <li>PoS-tags of the surrounding words</li>
 * <li>Local collocations</li>
 * <li>Surrounding words</li>
 * </ul>
 * check {@link https://www.comp.nus.edu.sg/~nght/pubs/ims.pdf} for details
 * about this approach
 */
public class MFS implements WSDisambiguator {

  public MFS(WSDParameters parameters) {
    super();
    this.parameters = parameters;
  }

  public MFS() {
    super();
    this.parameters = new MFSParameters();
  }

  public WSDParameters parameters;

  private String[] getMostFrequentSense(WordToDisambiguate wordToDisambiguate) {

    String word = wordToDisambiguate.getRawWord().toLowerCase();
    POS pos = Constants.getPOS(wordToDisambiguate.getPosTag());

    if (pos != null) {

      WordPOS wordPOS = new WordPOS(word, pos);

      ArrayList<Synset> synsets = wordPOS.getSynsets();

      int size = synsets.size();

      String[] senses = new String[size];

      for (int i = 0; i < size; i++) {
        String senseKey = null;
        for (Word wd : synsets.get(i).getWords()) {
          if (wd.getLemma().equals(
              wordToDisambiguate.getRawWord().split("\\.")[0])) {
            try {
              senseKey = wd.getSenseKey();
            } catch (JWNLException e) {
              e.printStackTrace();
            }
            senses[i] = "WordNet " + senseKey;
            break;
          }
        }

      }
      return senses;
    } else {
      System.out.println("The word has no definitions in WordNet !");
      return null;
    }

  }

  /**
   * This method returns the most frequent sense out of a wordTag. It serves for
   * quick check of the most frequent sense without any need to create a
   * {@link WordToDisambiguate} instance
   * 
   * @param wordTag
   *          the word to disambiguate. It should be written in the format
   *          "word.p" (Exp: "write.v", "well.r", "smart.a", "go.v"
   * @return The most frequent sense if it exists in WordNet, null} otherwise
   */
  public String[] getMostFrequentSense(String wordTag) {

    String word = wordTag.split("\\.")[0];
    String tag = wordTag.split("\\.")[1];

    POS pos;

    if (tag.equalsIgnoreCase("a")) {
      pos = POS.ADJECTIVE;
    } else if (tag.equalsIgnoreCase("r")) {
      pos = POS.ADVERB;
    } else if (tag.equalsIgnoreCase("n")) {
      pos = POS.NOUN;
    } else if (tag.equalsIgnoreCase("a")) {
      pos = POS.VERB;
    } else
      pos = null;

    if (pos != null) {

      WordPOS wordPOS = new WordPOS(word, pos);

      ArrayList<Synset> synsets = wordPOS.getSynsets();

      int size = synsets.size();

      String[] senses = new String[size];

      for (int i = 0; i < size; i++) {
        String senseKey = null;
        for (Word wd : synsets.get(i).getWords()) {
          if (wd.getLemma().equals(word)) {
            try {
              senseKey = wd.getSenseKey();
            } catch (JWNLException e) {
              e.printStackTrace();
            }
            senses[i] = senseKey;
            break;
          }
        }

      }
      return senses;
    } else {
      System.out.println("The word has no definitions in WordNet !");
      return null;
    }

  }

  @Override
  public WSDParameters getParams() {
    return this.parameters;
  }

  @Override
  public void setParams(WSDParameters params) throws InvalidParameterException {
    this.parameters = params;

  }

  @Override
  public String[] disambiguate(String[] tokenizedContext,
      int ambiguousTokenIndex) {
    // System.out.println(tokenizedContext[ambiguousTokenIndex]);
    WordToDisambiguate wtd = new WordToDisambiguate(tokenizedContext,
        ambiguousTokenIndex);
    // System.out.println(wtd.getPosTags()[ambiguousTokenIndex]);
    return getMostFrequentSense(wtd);
  }

  @Override
  public String[][] disambiguate(String[] tokenizedContext,
      Span[] ambiguousTokenIndexSpans) {

    // TODO Auto-generated method stub
    return null;
  }

}
