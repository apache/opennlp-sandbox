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

import java.util.Collections;
import java.util.List;

import opennlp.summarization.Sentence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexChainingKeywordExtractorTest extends AbstractLexicalChainTest {

  private static List<LexicalChain> chains;

  // SUT
  private LexicalChainingKeywordExtractor keywordExtractor;

  @BeforeAll
  static void initEnv() throws Exception {
    AbstractLexicalChainTest.initEnv();
    // Prep
    List<Sentence> sent = dp.getSentences(ARTICLE);
    assertNotNull(sent);
    assertFalse(sent.isEmpty());
    chains = lcs.buildLexicalChains(sent);
    assertNotNull(chains);
    assertFalse(chains.isEmpty());
  }

  @BeforeEach
  public void setUp() {
    keywordExtractor = new LexicalChainingKeywordExtractor();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 5, 42, Integer.MAX_VALUE})
  void testExtractKeywords(int noOfKeywords) {
    List<String> keywords = keywordExtractor.extractKeywords(chains, noOfKeywords);
    assertNotNull(keywords);
    assertFalse(keywords.isEmpty());
  }

  @Test
  void testExtractKeywordsWithEmptyInput() {
    List<String> keywords = keywordExtractor.extractKeywords(Collections.emptyList(), 5);
    assertNotNull(keywords);
    assertTrue(keywords.isEmpty());
  }

  @Test
  void testExtractKeywordsInvalid1() {
    assertThrows(IllegalArgumentException.class, () -> keywordExtractor.extractKeywords(null, 5));
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
  void testExtractKeywordsInvalid2(int noOfKeywords) {
    assertThrows(IllegalArgumentException.class, () -> keywordExtractor.extractKeywords(chains, noOfKeywords));
  }
}
