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

import opennlp.summarization.Sentence;
import opennlp.summarization.preprocess.DefaultDocProcessor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LexChainTest {

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
    dp = new DefaultDocProcessor(LexChainTest.class.getResourceAsStream("/en-sent.bin"));
    lcs = new LexicalChainingSummarizer(dp, LexChainTest.class.getResourceAsStream("/en-pos-maxent.bin"));
  }

  @Test
  void testBuildLexicalChains() {
    List<Sentence> sent = dp.getSentencesFromStr(ARTICLE);
    assertNotNull(sent);
    List<LexicalChain> vh = lcs.buildLexicalChains(ARTICLE, sent);
    assertNotNull(vh);
    Collections.sort(vh);
    assertTrue(!vh.isEmpty());

    List<Sentence> s = dp.getSentencesFromStr(ARTICLE);
    Hashtable<String, Boolean> comp = new Hashtable<>();

    for (int i = vh.size() - 1; i >= Math.max(vh.size() - 50, 0); i--) {
      LexicalChain lc = vh.get(i);

      if (!(comp.containsKey(lc.getWord().get(0).getLexicon()))) {
        comp.put(lc.getWord().get(0).getLexicon(), Boolean.TRUE);
        /*
        for(int j=0;j<lc.getWord().size();j++)
          System.out.print(lc.getWord().get(j) + " -- ");
        */

        assertEquals(1.0d, lc.score());
        /*
        for(Sentence sid : lc.getSentences()) {
          //if(sid>=0 && sid<s.size())
          System.out.println(sid);
        }
        */
      }
    }

  }

  @Test
  void testGetRelation() {
    try {
      WordRelationshipDetermination lcs = new WordRelationshipDetermination();
      LexicalChain l = new LexicalChain();
      List<Word> words = lcs.getWordSenses("music");

      l.addWord(words.get(0));
      // int rel = lcs.getRelation(l, "nation");
      WordRelation rel2 = lcs.getRelation(l, "tune", true);
      WordRelation rel3 = lcs.getRelation(l, "vocal", true);
      assertEquals(1, rel2.relation());
      assertEquals(1, rel3.relation());
      // assertEquals(rel, LexicalChainingSummarizer.STRONG_RELATION);
      assertEquals(WordRelation.MED_RELATION, rel2.relation());
      assertEquals(WordRelation.MED_RELATION, rel3.relation());
    } catch (Exception e) {
      fail(e.getLocalizedMessage());
    }
  }

}
