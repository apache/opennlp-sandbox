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

public class Paragraph {

  private final int pnum;
  private final List<Sentence> isentences;

  public Paragraph(int pnum) {
    this(pnum, new ArrayList<>());
  }

  public Paragraph(int pnum, List<Sentence> sentences) {
    this.pnum = pnum;
    this.isentences = sentences;
  }

  public int getPnum() {
    return pnum;
  }

  public List<Sentence> getSentences() {
    return isentences;
  }

  public void addSentence(Sentence isentence) {
    this.isentences.add(isentence);
  }

  @Override
  public String toString() {
    StringBuilder paragraph = new StringBuilder();
    for (Sentence isentence : this.isentences) {
      paragraph.append(" ").append(isentence.toString());
    }
    return paragraph.substring(1, paragraph.length());

  }

  /**
   * @param wordTag A word-tag combination, separated by a {@code .} between both parts.
   * @return {@code true} only and only if the word exists in a
   *         paragraph and is sense-tagged, {@code false} otherwise.
   */
  public boolean contains(String wordTag) {
    if (wordTag == null || wordTag.isBlank()) {
      return false;
    } else {
      final String[] parts = wordTag.split("\\.");
      final String word = parts[0];
      for (Sentence isentence : this.getSentences()) {
        for (Word iword : isentence.getIwords()) {
          if (iword.getWord().equals(word))
            return true;
        }
      }
      return false;
    }
  }

}
