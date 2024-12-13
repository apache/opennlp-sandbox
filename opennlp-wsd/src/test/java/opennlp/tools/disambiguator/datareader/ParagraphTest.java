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

package opennlp.tools.disambiguator.datareader;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParagraphTest {

  private Sentence s;
  private Word w;

  @BeforeEach
  void setUp() {
    s = new Sentence(1, 2);
    w = new Word(1, 1, 1, Word.Type.WORD, "cats", "cmd", "NN");
  }

  @Test
  void testCreate() {
    assertNotNull(s.getIwords());
    assertEquals(1, s.getPnum());
    assertEquals(2, s.getSnum());
    assertTrue(s.getIwords().isEmpty());
    s.addWord(w);
    assertFalse(s.getIwords().isEmpty());
    Paragraph p = new Paragraph(1, List.of(s));
    assertEquals(1, p.getPnum());
    assertEquals(1, p.getSentences().size());
  }

  @Test
  void testAddSentence() {
    // init
    Paragraph p = new Paragraph(1);
    assertEquals(1, p.getPnum());
    assertEquals(0, p.getSentences().size());

    // prepare
    assertNotNull(s.getIwords());
    assertEquals(1, s.getPnum());
    assertEquals(2, s.getSnum());
    assertTrue(s.getIwords().isEmpty());
    s.addWord(w);
    assertFalse(s.getIwords().isEmpty());

    // test
    p.addSentence(s);
    assertEquals(1, p.getPnum());
    assertEquals(1, p.getSentences().size());
  }

  @Test
  void testToString() {
    s.addWord(w);
    Paragraph p = new Paragraph(1, List.of(s));
    String asString = p.toString();
    assertNotNull(asString);
    assertFalse(asString.isEmpty());
  }

  @Test
  void testContains() {
    s.addWord(w);
    Paragraph p = new Paragraph(1, List.of(s));
    assertTrue(p.contains("cats.n"));
  }

  @Test
  void testContainsWithUnknownWord() {
    s.addWord(w);
    Paragraph p = new Paragraph(1, List.of(s));
    assertFalse(p.contains("dogs.n"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"\n", "\t", " "})
  void testContainsInvalid(String input) {
    s.addWord(w);
    Paragraph p = new Paragraph(1, List.of(s));
    assertFalse(p.contains(input));
  }
}
