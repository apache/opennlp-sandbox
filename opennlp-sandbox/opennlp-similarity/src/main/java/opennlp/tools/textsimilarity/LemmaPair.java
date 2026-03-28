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

/**
 * 
 * @author Albert-Jan de Vries
 * 
 */
public class LemmaPair {
  private String POS;

  private String lemma;

  private int startPos;

  private int endPos;

  public LemmaPair(String POS, String lemma, int startPos) {

    this.POS = POS;
    this.lemma = lemma;
    this.startPos = startPos;
  }

  public LemmaPair(String POS, String lemma) {
    this.POS = POS;
    this.lemma = lemma;
  }

  public String getPOS() {
    return POS;
  }

  public void setPOS(String pOS) {
    POS = pOS;
  }

  public String getLemma() {
    return lemma;
  }

  public void setLemma(String lemma) {
    this.lemma = lemma;
  }

  public int getStartPos() {
    return startPos;
  }

  public void setStartPos(int startPos) {
    this.startPos = startPos;
  }

  public int getEndPos() {
    return endPos;
  }

  public void setEndPos(int endPos) {
    this.endPos = endPos;
  }

  public String toString() {
    return this.getStartPos() + "(" + POS + "-" + lemma + ")";
  }
}
