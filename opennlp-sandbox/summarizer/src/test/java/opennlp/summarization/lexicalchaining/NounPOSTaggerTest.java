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
package opennlp.summarization.lexicalchaining;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link POSTagger} implementation {@link NounPOSTagger}.
 */
public class NounPOSTaggerTest {

  private static final String UNTAGGED_SENTENCE = "This is a test .";
  private static final String[] TOKENS_SENTENCE = {"This", "is", "a", "test", "."};
  private static final String[] TOKENS_TAGGED_SENTENCE = {"This/PRON", "is/AUX", "a/DET", "test/NOUN", "./PUNCT"};

  private static POSTagger tagger;  // SUT

  @BeforeAll
  public static void initResources() throws IOException {
    tagger = new NounPOSTagger("en");
  }

  @Test
  void testConstructWithInvalidResource() {
    assertThrows(IllegalArgumentException.class, () -> new NounPOSTagger(null));
  }

  @Test
  void testGetTaggedString() {
    String tagged = tagger.getTaggedString(UNTAGGED_SENTENCE);
    assertNotNull(tagged);
    assertEquals("This/PRON is/AUX a/DET test/NOUN ./PUNCT", tagged);
  }

  @Test
  void testGetTaggedStringInvalid1() {
    assertThrows(IllegalArgumentException.class, () -> tagger.getTaggedString(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @EmptySource
  void testGetTaggedStringInvalid2(String input) {
    String tagged = tagger.getTaggedString(input);
    assertNotNull(tagged);
  }

  @Test
  void testGetWordsOfTypeWithTags() {
    List<String> filteredByType = tagger.getWordsOfType(TOKENS_TAGGED_SENTENCE, POSTagger.NOUN);
    assertNotNull(filteredByType);
    assertEquals(1, filteredByType.size());
    assertEquals("test", filteredByType.get(0));
  }

  @Test
  void testGetWordsOfTypeWithoutTags() {
    assertThrows(IllegalArgumentException.class, () ->
            tagger.getWordsOfType(TOKENS_SENTENCE, POSTagger.NOUN));
  }

  @ParameterizedTest
  @ValueSource(ints = {POSTagger.ADJECTIVE, POSTagger.ADVERB, POSTagger.VERB})
  void testGetWordsOfTypeWithNonMatchingType(int type) {
    List<String> filteredByType = tagger.getWordsOfType(TOKENS_TAGGED_SENTENCE, type);
    assertNotNull(filteredByType);
    assertEquals(0, filteredByType.size());
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 5, Integer.MAX_VALUE})
  void testGetWordsOfTypeWithInvalidType(int type) {
    assertThrows(IllegalArgumentException.class, () ->
            tagger.getWordsOfType(TOKENS_TAGGED_SENTENCE, type));
  }

  @Test
  void testGetWordsOfTypeWithInvalidInput() {
    assertThrows(IllegalArgumentException.class, () ->
            tagger.getWordsOfType(null, POSTagger.NOUN));
  }
}
