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

package opennlp.tools.disambiguator;

import java.util.List;

import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.AbstractTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WordPOSTest extends AbstractTest {

  @Test
  void testCreatePlain() {
    WordPOS w = new WordPOS("cats", "NN");
    verify(w);
  }

  @Test
  void testCreateWithPOS() {
    WordPOS w = new WordPOS("cats", WSDHelper.getPOS("NN"));
    verify(w);
  }

  private void verify(WordPOS w) {
    assertEquals("cats", w.getWord());
    assertEquals(POS.NOUN, w.getPOS());
    assertNotNull(w.getStems());
    assertFalse(w.getStems().isEmpty());
    assertEquals("cat", w.getStems().get(0));
    List<Synset> synsets = w.getSynsets();
    assertNotNull(synsets);
    assertEquals(8, synsets.size());
  }

  @Test
  void testCreateWordPOSWithIrregularTerm() {
    WordPOS w = new WordPOS("xyz", WSDHelper.getPOS("NN"));
    assertEquals("xyz", w.getWord());
    assertEquals(POS.NOUN, w.getPOS());
    List<Synset> synsets = w.getSynsets();
    assertNull(synsets);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"\t", "\n", " "})
  void testCreateWordPOSInvalid1(String input) {
    assertThrows(IllegalArgumentException.class, () -> new WordPOS(input, WSDHelper.getPOS("NN")));
  }

  @ParameterizedTest
  @NullSource
  void testCreateWordPOSInvalid1(POS input) {
    assertThrows(IllegalArgumentException.class, () -> new WordPOS("cat", input));
  }

  @Test
  void testIsStemEquivalent() {
    WordPOS w1 = new WordPOS("cats", WSDHelper.getPOS("NN"));
    WordPOS w2 = new WordPOS("cat", WSDHelper.getPOS("NN"));
    assertTrue(w1.isStemEquivalent(w2));
  }

  @Test
  void testIsStemEquivalentInvalid() {
    WordPOS w1 = new WordPOS("cats", WSDHelper.getPOS("NN"));
    WordPOS w2 = new WordPOS("xyz", WSDHelper.getPOS("NN"));
    assertFalse(w1.isStemEquivalent(w2));
    assertFalse(w2.isStemEquivalent(w1));
  }
}
