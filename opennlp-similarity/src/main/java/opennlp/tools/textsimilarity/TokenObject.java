/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.textsimilarity;

import java.util.Comparator;

public class TokenObject {
  private String token;

  private String stem;

  private int docFreq;

  private int freq;

  private float tfidf;

  boolean stopWord;

  public TokenObject() {
  }

  public TokenObject(String token, String stem, int freq, int docFreq,
      float tfidf) {
    this.token = token;
    this.stem = stem;
    this.freq = freq;
    this.docFreq = docFreq;
    this.tfidf = tfidf;
    this.stopWord = false;
  }

  public TokenObject(String token, String stem) {
    this.token = token;
    this.stem = stem;
    this.freq = 0;
    this.docFreq = 0;
    this.tfidf = 0;
  }

  public boolean isStopWord() {
    return this.stopWord;
  }

  public void setIsStopWord(boolean val) {
    this.stopWord = val;
  }

  public String getToken() {
    return this.token;
  }

  public String getStem() {
    return this.stem;
  }

  public int getDocFreq() {
    return this.docFreq;
  }

  public int getFreq() {
    return this.freq;
  }

  public float getTFIDF() {
    return this.tfidf;
  }

  public void setDocFreq(int val) {
    this.docFreq = val;
  }

  public void setFreq(int val) {
    this.freq = val;
  }

  public void setTFIDF(float val) {
    this.tfidf = val;
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof TokenObject && ((TokenObject) obj).getStem().equals(
        getStem()));
  }

  public static class sortByDocFreq implements Comparator<TokenObject> {
    public int compare(TokenObject a, TokenObject b) {

      if (a.docFreq > b.docFreq) {
        return 1;
      } else {
        return -1;
      }
    }
  }

  public static class sortByTFIDF implements Comparator<TokenObject> {
    public int compare(TokenObject a, TokenObject b) {

      if (a.tfidf < b.tfidf) {
        return 1;
      } else {
        return -1;
      }
    }
  }
}
