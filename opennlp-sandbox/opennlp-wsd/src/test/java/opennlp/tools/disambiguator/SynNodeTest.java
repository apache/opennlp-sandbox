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

import java.util.ArrayList;
import java.util.List;

import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.AbstractTest;
import opennlp.tools.tokenize.Tokenizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SynNodeTest extends AbstractTest {

  private static final Tokenizer tokenizer = WSDHelper.getTokenizer();

  private WordPOS wp;

  @BeforeEach
  public void setUp() {
    wp = new WordPOS("play", POS.NOUN);
  }

  @Test
  void testCreateSimple() {
    for (Synset sy : wp.getSynsets()) {
      // prepare
      final String gloss = sy.getGloss();
      final String[] tokenizedGloss = tokenizer.tokenize(gloss);
      final List<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);
      // test
      SynNode sn = new SynNode(null, sy, relvGlossWords);
      assertEquals(gloss, sn.getGloss());
      assertEquals(sy.getOffset(), sn.getSynsetID());
      assertEquals(relvGlossWords, sn.getSenseRelevantWords());
    }
  }

  @Test
  void testCreateExtended() {
    for (Synset sy : wp.getSynsets()) {
      // prepare
      final String[] tokenizedGloss = tokenizer.tokenize(sy.getGloss());
      final List<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);
      // test
      SynNode sn = new SynNode(null, sy, relvGlossWords);
      assertEquals(sy.getOffset(), sn.getSynsetID());
      sn.addAttributes();
      sn.addCauses();
      sn.addCoordinateTerms();
      sn.addEntailements();
      sn.addHolonyms();
      sn.addHypernyms();
      sn.addHyponyms();
      sn.addMeronyms();
      sn.addPertainyms();
      sn.addSynonyms();
      assertNotNull(sn.getAttributes());
      assertNotNull(sn.getCauses());
      assertNotNull(sn.getCoordinateTerms());
      assertNotNull(sn.getEntailments());
      assertNotNull(sn.getMeronyms());
      assertNotNull(sn.getHolonyms());
      assertNotNull(sn.getHypernyms());
      assertNotNull(sn.getHyponyms());
      assertNotNull(sn.getPertainyms());
      assertNotNull(sn.getSynonyms());
    }
  }

  @Test
  void testUpdateSenses() {
    List<SynNode> nodes = new ArrayList<>();
    // prepare
    for (Synset sy : wp.getSynsets()) {
      final String gloss = sy.getGloss();
      final String[] tokenizedGloss = tokenizer.tokenize(gloss);
      final List<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);
      nodes.add(new SynNode(null, sy, relvGlossWords));
    }
    // test
    List<WordSense> wordSenses = SynNode.updateSenses(nodes);
    assertNotNull(wordSenses);
    assertFalse(wordSenses.isEmpty());
    assertEquals(nodes.size(), wordSenses.size());
  }
}
