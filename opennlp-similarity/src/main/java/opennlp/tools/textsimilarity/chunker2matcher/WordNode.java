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

package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.ArrayList;
import java.util.List;

public class WordNode extends SyntacticTreeNode {
  // the word in the sentence
  private String word;
  private String lemma;

  public WordNode(String type, String word) {
    super(type);

    setWord(word);
  }

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;

    // update lemma accordingly
    this.lemma = null;
    /*
     * WordDictionary.getInstance().getLemmaOrWord(word, getType());
     */
  }

  @Override
  public String getLemma(boolean removeStopWord) {
    if (removeStopWord) // && Feature.isStopWord(lemma, getType()))
      return null;

    return lemma;
  }

  @Override
  public List<SyntacticTreeNode> getChildren() {
    // a word node is a leaf and has no children
    return null;
  }

  @Override
  public String getText() {
    return word;
  }

  @Override
  public String toStringIndented(int numTabs) {
    String indent = SyntacticTreeNode.getIndent(numTabs);
    StringBuilder builder = new StringBuilder();
    builder.append(indent).append("type = ").append(getType())
        .append(", word = ").append(word);

    return builder.toString();
  }

  public static void main(String[] args) {
  }

  @Override
  public List<String> getOrderedPOSList() {
    List<String> types = new ArrayList<String>();
    types.add(getType());
    return types;
  }

  @Override
  public List<String> getOrderedLemmaList() {
    List<String> types = new ArrayList<String>();
    types.add(this.getWord());
    return types;
  }
}
