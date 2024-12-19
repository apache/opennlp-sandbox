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

import java.util.List;
import java.util.regex.Pattern;

/**
 * Describes a context generator for word sense disambiguation.
 */
public interface WSDContextGenerator {

  Pattern PATTERN = Pattern.compile("[^a-z_]");

  /**
   * Computes the context of a word to disambiguate.
   *
   * @param index      The index of the word to disambiguate.
   * @param toks       The tokens of the sentence / context.
   * @param tags       The POS-tags of the sentence / context.
   * @param lemmas     The lemmas of the sentence / context.
   * @param ngram      The ngram to consider for context. Must be greater than {@code 0}.
   * @param windowSize The context window. Must be greater than {@code 0}.
   * @param model      The list of unigrams.
   * @return The context of the word to disambiguate at {@code index} in {@code toks}.
   */
  String[] getContext(int index, String[] toks, String[] tags, String[] lemmas,
                      int ngram, int windowSize, List<String> model);

  /**
   * Computes the context of a word to disambiguate.
   *
   * @param sample     The sample of the word to disambiguate.
   * @param ngram      The ngram to consider for context. Must be greater than {@code 0}.
   * @param windowSize The context window. Must be greater than {@code 0}.
   * @param model      The list of unigrams.
   * @return The context of the word to disambiguate at {@code index} in {@code sample}.
   */
  String[] getContext(WSDSample sample, int ngram, int windowSize, List<String> model);

}
