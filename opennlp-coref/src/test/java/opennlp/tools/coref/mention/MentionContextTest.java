/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.coref.mention;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.coref.DefaultParse;
import opennlp.tools.coref.sim.GenderEnum;
import opennlp.tools.coref.sim.NumberEnum;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MentionContextTest {

  private static final String example =
          "(TOP (S (NP (DT The) (NN test)) (VP (MD may) (VP (VB come) (NP (NN today)))) (. .)))";

  private static ShallowParseMentionFinder finder;

  private Mention[] mentions;

  @BeforeAll
  public static void setUpClass() {
    finder = ShallowParseMentionFinder.getInstance(PTBHeadFinder.getInstance());
    assertNotNull(finder);
  }

  @BeforeEach
  public void setUp() {
    opennlp.tools.parser.Parse parse = Parse.parseParse(example);
    // prepare
    mentions = finder.getMentions(new DefaultParse(parse, 1));
    assertNotNull(mentions);
    assertEquals(2, mentions.length);
  }

  @Test
  void testCreateFromMention() {
    Mention m1 = new Mention(mentions[0]);
    m1.setId(42);
    // test
    MentionContext mc = new MentionContext(m1, 0,
            0, 0, 1, PTBHeadFinder.getInstance());
    assertEquals(42, mc.getId());
    assertEquals(GenderEnum.UNKNOWN, mc.getGender());
    assertEquals(0.0d, mc.getGenderProb());
    assertEquals(NumberEnum.UNKNOWN, mc.getNumber());
    assertEquals(0.0d, mc.getNumberProb());
    assertEquals(1, mc.getSentenceNumber());
    String headText = mc.getHeadText();
    assertNotNull(headText);
    assertFalse(headText.isBlank());
    String text = mc.toText();
    assertNotNull(text);
    assertFalse(text.isBlank());
    Span s1 = mc.getIndexSpan();
    assertEquals(0, s1.getStart());
    assertEquals(8, s1.getEnd());
    assertNotNull(mc.getHead());
    assertNotNull(mc.getHeadTokenParse());
    assertNotNull(mc.getTokenParses());
    assertNotNull(mc.getFirstToken());
    assertNotNull(mc.getFirstTokenText());
    assertNotNull(mc.getFirstTokenTag());
    assertNotNull(mc.getNextToken());
    assertNotNull(mc.getNextTokenBasal());
    assertTrue(mc.getNonDescriptorStart() >= 0);
    assertTrue(mc.getNounPhraseDocumentIndex() >= 0);
    assertTrue(mc.getNounPhraseSentenceIndex() >= 0);
    assertTrue(mc.getMaxNounPhraseSentenceIndex() >= 0);
    assertNull(mc.getPreviousToken());
  }

  @ParameterizedTest
  @EnumSource(value = GenderEnum.class)
  void testSetGender(GenderEnum input) {
    MentionContext mc = new MentionContext(new Mention(mentions[0]), 0,
            0, 0, 1, PTBHeadFinder.getInstance());
    mc.setGender(input, 0.5d);
    assertEquals(input, mc.getGender());
    assertEquals(0.5d, mc.getGenderProb());
  }

  @ParameterizedTest
  @EnumSource(value = NumberEnum.class)
  void testSetNumber(NumberEnum input) {
    MentionContext mc = new MentionContext(new Mention(mentions[0]), 0,
            0, 0, 1, PTBHeadFinder.getInstance());
    mc.setNumber(input, 0.5d);
    assertEquals(input, mc.getNumber());
    assertEquals(0.5d, mc.getNumberProb());
  }
}
