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

package opennlp.summarization;

import java.util.List;

import opennlp.tools.stemmer.Stemmer;

/**
 * A document processor abstracts a lot of the underlying complexities of parsing the document and
 * preparing it (e.g. stemming, stop word removal) from the summarization algorithm.
 * <p>
 * The current package supports sentence extraction based algorithms.
 * Thus, extracting sentences from the text is the first step and the basis for related algorithms.
 */
public interface DocProcessor {

  /**
   * Extracts {@link Sentence sentences} from a string representing an article.
   *
   * @param text The text to process; if {@code null} or empty, an empty list is returned.
   *
   * @return The resulting list of detected {@link Sentence sentences}.
   */
  List<Sentence> getSentences(String text);

  /**
   * Extracts words from a specified {@link String sent}.
   *
   * @param sent The sentence to process; if {@code null} or empty, an zero length array is returned.
   *
   * @return An array of tokens (words) contained in the given {@code sent}.
   */
  String[] getWords(String sent);

  /**
   * Provides a stemmer to stem words.
   */
  Stemmer getStemmer();
}
