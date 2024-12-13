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

package opennlp.tools.disambiguator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WTDIMSTest extends AbstractDisambiguatorTest {

  @Test
  void testCreatePlainWithIndex() {
    final WTDIMS wtdims = new WTDIMS(sentence1, tags1, lemmas1, 8);
    verify(wtdims, wtdims.getSentence(), wtdims.getPosTags(), wtdims.getLemmas());
  }

  @Test
  void testCreatePlainWithWordAndSenseIDs() {
    final WTDIMS wtdims = new WTDIMS(sentence1, tags1, lemmas1, "please", null);
    verify(wtdims, wtdims.getSentence(), wtdims.getPosTags(), wtdims.getLemmas());
  }

  @Test
  void testCreateViaWDSample() {
    final WSDSample sample = new WSDSample(sentence1, tags1, lemmas1, 8);
    final WTDIMS wtdims = new WTDIMS(sample);
    verify(wtdims, wtdims.getSentence(), wtdims.getPosTags(), wtdims.getLemmas());
  }

  @Test
  void testCreateInvalid1() {
    assertThrows(IllegalArgumentException.class, () -> new WTDIMS(new String[]{}, tags1, lemmas1, 0));
  }

  @Test
  void testCreateInvalid2() {
    assertThrows(IllegalArgumentException.class, () -> new WTDIMS(sentence1, new String[]{}, lemmas1, 0));
  }

  @Test
  void testCreateInvalid3() {
    assertThrows(IllegalArgumentException.class, () -> new WTDIMS(sentence1, tags1, new String[]{}, 0));
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, Integer.MAX_VALUE})
  void testCreateInvalid4(int input) {
    assertThrows(IllegalArgumentException.class, () -> new WTDIMS(sentence1, tags1, lemmas1, input));
  }


  private static void verify(WTDIMS result, String[] sentence, String[] posTags, String[] lemmas) {
    assertNotNull(sentence);
    assertNotNull(posTags);
    assertNotNull(lemmas);
    assertEquals(14, sentence.length);
    assertEquals(14, posTags.length);
    assertEquals(14, lemmas.length);
    assertEquals(8, result.getWordIndex());
    assertEquals("please", result.getWord());
    assertEquals("please.r", result.getWordTag());
  }
}
