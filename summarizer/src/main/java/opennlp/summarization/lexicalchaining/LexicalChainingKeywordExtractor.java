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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Uses the {@link LexicalChain lexical chaining} algorithm to extract keywords.
 *
 * @see LexicalChain
 */
public class LexicalChainingKeywordExtractor {

  /**
   * Extracts keywords from a list of {@link LexicalChain lexical chains}, limited by {@code noOfKeywords}.
   *
   * @param lexicalChains The {@link LexicalChain lexical chains} to process. Must not be {@code null}.
   * @param noOfKeywords The upper limit of keywords. Must be greater than {@code zero}.
   *
   * @return The extracted keywords as a list. Guaranteed to be not {@code null}.
   *
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   * @implNote This operation is based on longest lexical chains.
   */
  public List<String> extractKeywords(List<LexicalChain> lexicalChains, int noOfKeywords) {
    if (lexicalChains == null) {
      throw new IllegalArgumentException("Parameter 'lexicalChains' must not be null.");
    }
    if (noOfKeywords <= 0) {
      throw new IllegalArgumentException("Parameter 'noOfKeywords' must be greater than 0.");
    }
    if (lexicalChains.isEmpty()) {
      return Collections.emptyList();
    } else {
      Collections.sort(lexicalChains);
      List<String> ret = new ArrayList<>();
      for (int i = 0; i < Math.min(lexicalChains.size(), noOfKeywords); i++) {
        List<Word> words = lexicalChains.get(i).getWords();
        if (!words.isEmpty()) {
          Word w = words.get(0);
          if (!ret.contains(w.getLexicon())) {
            ret.add(w.getLexicon());
          }
        }
      }
      return ret;
    }
  }
}
