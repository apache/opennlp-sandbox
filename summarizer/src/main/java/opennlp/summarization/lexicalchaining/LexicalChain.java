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
import java.util.Objects;

import opennlp.summarization.Sentence;

public class LexicalChain implements Comparable<LexicalChain>{
  final List<Word> word;
  final List<Sentence> sentences;

  int start, last;
  int score;
  int occurrences = 1;

  public LexicalChain() {
    word = new ArrayList<>();
    sentences = new ArrayList<>();
  }

  public double score()
  {
    return length() ; //* homogeneity();
  }

  public int length(){
    return word.size();
  }

  public float homogeneity()
  {
    return (1.0f - (float) occurrences /(float)length());
  }

  public void addWord(Word w)
  {
    word.add(w);
  }

  public void addSentence(Sentence sent)
  {
    if(!sentences.contains(sent))
      sentences.add(sent);
  }

  public List<Word> getWord()
  {
    return word;
  }

  public List<Sentence>getSentences()
  {
    return this.sentences;
  }

  @Override
  public int compareTo(LexicalChain o) {
    double diff = (score() - o.score());
    return diff == 0 ? 0: diff > 0 ? 1:-1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LexicalChain that = (LexicalChain) o;
    return start == that.start && last == that.last && score == that.score && occurrences == that.occurrences;
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, last, score, occurrences);
  }
}
