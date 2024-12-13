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

import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import opennlp.tools.AbstractTest;
import opennlp.tools.tokenize.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WordSenseTest extends AbstractDisambiguatorTest {

  private static final Tokenizer tokenizer = WSDHelper.getTokenizer();

  private List<SynNode> nodes;

  @BeforeEach
  public void setUp() {
    final WordPOS wp = new WordPOS("play", POS.NOUN);
    final List<Synset> synsets = wp.getSynsets();
    assertEquals(17, synsets.size());

    nodes = new ArrayList<>();
    for (final Synset sy : synsets) {
      // prepare
      final String gloss = sy.getGloss();
      final String[] tokenizedGloss = tokenizer.tokenize(gloss);
      final List<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);
      final SynNode sn = new SynNode(null, sy, relvGlossWords);
      nodes.add(sn);
    }
  }

  @Test
  void testCreateSimple() {
    assertEquals(17, nodes.size());
    final List<WordSense> senses = new ArrayList<>(nodes.size());
    // test
    for (int i = 0; i < nodes.size(); i++) {
      WordSense wordSense = new WordSense(i + 1, nodes.get(i));
      assertTrue(wordSense.getId() > 0);
      assertNotNull(wordSense.getGloss());
      senses.add(wordSense);
    }
    assertEquals(17, senses.size());
  }

  @Test
  void testCompareWordSenses() {
    assertEquals(17, nodes.size());
    // test
    WordSense wordSense1 = new WordSense(0, nodes.get(0));
    WordSense wordSense2 = new WordSense(1, nodes.get(1));
    wordSense1.setScore(1.0);
    wordSense2.setScore(2.0);
    assertEquals(1.0, wordSense1.getScore(), 0.0);
    assertEquals(2.0, wordSense2.getScore(), 0.0);
    // testing variants
    assertEquals(-1, wordSense1.compareTo(wordSense2));
    wordSense1.setScore(1.0);
    wordSense2.setScore(1.0);
    assertEquals(0, wordSense1.compareTo(wordSense2));
    wordSense1.setScore(2.0);
    wordSense2.setScore(1.0);
    assertEquals(1, wordSense1.compareTo(wordSense2));
  }

  @Test
  void testCreateWithWDSample() {
    // target word is "summer"
    final WSDSample wds = new WSDSample(sentence3, tags3, lemmas3, 1);
    final List<Synset> synsets = wds.getSynsets();
    final List<WordSense> senses = new ArrayList<>(synsets.size());
    for (final Synset sy : synsets) {
      // prepare
      final String gloss = sy.getGloss();
      final String[] tokenizedGloss = tokenizer.tokenize(gloss);
      final List<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);
      final SynNode sn = new SynNode(null, sy, relvGlossWords);
      WordSense wordSense = new WordSense(wds, sn);
      assertEquals(wds, wordSense.getWSDSample());
      assertEquals(sn, wordSense.getNode());
      wordSense.setWSDSample(wds); // re-setting the wds sample instance
      assertEquals(wds, wordSense.getWSDSample());
      senses.add(wordSense);
    }
    assertEquals(2, senses.size());
  }

}
