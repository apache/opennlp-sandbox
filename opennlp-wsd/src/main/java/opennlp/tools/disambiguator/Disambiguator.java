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

import opennlp.tools.util.Span;

import java.util.List;

/**
 * Describes a word sense disambiguator that determines which sense of a word is
 * meant in a particular context.
 * It is a classification task, where the classes are the different senses of
 * the ambiguous word. Disambiguation can be achieved in either supervised or
 * un-supervised approaches. A {@link Disambiguator} returns a sense ID.
 * <p>
 * <b>How it works:</b><br/>
 * Just supply the {@code context} as an array of tokens and the index of the
 * {@code target word} to the disambiguate method.
 * <p>
 * Otherwise, for multiple words, you can set a word {@link Span} instead of
 * a single target index.
 */
public interface Disambiguator {

  /**
   * Conducts disambiguation for a {@link WSDSample} context.
   *
   * @param sample    The {@link WSDSample} containing the word and POS tags to use.
   * @return The sense of the {@code sample} to disambiguate.
   */
  String disambiguate(WSDSample sample);
  
  /**
   * Conducts disambiguation for a single word located at {@code ambiguousTokenIndex}.
   *
   * @param tokenizedContext    The text containing the word to disambiguate.
   * @param tokenTags           The tags corresponding to the context.
   * @param lemmas              The lemmas of ALL the words in the context.
   * @param ambiguousTokenIndex The index of the word to disambiguate.
   *                            Must not be less or equal to zero.
   * @return The sense of the word to disambiguate.
   */
  String disambiguate(String[] tokenizedContext, String[] tokenTags,
                      String[] lemmas, int ambiguousTokenIndex);

  /**
   * Conducts disambiguation for all word located at {@code ambiguousTokenSpan}.
   *
   * @param tokenizedContext    The text containing the word to disambiguate.
   * @param tokenTags           The tags corresponding to the context.
   * @param lemmas              The lemmas of ALL the words in the context.
   * @param ambiguousTokenSpan  The {@link Span} of the word(s) to disambiguate.
   *                            Must not be {@code null}.
   * @return A List of senses, each corresponding to the senses of each word of
   *         the context which are to be disambiguated.
   */
  List<String> disambiguate(String[] tokenizedContext, String[] tokenTags,
                            String[] lemmas, Span ambiguousTokenSpan);

  /**
   * Conducts disambiguation for all the words of the {@code tokenizedContext}.
   *
   * @param tokenizedContext    The text containing the word to disambiguate.
   * @param tokenTags           The tags corresponding to the context.
   * @param lemmas              The lemmas of ALL the words in the context.
   * @return A List of senses, each corresponding to the senses of each word of
   *         the context which are to be disambiguated.
   */
  List<String> disambiguate(String[] tokenizedContext, String[] tokenTags,
                            String[] lemmas);

}
