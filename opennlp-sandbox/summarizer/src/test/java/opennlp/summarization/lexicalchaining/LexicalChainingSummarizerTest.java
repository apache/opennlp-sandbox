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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.summarization.Sentence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LexicalChainingSummarizerTest extends AbstractLexicalChainTest {

  private List<Sentence> sent;

  @BeforeEach
  void setUp() {
    sent = dp.getSentences(ARTICLE);
    assertNotNull(sent);
  }

  @Test
  void testBuildLexicalChains() {
    List<LexicalChain> vh = lcs.buildLexicalChains(sent);
    assertNotNull(vh);
    Collections.sort(vh);
    assertFalse(vh.isEmpty());

    Map<String, Boolean> comp = new Hashtable<>();

    for (int i = vh.size() - 1; i >= Math.max(vh.size() - 50, 0); i--) {
      LexicalChain lc = vh.get(i);
      Word w = lc.getWords().get(0);
      if (!(comp.containsKey(w.getLexicon()))) {
        comp.put(w.getLexicon(), Boolean.TRUE);
        /*
        for(int j=0;j<lc.getWord().size();j++)
          System.out.print(lc.getWord().get(j) + " -- ");
        */

        // assertEquals(1.0d, lc.score());
        /*
        System.out.println(lc + ": ");
        for(Sentence sid : lc.getSentences()) {
          //if(sid>=0 && sid<s.size())
          System.out.println("\t" + sid + " [" + lc.score() + "]");
        }
        */
      }
    }
  }

}
