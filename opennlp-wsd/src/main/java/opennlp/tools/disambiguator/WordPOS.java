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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO extend Word instead
public class WordPOS {

  private static final Logger LOG = LoggerFactory.getLogger(WordPOS.class);

  private final String word;
  private final POS pos;
  private transient List<String> stems;
  private transient List<String> stemsLowerCased;

  /**
   * Instantiates a {@link WordPOS} via a {@code word} and related {@code tag}.
   *
   * @param word The token to use. It must not be {@code null}.
   * @param tag The POS tag to use. It must not be {@code null}.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public WordPOS(String word, String tag) {
    this(word, WSDHelper.getPOS(tag));
  }

  /**
   * Instantiates a {@link WordPOS} via a {@code word} and related {@code tag}.
   *
   * @param word The token to use. It must not be {@code null} and not be empty.
   * @param pos The {@link POS pos tag} to use. It must not be {@code null}.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public WordPOS(String word, POS pos) {
    if (word == null || word.isBlank() || pos == null) {
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

  /**
   * @return Retrieves the stems of the associated {@code word}.
   */
  public List<String> getStems() {
    if (stems == null) {
      stems = WSDHelper.stem(this);
      if (stems != null) {
        stemsLowerCased = new ArrayList<>(stems);
        stemsLowerCased.replaceAll(String::toLowerCase);
      }
    }
    return stems;
  }

  /**
   * @return Retrieves the {@link Synset synsets}, aka the senses, of the associated {@code word}.
   * The result might be null or empty.
   */
  public List<Synset> getSynsets() {

    IndexWord indexWord;
    try {
      indexWord = WSDHelper.getDictionary().lookupIndexWord(pos, word);
      if (indexWord == null) {
        LOG.debug("NULL synset probably a POS tagger mistake ! :: [POS] : {} [word] : {}", pos.getLabel(), word);
        return null;
      }
      return indexWord.getSenses();
    } catch (JWNLException e) {
      LOG.error(e.getMessage(), e);
    }
    return Collections.emptyList();
  }

  // uses Stemming to check if two words are equivalent
  public boolean isStemEquivalent(WordPOS wordToCompare) {
    // check if there is intersection in the stems;
    List<String> listToCompare = wordToCompare.getStems();

    if (this.getStems() == null || listToCompare == null) {
      return false;
    } else {
      //listToCompare.replaceAll(String::toLowerCase);
      return !Collections.disjoint(stemsLowerCased, listToCompare);
    }
  }

}
