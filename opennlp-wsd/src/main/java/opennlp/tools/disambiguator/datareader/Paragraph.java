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

import java.util.ArrayList;

public class Paragraph {

  protected int pnum;
  protected ArrayList<Sentence> isentences;

  public Paragraph() {
    super();
    this.isentences = new ArrayList<Sentence>();
  }

  public Paragraph(int pnum) {
    super();
    this.pnum = pnum;
    this.isentences = new ArrayList<Sentence>();
  }

  public Paragraph(int pnum, ArrayList<Sentence> sentences) {
    super();
    this.pnum = pnum;
    this.isentences = sentences;
  }

  public int getPnum() {
    return pnum;
  }

  public void setPnum(int pnum) {
    this.pnum = pnum;
  }

  public ArrayList<Sentence> getSsentences() {
    return isentences;
  }

  public void setIsentences(ArrayList<Sentence> isentences) {
    this.isentences = isentences;
  }

  public void addIsentence(Sentence isentence) {
    this.isentences.add(isentence);
  }

  @Override
  public String toString() {
    String paragraph = "";
    for (int i = 0; i < this.isentences.size(); i++) {
      paragraph = paragraph + " " + this.isentences.get(i).toString();
    }
    return paragraph.substring(1, paragraph.length());

  }

  /**
   * This return TRUE only and only if the paragraph contains the word and it is
   * sense-tagged
   * 
   * @param wordTag
   * @return {@value Boolean.true} if the word exists in the paragraph and is
   *         sense-tagged
   * 
   */
  public boolean contains(String wordTag) {

    for (Sentence isentence : this.getSsentences()) {
      for (Word iword : isentence.getIwords()) {
        if (iword.equals(iword))
          return true;
      }
    }

    return false;
  }

}
