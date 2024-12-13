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

package opennlp.tools.disambiguator.datareader;

import java.util.Objects;
import net.sf.extjwnl.data.POS;

import opennlp.tools.disambiguator.WSDHelper;

// TODO extend Word from Wordnet
public class Word {

  public enum Type {
    WORD(1, "word"), PUNCTUATIONMARK(2, "pm");

    public final int code;
    public final String type;

    Type(int code, String type) {
      this.code = code;
      this.type = type;
    }
  }

  private final int pnum;
  private final int snum;
  private final int wnum;

  // Type refers to the type of word in the sentence
  private final Type type;

  private final String word;
  private final POS pos;
  
  private String cmd;
  private String lemma;
  private String wnsn;
  private String lexsn;

  /**
   * Initializes a DISAMBIGUATED {@link Word} instance.
   *
   * @param pnum  The id of the paragraph
   * @param snum  The id of the sentence
   * @param wnum  The id of the word
   * @param type  The type in this case is {@link Type#WORD}
   * @param word  The plain word, as it appears in the sentence
   * @param cmd   Whether it is semantically disambiguated or not
   *              (or to be disambiguated)
   * @param pos   The PoS Tag of the word
   * @param lemma The lemma of the word
   * @param wnsn  The integer sense number corresponding to the WordNet output display
   * @param lexsn The "Sense_key" that indicates the WordNet sense to which word
   *              should be linked
   */
  public Word(int pnum, int snum, int wnum, Type type, String word,
              String cmd, String pos, String lemma, String wnsn, String lexsn) {
    this.pnum = pnum;
    this.snum = snum;
    this.wnum = wnum;
    this.type = type;
    this.word = word;
    if (pos != null) {
      this.pos = WSDHelper.getPOS(pos);
    } else {
      this.pos = null;
    }
    setCmd(cmd);
    setLemma(lemma);
    setWnsn(wnsn);
    setLexsn(lexsn);
  }

  /**
   * Initializes a NON DISAMBIGUATED {@link Word} instance.
   * 
   * @param pnum The id of the paragraph
   * @param snum The id of the sentence
   * @param wnum The id of the word
   * @param type The type in this case is {@link Type#WORD}
   * @param word The raw word, as it appears in the sentence
   * @param cmd Whether it is semantically disambiguated or not
   *            (or to be disambiguated)
   * @param pos The PoS Tag of the word
   */
  public Word(int pnum, int snum, int wnum, Type type,
              String word, String cmd, String pos) {
    this(pnum, snum, wnum, type, word, cmd, pos, null, null, null);
  }

  /**
   * Initializes a punctuation {@link Word} instance.
   *
   * @param pnum The id of the paragraph
   * @param snum The id of the sentence
   * @param wnum The id of the word
   * @param type The type in this case is {@link Type#WORD}
   * @param word The punctuation mark, as it appears in the sentence
   */
  public Word(int pnum, int snum, int wnum, Type type, String word) {
    this(pnum, snum, wnum, type, word, null, null, null, null, null);
  }

  public int getPnum() {
    return pnum;
  }

  public int getSnum() {
    return snum;
  }

  public int getWnum() {
    return wnum;
  }

  public String getWord() {
    return word;
  }

  public Type getType() {
    return type;
  }

  public POS getPos() {
    return pos;
  }

  public String getCmd() {
    return cmd;
  }

  public void setCmd(String cmd) {
    this.cmd = cmd;
  }

  public String getLemma() {
    return lemma;
  }

  public void setLemma(String lemma) {
    this.lemma = lemma;
  }

  public String getWnsn() {
    return wnsn;
  }

  public void setWnsn(String wnsn) {
    this.wnsn = wnsn;
  }

  public String getLexsn() {
    return lexsn;
  }

  public void setLexsn(String lexsn) {
    this.lexsn = lexsn;
  }

  public boolean isInstanceOf(String wordTag) {
    if (this.lemma != null) {
      String[] parts = wordTag.split("\\."); // word.tag
      if (this.lemma.equals(parts[0]) && pos.getKey().equals(parts[1])) {
        if (this.lexsn != null) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean senseEquals(Object oword) {
    if (oword == this)
      return true;
    if (!(oword instanceof Word iword))
      return false;

    if (lemma != null && iword.getLemma() != null &&
            lexsn != null && iword.getLexsn() != null) {
      return lemma.equals(iword.getLemma()) && pos.equals(iword.getPos())
              && lexsn.equals(iword.getLexsn());
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return this.word;
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(word);
    result = 31 * result + Objects.hashCode(pos);
    result = 31 * result + Objects.hashCode(lemma);
    return result;
  }

  @Override
  public boolean equals(Object oword) {

    if (oword == this)
      return true;
    if (!(oword instanceof Word iword))
      return false;

    if (this.lemma != null && iword.getLemma() != null) {
      return lemma.equals(iword.getLemma()) && pos.equals(iword.getPos());
    } else {
      return this.word.equals(iword.getWord()) && pos.equals(iword.pos);
    }
  }

}
