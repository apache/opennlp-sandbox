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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WordRelationshipDeterminationTest {

  // SUT
  private WordRelationshipDetermination wrd;

  @BeforeEach
  public void setUp() {
    wrd = new WordRelationshipDetermination();
  }

  @Test
  void testGetWordSenses() {
    LexicalChain l = new LexicalChain();
    List<Word> words = wrd.getWordSenses("music");
    assertNotNull(words);
    assertFalse(words.isEmpty());
    l.addWord(words.get(0));
  }

  @Test
  void testGetRelation() {
    LexicalChain l = new LexicalChain();
    List<Word> words = wrd.getWordSenses("music");
    assertNotNull(words);
    assertFalse(words.isEmpty());
    l.addWord(words.get(0));
    // int rel = lcs.getRelation(l, "nation");
    WordRelation rel2 = wrd.getRelation(l, "tune", true);
    WordRelation rel3 = wrd.getRelation(l, "vocal", true);
    assertEquals(1, rel2.relation());
    assertEquals(1, rel3.relation());
    // assertEquals(rel, LexicalChainingSummarizer.STRONG_RELATION);
    assertEquals(WordRelation.MED_RELATION, rel2.relation());
    assertEquals(WordRelation.MED_RELATION, rel3.relation());
  }
}
