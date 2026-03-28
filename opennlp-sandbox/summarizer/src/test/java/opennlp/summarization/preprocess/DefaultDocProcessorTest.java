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

package opennlp.summarization.preprocess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.summarization.Sentence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDocProcessorTest {

  private static DefaultDocProcessor dp;

  @BeforeAll
  static void initEnv() throws IOException {
    dp = new DefaultDocProcessor("en");
  }

  @Test
  void testGetSentences() {
    String sent = "This is a sentence, with some punctuations; to test if the sentence breaker can handle it! Is every thing working OK ? Yes.";
    List<Sentence> doc = dp.getSentences(sent);
    assertNotNull(doc);
    assertEquals(3, doc.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @NullAndEmptySource
  void testGetSentencesInvalid(String input) {
    List<Sentence> doc = dp.getSentences(input);
    assertNotNull(doc);
    assertEquals(0, doc.size());
  }

  @Test
  void testGetWords() {
    String sent = "This is a sentence, with some punctuations; to test if the sentence breaker can handle it! Is every thing working OK ? Yes.";
    List<Sentence> doc = dp.getSentences(sent);
    assertNotNull(doc);
    assertEquals(3, doc.size());
    for (Sentence sentence : doc) {
      String[] words = dp.getWords(sentence.getStringVal());
      assertNotNull(words);
      assertTrue(words.length > 0);
      assertTrue(words.length >= sentence.getWordCount()); // due to stop words not counted, this must hold.
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @NullAndEmptySource
  void testGetWordsInvalid(String input) {
    String[] words = dp.getWords(input);
    assertNotNull(words);
    assertEquals(0, words.length);
  }

  @Test
  void testDocToString() throws IOException {
    String content = dp.docToString("/news/0a2035f3f73b06a5150a6f01cffdf45d027bbbed.story");
    assertNotNull(content);
    assertFalse(content.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @NullAndEmptySource
  void testDocToStringInvalid(String input) throws IOException {
    String content = dp.docToString(input);
    assertNotNull(content);
    assertTrue(content.isEmpty());
  }

  @Test
  void testDocToSentences() throws IOException {
    List<Sentence> content = dp.docToSentences("/news/0a2035f3f73b06a5150a6f01cffdf45d027bbbed.story");
    assertNotNull(content);
    assertFalse(content.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @NullAndEmptySource
  void testDocToSentencesInvalid(String input) throws IOException {
    List<Sentence> content = dp.docToSentences(input);
    assertNotNull(content);
    assertTrue(content.isEmpty());
  }
}
