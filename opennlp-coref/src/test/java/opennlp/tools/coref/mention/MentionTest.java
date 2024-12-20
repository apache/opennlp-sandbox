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

import opennlp.tools.coref.DefaultParse;
import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MentionTest {

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
    // test
    Mention m1 = new Mention(mentions[0]);
    Mention m2 = new Mention(mentions[1]);
    assertNotEquals(m1, m2);
    assertEquals(1, m1.getId());
    Span s1 = m1.getSpan();
    assertEquals(0, s1.getStart());
    assertEquals(8, s1.getEnd());
    assertNotNull(m1.getHeadSpan());
    assertEquals("NN", m1.getNameType());
    Span s2 = m1.getSpan();
    assertEquals(0, s2.getStart());
    assertEquals(8, s2.getEnd());
    assertEquals("NN", m2.getNameType());
    assertNotNull(m2.getHeadSpan());
  }

  @Test
  void testCreatePlain() {
    Mention m = new Mention(mentions[0].getSpan(), mentions[0].getHeadSpan(), mentions[0].getId(),
            mentions[0].getParse(), mentions[0].type);
    // Note: This has not been set and is expected
    assertNull(m.getNameType());
  }

  @Test
  void testGetParse() {
    Mention m1 = new Mention(mentions[0]);
    opennlp.tools.coref.mention.Parse p1 = m1.getParse();
    assertNotNull(p1);
    m1.setParse(p1);
    assertEquals(p1, m1.getParse());
  }

  @Test
  void testSetId() {
    Mention m1 = new Mention(mentions[0]);
    m1.setId(42);
    assertEquals(42, m1.getId());
  }

  @Test
  void testCompareTo() {
    Mention m1 = new Mention(mentions[0]);
    Mention m2 = new Mention(mentions[1]);
    assertEquals(-1, m1.compareTo(m2));
    assertEquals(1, m2.compareTo(m1));
    assertEquals(0, m1.compareTo(m1)); // on purpose!
  }

  @Test
  void testToString() {
    assertTrue(mentions[0].toString().startsWith("mention(span="));
  }
}
