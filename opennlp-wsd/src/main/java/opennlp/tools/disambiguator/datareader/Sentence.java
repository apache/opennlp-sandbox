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
import java.util.List;

public class Sentence {

  private final int pnum;
  private final int snum;
  private List<Word> iwords;

  public Sentence(int pnum, int snum) {
    this(pnum, snum, new ArrayList<>());
  }

  public Sentence(int pnum, int snum, List<Word> iwords) {
    super();
    this.pnum = pnum;
    this.snum = snum;
    this.iwords = iwords;
  }

  public int getPnum() {
    return pnum;
  }

  public int getSnum() {
    return snum;
  }

  public List<Word> getIwords() {
    return iwords;
  }

  public void addWord(Word iword) {
    this.iwords.add(iword);
  }

  @Override
  public String toString() {
    StringBuilder sentence = new StringBuilder();
    for (Word iword : this.iwords) {
      sentence.append(" ").append(iword.toString());
    }
    return sentence.substring(1, sentence.length());
  }

}
