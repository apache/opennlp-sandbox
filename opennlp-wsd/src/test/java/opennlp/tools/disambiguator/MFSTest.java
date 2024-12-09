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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This is the test class for {@link MFS}.
 * <p>
 * The scope of this test is to make sure that the MFS disambiguator code can be
 * executed. This test can not detect mistakes which lead to incorrect feature
 * generation or other mistakes which decrease the disambiguation performance of
 * the disambiguator.
 */
// TODO write more tests
// TODO modify when we fix the parameter model
class MFSTest extends AbstractDisambiguatorTest {

  static MFS mfs;


  /*
   * Setup the testing variables and the training files
   */
  @BeforeAll
  static void setUpAndTraining() {
    mfs = new MFS();
  }

  /*
   * Tests disambiguating only one word : The ambiguous word "please"
   */
  @Test
  void testOneWordDisambiguation() {
    String sense = mfs.disambiguate(sentence1, tags1, lemmas1, 8);
    assertEquals("WORDNET please%4:02:00::", sense, "Check 'please' sense ID");
  }

  /*
   * Tests disambiguating a word Span In this case we test a mix of monosemous
   * and polysemous words as well as words that do not need disambiguation such
   * as determiners
   */
  @Test
  void testWordSpanDisambiguation() {
    Span span = new Span(3, 7);
    List<String> senses = mfs.disambiguate(sentence2, tags2, lemmas2, span);
    assertEquals(5, senses.size(), "Check number of returned words");
    String sensePosZero = senses.get(0);
    assertNotNull(sensePosZero);
    assertEquals("WORDNET highly%4:02:01::", sensePosZero, "Check 'highly' sense ID");
    assertEquals("WORDNET radioactive%3:00:00::", senses.get(1), "Check 'radioactive' sense ID");
    assertEquals("WSDHELPER preposition / subordinating conjunction", senses.get(2), "Check 'to' as preposition");
    assertEquals("WSDHELPER determiner", senses.get(3), "Check determiner");
    assertEquals("WORDNET point%1:09:00::", senses.get(4), "Check 'point' sense ID");
  }

  /*
   * Tests disambiguating all the words
   */
  @Test
  void testAllWordsDisambiguation() {
    List<String> senses = mfs.disambiguate(sentence3, tags3, lemmas3);

    assertEquals(16, senses.size(), "Check number of returned words");
    String sensePosSix = senses.get(6);
    assertNotNull(sensePosSix);
    assertEquals("WSDHELPER personal pronoun", sensePosSix, "Check preposition");
  }
}