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

import java.util.List;

/**
 * A basic POS tagger which describes functionality to tag text and
 * filter tokens for certain word classes.
 */
public interface POSTagger {

  //Tagger types..
  int NOUN = 0;
  int VERB = 1;
  int ADJECTIVE = 2;
  int ADVERB = 3;
  int PRONOUN = 4;

  /**
   * Tags a given {@code input} text so that word classes are appended to each token.
   *
   * @param input The text to process. Must not be {@code null}. If empty, an empty String is returned.
   * @return The POS tagged text. May be empty.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  String getTaggedString(String input);

  /**
   * Extracts words from POS-tagged {@code tokens} which equal a certain word class ({@code type}).
   *
   * @param tokens An array of words to filter for its word class ({@code type}). Must not be {@code null}.
   *               Must be in a tagged form, that is, separated into {@code token/word-class} pairs.
   * @param type One of the supported types: {@link #NOUN}, {@link #VERB}, {@link #ADJECTIVE},
   *             {@link #ADVERB}, or {@link #PRONOUN}. Must not be less than {@code zero}
   *             and not be more than {@link #PRONOUN}.
   * @return A list of words that match the given {@code type}. May be empty, yet guaranteed to be non-{@code null}.
   *
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  List<String> getWordsOfType(String[] tokens, int type);
}
