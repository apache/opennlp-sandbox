/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.summarization;

import java.text.BreakIterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import opennlp.summarization.preprocess.StopWords;
import opennlp.tools.stemmer.PorterStemmer;

/**
 * A representation of a sentence geared toward pagerank and summarization.
 */
public class Sentence {

  private static final String SPACE = " ";
  private final List<Sentence> links = new ArrayList<>();
  private final int sentId;

  // sentId is always position of sentence in doc.
  private String stringVal;
  private Score pageRankScore;
  private int paragraph;
  private int paraPos;
  private boolean hasQuote;
  private double wordWeight = 0;
  private int wordCound = 0;

  /**
   * Instantiates a plain {@link Sentence} via a set of parameters.
   *
   * @param id A numeric identifier with a positive value starting at {@code zero}.
   * @param stringVal The string representation of the sentence.
   * @param paragraph The n-th paragraph number within a document.
   * @param paraPos The index position of the {@code paragraph}.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public Sentence(int id, String stringVal, int paragraph, int paraPos) {
    if (id < 0) throw new IllegalArgumentException("Parameter 'id' cannot be negative");
    if (stringVal == null || stringVal.isBlank())
      throw new IllegalArgumentException("Parameter 'stringVal' must not be null");
    if (paragraph < 0) throw new IllegalArgumentException("Parameter 'paragraph' cannot be negative");
    if (paraPos < 0) throw new IllegalArgumentException("Parameter 'paraPos' cannot be negative");

    this.sentId = id;
    setParagraph(paragraph);
    setStringVal(stringVal);
    setParaPos(paraPos);
  };

  public int getSentId() {
    return sentId;
  }

  public Score getPageRankScore() {
    return pageRankScore;
  }

  public void setPageRankScore(Score pageRankScore) {
    this.pageRankScore = pageRankScore;
  }

  public int getParagraph() {
    return paragraph;
  }

  public void setParagraph(int paragraph) {
    this.paragraph = paragraph;
  }

  public int getParaPos() {
    return paraPos;
  }

  public void setParaPos(int paraPos) {
    this.paraPos = paraPos;
  }

  private int calcWordCount(String stringVal2) {
    int ret = 0;
    StopWords sw = StopWords.getInstance();
    String[] wrds = stringVal.split("\\s+");
    for (String wrd : wrds) {
      if (!sw.isStopWord(wrd) && !wrd.startsWith("'") && !wrd.equals(".") && !wrd.equals("?"))
        ret++;
    }
    return ret;
  }

  public String getStringVal() {
    return stringVal;
  }

  public void setStringVal(String stringVal) {
    this.stringVal = stringVal;
    if (stringVal.contains("\"")) this.hasQuote = true;
    this.wordCound = calcWordCount(stringVal);
  }

  public void addLink(Sentence s) {
    this.links.add(s);
  }

  public List<Sentence> getLinks() {
    return this.links;
  }

  public double getWordWeight() {
    return wordWeight;
  }

  public void setWordWeight(double wordWt) {
    this.wordWeight = wordWt;
  }

  public int getWordCount() {
    return wordCound;
  }

  /**
   * @return Applies stemming to each word and returns a fully-stemmed representation of a sentence.
   */
  public String stem() {
    PorterStemmer stemmer = new PorterStemmer();
    StopWords sw = StopWords.getInstance();

    BreakIterator wrdItr = BreakIterator.getWordInstance(Locale.US);
    int wrdStrt = 0;
    StringBuilder b = new StringBuilder();
    wrdItr.setText(stringVal);
    for (int wrdEnd = wrdItr.next(); wrdEnd != BreakIterator.DONE;
         wrdStrt = wrdEnd, wrdEnd = wrdItr.next()) {
      String word = this.getStringVal().substring(wrdStrt, wrdEnd);//words[i].trim();
      word = word.replace("\"|'", "");

      // Skip stop words and stem the word.
      if (sw.isStopWord(word)) continue;

      stemmer.stem(word);
      b.append(stemmer.toString());
      b.append(SPACE);
    }
    return b.toString();
  }

  // Should add an article id to the sentence class. For now returns true if the ids are the same.
  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Sentence sentence)) return false;

    return sentId == sentence.sentId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sentId);
  }

  @Override
  public String toString() {
    return this.stringVal; // + "("+ this.paragraph +", "+this.paraPos+")";
  }
}
