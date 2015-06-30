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
import java.util.Collections;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;

public class WordPOS {

  private String word;
  private List stems;
  private POS pos;

  // Constructor
  public WordPOS(String word, POS pos) throws IllegalArgumentException {
    if (word == null || pos == null) {
      throw new IllegalArgumentException("Args are null");
    }
    this.word = word;
    this.pos = pos;
  }

  public String getWord() {
    return word;
  }

  public POS getPOS() {
    return pos;
  }

  public List getStems() {
    if (stems == null) {
      return PreProcessor.Stem(this);
    } else {
      return stems;
    }
  }

  // Return the synsets (thus the senses) of the current word
  public ArrayList<Synset> getSynsets() {

    IndexWord indexWord;
    try {
      indexWord = Loader.getDictionary().lookupIndexWord(pos, word);
      List<Synset> synsets = indexWord.getSenses();
      return (new ArrayList<Synset>(synsets));
    } catch (JWNLException e) {
      e.printStackTrace();
    }
    return null;
  }

  // uses Stemming to check if two words are equivalent
  public boolean isStemEquivalent(WordPOS wordToCompare) {
    // check if there is intersection in the stems;
    List originalList = this.getStems();
    List listToCompare = wordToCompare.getStems();

    // Constants.print("+++++++++++++++++++++  ::: "+ this.getWord());
    // Constants.print("+++++++++++++++++++++  ::: "+ wordToCompare.getWord());
    // Constants.print("the first list is \n"+originalList.toString());
    // Constants.print("the second list is \n"+listToCompare.toString());

    if (originalList == null || listToCompare == null) { // any of the two
                                                         // requested words do
                                                         // not exist
      return false;
    } else {
      return !Collections.disjoint(originalList, listToCompare);
    }

  }

  // uses Lemma to check if two words are equivalent
  public boolean isLemmaEquivalent(WordPOS wordToCompare) {
    // TODO use lemmatizer to compare with lemmas

    ArrayList<String> lemmas_word = new ArrayList();
    ArrayList<String> lemmas_wordToCompare = new ArrayList();

    for (String pos : Constants.allPOS) {
      Loader.getLemmatizer().lemmatize(wordToCompare.getWord(), pos);
    }
    return false;
  }

}
