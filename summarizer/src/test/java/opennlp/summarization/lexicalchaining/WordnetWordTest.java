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

import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WordnetWordTest {

  private WordRelationshipDetermination wrd;

  // SUT
  private Word word;

  @BeforeEach
  public void setUp() {
    wrd = new WordRelationshipDetermination();
    List<Word> words = wrd.getWordSenses("music");
    assertNotNull(words);
    assertFalse(words.isEmpty());
    word = words.get(0);
    assertNotNull(word);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\t", "\n", " "})
  @NullAndEmptySource
  public void testConstructInvalid1(String input) {
    assertThrows(IllegalArgumentException.class, () -> new WordnetWord(input, new DummyWordID()));
  }

  @Test
  public void testConstructInvalid2() {
    assertThrows(IllegalArgumentException.class, () -> new WordnetWord("music", null));
  }

  @Test
  public void testSentenceIdentity() {
    assertEquals("music", word.getLexicon());
    assertEquals("WID-07034009-N-01-music", word.getID().toString());
  }

  @Test
  public void testHashcode() {
    int hash = word.hashCode();
    assertEquals(hash, wrd.getWordSenses("music").get(0).hashCode());
  }

  @Test
  public void testEquals() {
    assertEquals(word, wrd.getWordSenses("music").get(0));
  }

  @Test
  public void testToString() {
    assertEquals(word.toString(), wrd.getWordSenses("music").get(0).toString());
  }

  private static class DummyWordID implements IWordID {
    @Override
    public ISynsetID getSynsetID() {
      return null;
    }

    @Override
    public int getWordNumber() {
      return 0;
    }

    @Override
    public String getLemma() {
      return "";
    }

    @Override
    public POS getPOS() {
      return null;
    }
  }
}
