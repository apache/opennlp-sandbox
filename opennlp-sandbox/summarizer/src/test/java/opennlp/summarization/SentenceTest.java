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
package opennlp.summarization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SentenceTest {

  private static final String SENTENCE = "This example is available in many tests.";

  // SUT
  private Sentence sentence;

  @BeforeEach
  public void setUp() {
    sentence = new Sentence(0, SENTENCE, 0, 0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @NullAndEmptySource
  public void testConstructInvalid1(String input) {
    assertThrows(IllegalArgumentException.class, () -> new Sentence(0, input, 0, 0));
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -42, -1})
  public void testConstructInvalid2(int input) {
    assertThrows(IllegalArgumentException.class, () -> new Sentence(input, SENTENCE, 0, 0));
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -42, -1})
  public void testConstructInvalid3(int input) {
    assertThrows(IllegalArgumentException.class, () -> new Sentence(0, SENTENCE, input, 0));
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -42, -1})
  public void testConstructInvalid4(int input) {
    assertThrows(IllegalArgumentException.class, () -> new Sentence(0,  SENTENCE, 0, input));
  }

  @Test
  public void testSentenceIdentity() {
    assertEquals(0, sentence.getSentId());
    assertEquals(0, sentence.getParagraph());
    assertEquals(0, sentence.getParaPos());
    assertEquals(SENTENCE, sentence.getStringVal());
  }

  @Test
  public void testStem() {
    String stemmed = sentence.stem();
    assertNotNull(stemmed);
    assertFalse(stemmed.isBlank());
    assertEquals("Thi exampl avail mani test ", stemmed);
  }

  @Test
  public void testGetWrdCnt() {
    int wordCountWithoutStopwords = sentence.getWordCount();
    assertEquals(5, wordCountWithoutStopwords);
  }

  @Test
  public void testHashcode() {
    int hash = sentence.hashCode();
    assertEquals(hash, new Sentence(0, SENTENCE, 0, 0).hashCode());
  }

  @Test
  public void testEquals() {
    assertEquals(sentence, new Sentence(0, SENTENCE, 0, 0));
  }

  @Test
  public void testToString() {
    assertEquals(sentence.toString(), new Sentence(0, SENTENCE, 0, 0).toString());
  }
}
