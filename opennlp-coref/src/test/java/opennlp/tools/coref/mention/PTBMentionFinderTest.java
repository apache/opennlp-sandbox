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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PTBMentionFinderTest {

  private static final String example =
          "(TOP (S (NP (DT The) (NN test)) (VP (MD may) (VP (VB come) (NP (NN today)))) (. .)))";

  private static PTBMentionFinder finder;

  private Parse parse;
  
  @BeforeAll
  public static void setUpClass() {
    finder = PTBMentionFinder.getInstance(PTBHeadFinder.getInstance());
    assertNotNull(finder);
  }


  @BeforeEach
  public void setUp() {
    parse = Parse.parseParse(example);
  }

  @Test
  void testCreateWithDifferentHeadFinder() {
    PTBMentionFinder mockFinder = PTBMentionFinder.getInstance(new DummyHeadFinder());
    assertNotNull(mockFinder);
    assertNotEquals(finder, mockFinder);
  }

  @Test
  void testGetMentions() {
    Mention[] mentions = finder.getMentions(new DefaultParse(parse, 1));
    assertNotNull(mentions);
    assertEquals(2, mentions.length);
    Span span1 = mentions[0].getSpan();
    assertEquals(0, span1.getStart());
    assertEquals(8, span1.getEnd());
    Span span2 = mentions[1].getSpan();
    assertEquals(18, span2.getStart());
    assertEquals(23, span2.getEnd());
  }

  @Test
  void getNamedEntities() {
    List<opennlp.tools.coref.mention.Parse> entities =
            finder.getNamedEntities(new DefaultParse(parse, 1));
    assertNotNull(entities);
    assertEquals(0, entities.size());
  }

  @Test
  void testGetEntityType() {
    String entityType = finder.getEntityType(new DefaultParse(parse, 1));
    assertNull(entityType); // expected as no entity is contained in the test sentence
  }

}

