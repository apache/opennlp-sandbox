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

package opennlp.tools.coref;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultParseTest {

  private static final String example =
          "(TOP (S (NP (DT The) (NN test)) (VP (MD may) (VP (VB come) (NP (NN today)))) (. .)))";

  private static final String exampleChanged =
          "(TOP (S (NP (DT The) (NN proof)) (VP (MD may) (VP (VB come) (NP (NN today)))) (. .)))";

  private Parse parse;

  // SUT
  private DefaultParse dp;

  @BeforeEach
  public void setUp() {
    parse = Parse.parseParse(example);
    /* parse = ParserTool.parseLine("The test may come today . ", parserEN, 1)[0]; */
    dp = new DefaultParse(parse, 1);
  }

  @Test
  void testConstruct() {
    assertEquals(parse, dp.getParse());
    assertEquals(1, dp.getSentenceNumber());
    assertTrue(dp.isSentence());
    assertEquals(-1, dp.getEntityId());
    assertNull(dp.getParent());
    assertFalse(dp.isParentNAC());
    Span s = dp.getSpan();
    assertEquals(0, s.getStart());
    assertEquals(26, s.getEnd());
  }

  @Test
  void testCompareTo() {
    DefaultParse dp2 = new DefaultParse(parse, 1);
    assertEquals(0, dp.compareTo(dp2));
  }

  @Test
  void testCompareToIdentity() {
    //noinspection EqualsWithItself
    assertEquals(0, dp.compareTo(dp));
  }

  @Test
  void testEquals() {
    DefaultParse dp2 = new DefaultParse(parse, 1);
    assertEquals(dp, dp2);
  }

  @Test
  void testEqualsIdentity() {
    //noinspection EqualsWithItself
    assertEquals(dp, dp);
  }

  @Test
  void testHashCode() {
    DefaultParse dp2 = new DefaultParse(parse, 1);
    assertEquals(dp.hashCode(), dp2.hashCode());
  }

  @Test
  void testHashCodeIdentity() {
    assertEquals(dp.hashCode(), dp.hashCode());
  }

  @Test
  void testCompareToWithDifferentParses() {
    DefaultParse dp2 = new DefaultParse(Parse.parseParse(exampleChanged), 1);
    assertEquals(1, dp.compareTo(dp2));
    assertEquals(-1, dp2.compareTo(dp));
  }

  @Test
  void testGetNamedEntities() {
    List<opennlp.tools.coref.mention.Parse> dpNamedEntities = dp.getNamedEntities();
    assertNotNull(dpNamedEntities);
    assertTrue(dpNamedEntities.isEmpty());
  }

  @Test
  void testGetChildren() {
    List<opennlp.tools.coref.mention.Parse> dpChildren = dp.getChildren();
    assertNotNull(dpChildren);
    assertFalse(dpChildren.isEmpty());
    assertEquals(1, dpChildren.size());
  }

  @Test
  void testGetSyntacticChildren() {
    List<opennlp.tools.coref.mention.Parse> dpChildren = dp.getSyntacticChildren();
    assertNotNull(dpChildren);
    assertFalse(dpChildren.isEmpty());
    assertEquals(1, dpChildren.size());
  }

  @Test
  void testGetSyntacticType() {
    List<opennlp.tools.coref.mention.Parse> dpTokens = dp.getTokens();
    assertNotNull(dpTokens);
    assertFalse(dpTokens.isEmpty());
    assertEquals(6, dpTokens.size());
    assertEquals("DT", dpTokens.get(0).getSyntacticType());
    assertEquals("NN", dpTokens.get(1).getSyntacticType());
    assertEquals("MD", dpTokens.get(2).getSyntacticType());
    assertEquals("VB", dpTokens.get(3).getSyntacticType());
    assertEquals("NN", dpTokens.get(4).getSyntacticType());
    assertEquals(".", dpTokens.get(5).getSyntacticType());
  }

}
