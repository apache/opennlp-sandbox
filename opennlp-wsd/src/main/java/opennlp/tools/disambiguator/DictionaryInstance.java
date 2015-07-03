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

package opennlp.tools.disambiguator;

/**
 * An instance of the dictionary. A dictionary instance has:
 * <ul>
 * <li>index: an index for the current instance of the dictionary</li>
 * <li>word: the word to disambiguate</li>
 * <li>id: its id in the source (e.g., in WordNet, Wordsmyth, etc.)</li>
 * <li>source: the source of the instance (e.g., WordNet, Wordsmyth, etc.)</li>
 * <li>synset: the list of synonyms (i.e., the words that share the same current
 * meaning)</li>
 * <li>gloss: the sense of the word</li>
 * </ul>
 */
public class DictionaryInstance {

  protected int index;

  protected String word;

  protected String id;
  protected String source;
  protected String[] synset;
  protected String gloss;

  /**
   * Constructor
   */
  public DictionaryInstance(int index, String word, String id, String source,
      String[] synset, String gloss) {
    super();
    this.index = index;
    this.word = word;
    this.id = id;
    this.source = source;
    this.synset = synset;
    this.gloss = gloss;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String[] getSynset() {
    return synset;
  }

  public void setSynset(String[] synset) {
    this.synset = synset;
  }

  public String getGloss() {
    return gloss;
  }

  public void setGloss(String gloss) {
    this.gloss = gloss;
  }

}
