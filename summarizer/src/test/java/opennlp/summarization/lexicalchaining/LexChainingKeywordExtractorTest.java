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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.summarization.Sentence;
import opennlp.summarization.preprocess.DefaultDocProcessor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LexChainingKeywordExtractorTest {

  private static final String ARTICLE =
      "US President Barack Obama has welcomed an agreement between the US and Russia under which Syria's chemical weapons must be destroyed or removed by mid-2014 as an \"important step\"."
          + "But a White House statement cautioned that the US expected Syria to live up to its public commitments. "
          + "The US-Russian framework document stipulates that Syria must provide details of its stockpile within a week. "
          + "If Syria fails to comply, the deal could be enforced by a UN resolution. "
          + "China, France, the UK, the UN and Nato have all expressed satisfaction at the agreement. "
          + "In Beijing, Foreign Minister Wang Yi said on Sunday that China welcomes the general agreement between the US and Russia.";

  private static DefaultDocProcessor dp;
  private static LexicalChainingSummarizer lcs;

  @BeforeAll
  static void initEnv() throws Exception {
    dp = new DefaultDocProcessor(LexChainingKeywordExtractorTest.class.getResourceAsStream("/en-sent.bin"));
    lcs = new LexicalChainingSummarizer(dp, LexChainingKeywordExtractorTest.class.getResourceAsStream("/en-pos-maxent.bin"));
  }

  @Test
  void testGetKeywords() {
    List<Sentence> sent = dp.getSentencesFromStr(ARTICLE);
    List<LexicalChain> vh = lcs.buildLexicalChains(ARTICLE, sent);
    LexChainingKeywordExtractor ke = new LexChainingKeywordExtractor();
    List<String> keywords = ke.getKeywords(vh, 5);
    assertNotNull(keywords);
    assertFalse(keywords.isEmpty());
  }
}
