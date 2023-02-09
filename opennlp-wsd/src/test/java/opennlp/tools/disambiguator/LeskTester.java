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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.disambiguator.LeskParameters.LESK_TYPE;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link Lesk}.
 * <p>
 * The scope of this test is to make sure that the Lesk disambiguator code can
 * be executed. This test can not detect mistakes which lead to incorrect
 * feature generation or other mistakes which decrease the disambiguation
 * performance of the disambiguator.
 */
class LeskTester {
  // TODO write more tests

  static String modelsDir = "src/test/resources/models/";

  static Lesk lesk;

  static String test1 = "We need to discuss an important topic, please write to me soon.";
  static String test2 = "The component was highly radioactive to the point that"
      + " it has been activated the second it touched water";
  static String test3 = "The summer is almost over and I did not go to the beach even once";

  static String[] sentence1;
  static String[] sentence2;
  static String[] sentence3;

  static String[] tags1;
  static String[] tags2;
  static String[] tags3;

  static List<List<String>> lemmas1;
  static List<List<String>> lemmas2;
  static List<List<String>> lemmas3;

  /*
   * Setup the testing variables
   */
  @BeforeAll
  static void setUp() {

    WSDHelper.loadTagger(modelsDir + "en-pos-maxent.bin");
    WSDHelper.loadTokenizer(modelsDir + "en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir + "en-lemmatizer.dict.gz");

    sentence1 = WSDHelper.getTokenizer().tokenize(test1);
    sentence2 = WSDHelper.getTokenizer().tokenize(test2);
    sentence3 = WSDHelper.getTokenizer().tokenize(test3);

    tags1 = WSDHelper.getTagger().tag(sentence1);
    tags2 = WSDHelper.getTagger().tag(sentence2);
    tags3 = WSDHelper.getTagger().tag(sentence3);

    final Lemmatizer lemmatizer = WSDHelper.getLemmatizer();
    lemmas1 = lemmatizer.lemmatize(Arrays.asList(sentence1), Arrays.asList(tags1));
    lemmas2 = lemmatizer.lemmatize(Arrays.asList(sentence2), Arrays.asList(tags2));
    lemmas3 = lemmatizer.lemmatize(Arrays.asList(sentence3), Arrays.asList(tags3));

    lesk = new Lesk();

    LeskParameters params = new LeskParameters();
    params.setLeskType(LESK_TYPE.LESK_EXT);
    boolean a[] = {true, true, true, true, true, true, true, true, true, true};
    params.setFeatures(a);
    lesk.setParams(params);
  }

  /*
   * Tests disambiguating only one word : The ambiguous word "please"
   */
  @Test
  void testOneWordDisambiguation() {
    String sense = lesk.disambiguate(sentence1, tags1, lemmas1.get(0).toArray(new String[0]), 8);
    assertEquals("WORDNET please%2:37:00:: -1", sense, "Check 'please' sense ID");
  }

  /*
   * Tests disambiguating a word Span In this case we test a mix of monosemous
   * and polysemous words as well as words that do not need disambiguation such
   * as determiners
   */
  @Test
  void testWordSpanDisambiguation() {
    Span span = new Span(3, 7);
    List<String> senses = lesk.disambiguate(sentence2, tags2, lemmas2.get(0).toArray(new String[0]), span);

    assertEquals(5, senses.size(), "Check number of returned words");
    assertEquals("WORDNET highly%4:02:01:: 3.8",
        senses.get(0), "Check 'highly' sense ID");
    assertEquals(
        "WORDNET radioactive%3:00:00:: 6.0", senses.get(1), "Check 'radioactive' sense ID");
    assertEquals("WSDHELPER to", senses.get(2), "Check preposition");
    assertEquals("WSDHELPER determiner", senses.get(3), "Check determiner");
  }

  /*
   * Tests disambiguating all the words
   */
  @Test
  void testAllWordsDisambiguation() {
    List<String> senses = lesk.disambiguate(sentence3, tags3, lemmas3.get(0).toArray(new String[0]));

    assertEquals(15, senses.size(), "Check number of returned words");
    assertEquals("WSDHELPER personal pronoun",
        senses.get(6), "Check preposition");
  }

}