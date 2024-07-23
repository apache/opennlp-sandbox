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

package opennlp.summarization.lexicalchaining;

import java.util.ArrayList;
import java.util.List;

import opennlp.summarization.Sentence;

/**
 * Represents a lexical chain.
 */
public class LexicalChain implements Comparable<LexicalChain> {

  private final List<Word> words = new ArrayList<>();
  private final List<Sentence> sentences = new ArrayList<>();
  private int score;

  int start;
  int last;
  int occurrences = 1;

  public LexicalChain() {
  }

  public LexicalChain(int start) {
    this.start = start;
  }

  public double score() {
    return length(); //* homogeneity();
  }

  public int length() {
    return words.size();
  }

  public float homogeneity() {
    return (1.0f - (float) occurrences / (float) length());
  }

  public void addWord(Word w) {
    words.add(w);
  }

  public void addSentence(Sentence sent) {
    if (!sentences.contains(sent))
      sentences.add(sent);
  }

  public List<Word> getWords() {
    return words;
  }

  public List<Sentence> getSentences() {
    return this.sentences;
  }

  @Override
  public int compareTo(LexicalChain o) {
    double diff = (score() - o.score());
    return diff == 0 ? 0 : diff > 0 ? 1 : -1;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LexicalChain that)) return false;

    return start == that.start && last == that.last && score == that.score && occurrences == that.occurrences;
  }

  @Override
  public int hashCode() {
    int result = start;
    result = 31 * result + last;
    result = 31 * result + score;
    result = 31 * result + occurrences;
    return result;
  }
}
