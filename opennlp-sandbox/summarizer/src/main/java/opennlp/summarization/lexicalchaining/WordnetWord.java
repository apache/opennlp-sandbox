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

import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import edu.mit.jwi.item.IPointer;
import edu.mit.jwi.item.ISenseKey;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWordID;

/**
 * A {@link Word} implementation based on Wordnet concepts.
 */
public class WordnetWord implements Word {

  private String lexicon;
  private IWordID id;
  private ISenseKey wordSense;

  final Hashtable<IPointer, List<ISynsetID>> rels = new Hashtable<>();
  // Cache..
  ISynset synonyms;

  /**
   * Instantiates a {@link WordnetWord} via its lexicon term.
   *
   * @param lexicon Must not be {@code null} and not be an empty string.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public WordnetWord(String lexicon) {
    if (lexicon == null || lexicon.isBlank()) throw new IllegalArgumentException("parameter 'lexicon' must not be null or empty");
    setLexicon(lexicon);
  }

  /**
   * Instantiates a {@link WordnetWord} via its lexicon term and a {@link IWordID}.
   *
   * @param lexicon Must not be {@code null} and not be an empty string.
   * @param id A unique identifier sufficient to retrieve a particular word from the Wordnet database.
   *           Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public WordnetWord(String lexicon, IWordID id) {
    this(lexicon);
    if (id == null) throw new IllegalArgumentException("parameter 'id' must not be null");
    setID(id);
  }

  /**
   * Instantiates a {@link WordnetWord} via its lexicon term and a {@link IWordID}.
   *
   * @param lexicon Must not be {@code null} and not be an empty string.
   * @param wordSense A sense key is a unique string that identifies a Wordnet word.
   *                  Must not be {@code null}.
   * @param id A unique identifier sufficient to retrieve a particular word from the Wordnet database.
   *           Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public WordnetWord(String lexicon, ISenseKey wordSense, IWordID id) {
    this(lexicon, id);
    if (wordSense == null) throw new IllegalArgumentException("parameter 'wordSense' must not be null");
    setSense(wordSense);
  }

  @Override
  public String getLexicon() {
    return lexicon;
  }

  @Override
  public void setLexicon(String lex) {
    this.lexicon = lex;
  }

  @Override
  public Object getSense() {
    return wordSense;
  }

  @Override
  public void setSense(Object senseID) {
    this.wordSense = (ISenseKey) senseID;
  }

  @Override
  public Object getID() {
    return id;
  }

  @Override
  public void setID(Object id) {
    this.id = (IWordID) id;
  }

  @Override
  public String toString() {
    return this.lexicon;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WordnetWord that)) return false;

    return Objects.equals(lexicon, that.lexicon) && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(lexicon);
    result = 31 * result + Objects.hashCode(id);
    return result;
  }
}
