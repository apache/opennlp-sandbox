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

import net.sf.extjwnl.data.POS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WordTest {

  // SUT
  private Word w;

  @BeforeEach
  public void setUp() {
    w = new Word(1, 1, 1, Word.Type.WORD, "organisms", "cmd", "NN");
    w.setLemma("organism"); // trivial
    w.setLexsn("1:03:00::"); // looked up
    w.setWnsn("123"); // fake
  }
  
  @Test
  void testCreateWithoutLemmas() {
    assertEquals(1, w.getPnum());
    assertEquals(1, w.getWnum());
    assertEquals(1, w.getSnum());
    assertEquals("123", w.getWnsn());
    assertEquals(Word.Type.WORD, w.getType());
    assertEquals("organisms", w.getWord());
    assertEquals("cmd", w.getCmd());
    assertEquals(POS.NOUN, w.getPos());
    assertEquals(POS.NOUN.getKey(), w.getPos().getKey());
  }

  @Test
  void testToString() {
    assertEquals(w.getWord(), w.toString());
  }

  @Test
  void testIsInstanceOf() {
    assertEquals("organisms", w.getWord());
    // test
    assertTrue(w.isInstanceOf("organism.n"));
    assertFalse(w.isInstanceOf("house.n"));
  }

  @Test
  void testEquals() {
    Word object = new Word(1, 1, 1, Word.Type.WORD,
            "organisms", "cmd", "NN");
    assertEquals(w, object);
    // setting detailed word data ->  equals must still hold
    object.setLemma("organism");
    object.setLexsn("1:03:00::");
    assertEquals(w, object);
  }

  @Test
  void testHashCode() {
    Word object = new Word(1, 1, 1, Word.Type.WORD,
            "organisms", "cmd", "NN");
    assertNotEquals(w.hashCode(), object.hashCode());
    // setting detailed word data ->  hashcode must now hold
    object.setLemma("organism");
    assertEquals(w.hashCode(), object.hashCode());
  }

  @Test
  void testEqualsWithItself() {
    assertEquals(w, w);
  }

  @Test
  void testEqualsWithDifferentWord() {
    Word object = new Word(1, 1, 1, Word.Type.WORD,
            "cats", "cmd", "NN");
    assertNotEquals(w, object);
  }

  @Test
  void testEqualsWithDifferentObject() {
    assertNotEquals(w, "foo"); // leave as is: test idea!
  }

  @Test
  void testSenseEquals() {
    Word object = new Word(1, 1, 1, Word.Type.WORD,
            "organisms", "cmd", "NN");
    assertFalse(w.senseEquals(object));
    object.setLemma("organism");
    assertFalse(w.senseEquals(object));
    // senseEquals must now hold
    object.setLexsn("1:03:00::");
    assertTrue(w.senseEquals(object));
  }

  @Test
  void testSenseEqualsWithItself() {
    assertTrue(w.senseEquals(w));
  }

  @Test
  void testSenseEqualsWithDifferentObject() {
    assertFalse(w.senseEquals("foo")); // leave as is: test idea!
  }
}
