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
import java.util.ListIterator;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;

// TODO extend Word instead
public class WordPOS {

  private String word;
  private List<String> stems;
  private POS pos;
  private String posTag;
  public boolean isTarget = false;

  public WordPOS(String word, String tag) throws IllegalArgumentException {
    if (word == null || tag == null) {
      throw new IllegalArgumentException("Args are null");
    }
    this.word = word;
    this.posTag = tag;
    this.pos = WSDHelper.getPOS(tag);
  }

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

  public String getPosTag() {
    return posTag;
  }

  public List<String> getStems() {
    if (stems == null) {
      return WSDHelper.Stem(this);
    } else {
      return stems;
    }
  }

  // Return the synsets (thus the senses) of the current word
  public ArrayList<Synset> getSynsets() {

    IndexWord indexWord;
    try {
      indexWord = WSDHelper.getDictionary().lookupIndexWord(pos, word);
      if (indexWord == null) {
        WSDHelper
            .print("NULL synset probably a POS tagger mistake ! :: [POS] : "
                + pos.getLabel() + " [word] : " + word);
        return null;
      }
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
    List<String> originalList = this.getStems();
    List<String> listToCompare = wordToCompare.getStems();

    if (originalList == null || listToCompare == null) {
      return false;
    } else {
      ListIterator<String> iterator = originalList.listIterator();
      while (iterator.hasNext()) {
        iterator.set(iterator.next().toLowerCase());
      }
      iterator = listToCompare.listIterator();
      while (iterator.hasNext()) {
        iterator.set(iterator.next().toLowerCase());
      }
      return !Collections.disjoint(originalList, listToCompare);
    }

  }

}
