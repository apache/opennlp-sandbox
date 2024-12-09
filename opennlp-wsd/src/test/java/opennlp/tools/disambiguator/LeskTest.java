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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.disambiguator.LeskParameters.LESK_TYPE;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link Lesk}.
 * <p>
 * The scope of this test is to make sure that the Lesk disambiguator code can
 * be executed. This test can not detect mistakes which lead to incorrect
 * feature generation or other mistakes which decrease the disambiguation
 * performance of the disambiguator.
 */
// TODO write more tests
class LeskTest extends AbstractDisambiguatorTest {

  static Lesk lesk;

  /*
   * Setup the testing variables
   */
  @BeforeAll
  static void setUp() {
    lesk = new Lesk();
    LeskParameters params = new LeskParameters();
    params.setLeskType(LESK_TYPE.LESK_EXT);
    boolean[] a = {true, true, true, true, true, true, true, true, true, true};
    params.setFeatures(a);
    lesk.setParams(params);
  }

  /*
   * Tests disambiguating only one word : The ambiguous word "please"
   */
  @Test
  void testOneWordDisambiguation() {
    String sense = lesk.disambiguate(sentence1, tags1, lemmas1, 8);
    assertEquals("WORDNET please%4:02:00:: -1", sense, "Check 'please' sense ID");
  }

  /*
   * Tests disambiguating a word Span In this case we test a mix of monosemous
   * and polysemous words as well as words that do not need disambiguation such
   * as determiners
   */
  @Test
  void testWordSpanDisambiguation() {
    Span span = new Span(3, 7);
    List<String> senses = lesk.disambiguate(sentence2, tags2, lemmas2, span);

    assertEquals(5, senses.size(), "Check number of returned words");
    assertEquals("WORDNET highly%4:02:05:: 1.0", senses.get(0), "Check 'highly' sense ID");
    assertEquals("WORDNET radioactive%3:00:00:: 6.0", senses.get(1), "Check 'radioactive' sense ID");
    assertEquals("WSDHELPER preposition / subordinating conjunction", senses.get(2), "Check 'to' as preposition");
    assertEquals("WSDHELPER determiner", senses.get(3), "Check determiner");
    assertEquals("WORDNET point%1:09:00:: -1", senses.get(4), "Check 'point' sense ID");
  }

  /*
   * Tests disambiguating all the words
   */
  @Test
  void testAllWordsDisambiguation() {
    List<String> senses = lesk.disambiguate(sentence3, tags3, lemmas3);

    assertEquals(16, senses.size(), "Check number of returned words");
    String sensePosSix = senses.get(6);
    assertNotNull(sensePosSix);
    assertEquals("WSDHELPER personal pronoun", sensePosSix, "Check preposition");
  }

}