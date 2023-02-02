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

import opennlp.tools.disambiguator.WSDHelper;

// TODO extend Word from Wordnet
public class Word {

  public static enum Type {
    WORD(1, "word"), PUNCTUATIONMARK(2, "pm");

    public int code;
    public String type;

    private Type(int code, String type) {
      this.code = code;
      this.type = type;
    }
  }

  protected int pnum;
  protected int snum;
  protected int wnum;

  // Type refers to the type of word in the sentence
  protected Type type;

  protected String word;
  protected String cmd;
  protected String pos;
  protected String lemma;
  protected String wnsn;
  protected String lexsn;

  public Word() {
    super();
  }

  public Word(String lemma, String pos) {
    super();
    this.word = lemma;
    this.lemma = lemma;
    this.pos = pos;
  }

  /**
   * This serves to create a DISAMBIGUATED word instance
   * 
   * @param pnum
   *          id of the paragraph
   * @param snum
   *          id of the sentence
   * @param wnum
   *          id of the word in the sentence
   * @param type
   *          the type in this case is {@link Type.DWORD}
   * @param word
   *          The raw word, as it appears in the sentence
   * @param cmd
   *          Whether it is semantically disambiguated or not (or to be
   *          disambiguated)
   * @param pos
   *          The PoS Tag of the word
   * @param lemma
   *          The lemma of the word
   * @param wnsn
   *          The integer sense number corresponding to the WordNet output
   *          display
   * @param lexsn
   *          The "Sense_key" that indicates the WordNet sense to which word
   *          should be linked
   * 
   */
  public Word(int pnum, int snum, int wnum, Type type, String word,
      String cmd, String pos, String lemma, String wnsn, String lexsn) {
    super();
    this.pnum = pnum;
    this.snum = snum;
    this.wnum = wnum;
    this.type = type;
    this.word = word;
    this.cmd = cmd;
    this.pos = pos;
    this.lemma = lemma;
    this.wnsn = wnsn;
    this.lexsn = lexsn;
  }

  /**
   * This serves to create a NON DISAMBIGUATED word instance
   * 
   * @param pnum
   *          id of the paragraph
   * @param snum
   *          id of the sentence
   * @param type
   *          the type in this case is {@link Type.DWORD}
   * @param word
   *          The raw word, as it appears in the sentence
   * @param cmd
   *          Whether it is semantically disambiguated or not (or to be
   *          disambiguated)
   * @param pos
   *          The PoS Tag of the word
   * 
   */
  public Word(int pnum, int snum, int wnum, Type type, String word,
      String cmd, String pos) {
    super();
    this.wnum = wnum;
    this.pnum = pnum;
    this.snum = snum;
    this.type = type;
    this.word = word;
    this.cmd = cmd;
    this.pos = pos;
  }

  /**
   * This serves to create a punctuation instances
   * 
   * @param type
   *          The type as in {@link Type}
   * @param word
   *          The punctuation mark, as it appears in the sentence
   */
  public Word(int pnum, int snum, int wnum, Type type, String word) {
    super();
    this.pnum = pnum;
    this.snum = snum;
    this.type = type;
    this.word = word;
  }

  public int getPnum() {
    return pnum;
  }

  public void setPnum(int pnum) {
    this.pnum = pnum;
  }

  public int getSnum() {
    return snum;
  }

  public void setSnum(int snum) {
    this.snum = snum;
  }

  public int getWnum() {
    return wnum;
  }

  public void setWnum(int wnum) {
    this.wnum = wnum;
  }

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getCmd() {
    return cmd;
  }

  public void setCmd(String cmd) {
    this.cmd = cmd;
  }

  public String getPos() {
    return pos;
  }

  public void setPos(String pos) {
    this.pos = pos;
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

  @Override
  public String toString() {
    return this.word;
  }

  public boolean equals(Object oword) {

    if (!(oword instanceof Word))
      return false;
    if (oword == this)
      return true;

    Word iword = (Word) oword;

    if (this.lemma != null && iword.getLemma() != null) {
      if (iword.getLemma().equals(this.getLemma())
          && WSDHelper.getPOS(iword.getPos()).equals(
              WSDHelper.getPOS(this.getPos()))) {
        return true;
      }
    } else {
      if (this.word.equals(iword.getWord())
          && WSDHelper.getPOSabbreviation(this.getPos()).equals(
              WSDHelper.getPOSabbreviation(iword.getPos()))) {
        return true;
      }
    }
    return false;
  }

  public boolean isInstanceOf(String wordTag) {

    String tag = WSDHelper.getPOSabbreviation(this.getPos());

    String oword = wordTag.split("\\.")[0];
    String otag = wordTag.split("\\.")[1];

    if (this.lemma != null) {
      if (this.lemma.equals(oword) && tag.equals(otag)) {
        if (this.lexsn != null) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean senseEquals(Object oword) {

    if (!(oword instanceof Word))
      return false;
    if (oword == this)
      return true;

    Word iword = (Word) oword;

    if (iword.getLemma().equals(this.getLemma())
        && WSDHelper.getPOS(iword.getPos()).equals(
            WSDHelper.getPOS(this.getPos()))
        && iword.getLexsn().equals(this.getLexsn())) {
      return true;
    }

    return false;
  }

}
