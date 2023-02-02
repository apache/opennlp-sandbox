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

public class Sentence {

  protected int pnum;
  protected int snum;
  protected ArrayList<Word> iwords;

  public Sentence() {
    super();
    this.iwords = new ArrayList<Word>();
  }

  public Sentence(int pnum, int snum) {
    super();
    this.pnum = pnum;
    this.snum = snum;
    this.iwords = new ArrayList<Word>();
  }

  public Sentence(int pnum, int snum, ArrayList<Word> iwords) {
    super();
    this.pnum = pnum;
    this.snum = snum;
    this.iwords = iwords;
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

  public ArrayList<Word> getIwords() {
    return iwords;
  }

  public void setIwords(ArrayList<Word> iwords) {
    this.iwords = iwords;
  }

  public void addIword(Word iword) {
    this.iwords.add(iword);
  }

  @Override
  public String toString() {
    String sentence = "";
    for (int i = 0; i < this.iwords.size(); i++) {
      sentence = sentence + " " + this.iwords.get(i).toString();
    }
    return sentence.substring(1, sentence.length());

  }

}
