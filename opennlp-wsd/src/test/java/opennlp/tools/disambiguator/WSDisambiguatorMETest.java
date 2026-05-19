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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.disambiguator.datareader.SemcorReaderExtended;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is the test class for {@link WSDisambiguatorME}.
 * <p/>
 * The scope of this test is to make sure that the WSDisambiguatorME code can be
 * executed. This test can not detect mistakes which lead to incorrect feature
 * generation or other mistakes which decrease the disambiguation performance of
 * the disambiguator.
 * <p/>
 * In this test the {@link WSDisambiguatorME} is trained with Semcor and then the
 * computed model is used to predict sentences from the training sentences.
 */
class WSDisambiguatorMETest extends AbstractDisambiguatorTest {

  static WSDDefaultParameters wsdParams;
  static WSDModel model;

  /*
   * The class under test
   */
  private WSDisambiguatorME wsdME;

  @BeforeAll
  static void prepareEnv(@TempDir(cleanup = CleanupMode.ALWAYS) Path tmpDir) {

    Path workDir = tmpDir.resolve("models" + File.separatorChar);
    Path trainingDir = workDir.resolve("training" + File.separatorChar)
                              .resolve("supervised" + File.separatorChar);
    File folder = trainingDir.toFile();
    if (!folder.exists()) {
      assertTrue(folder.mkdirs());
    }
    wsdParams = WSDDefaultParameters.defaultParams();
    wsdParams.putIfAbsent(WSDDefaultParameters.TRAINING_DIR_PARAM, trainingDir.toAbsolutePath().toString());
    
    final TrainingParameters trainingParams = TrainingParameters.defaultParams();
    final WSDisambiguatorFactory factory = new WSDisambiguatorFactory();
    final SemcorReaderExtended sr = new SemcorReaderExtended(SEMCOR_DIR);

    final String test = "please.v";
    final ObjectStream<WSDSample> sampleStream = sr.getSemcorDataStream(test);

    /*
     * Tests training the disambiguator
     * We test both writing and reading a model file trained by semcor
    */
    try {
      model= WSDisambiguatorME.train("en", sampleStream, trainingParams, wsdParams, factory);
      assertNotNull(model, "Checking the model");
    } catch (IOException e1) {
      fail("Exception in training: " + e1.getMessage());
    }
  }

  @BeforeEach
  public void setup() {
    wsdME = new WSDisambiguatorME(model, wsdParams);
    assertNotNull(wsdME, "Checking the disambiguator");
  }

  /*
   * Tests disambiguating only one word : The ambiguous word "please"
   */
  @Test
  void testDisambiguateOneWord() {
    String sense = wsdME.disambiguate(sentence1, tags1, lemmas1, 8);
    assertEquals("WORDNET please%4:02:00::", sense, "Check 'please' sense ID");
  }

  /*
   * Tests disambiguating a word Span In this case we test a mix of monosemous
   * and polysemous words as well as words that do not need disambiguation such
   * as determiners
   */
  @Test
  void testDisambiguateWordSpan() {
    Span span = new Span(3, 7);
    List<String> senses = wsdME.disambiguate(sentence2, tags2, lemmas2, span);

    assertEquals(5, senses.size(), "Check number of returned words");
    assertEquals("WORDNET highly%4:02:01::", senses.get(0), "Check 'highly' sense ID");
    assertEquals("WORDNET radioactive%3:00:00::", senses.get(1), "Check 'radioactive' sense ID");
    assertEquals("WSDHELPER preposition / subordinating conjunction", senses.get(2), "Check 'to' as preposition");
    assertEquals("WSDHELPER determiner", senses.get(3), "Check determiner");
    assertEquals("WORDNET point%1:09:00::", senses.get(4), "Check 'point' sense ID");
  }

  /*
   * Tests disambiguating all the words
   */
  @Test
  void testDisambiguateAllWords() {
    List<String> senses = wsdME.disambiguate(sentence3, tags3, lemmas3);

    assertEquals(16, senses.size(), "Check number of returned words");
    String sensePosSix = senses.get(6);
    assertNotNull(sensePosSix);
    assertEquals("WSDHELPER personal pronoun", sensePosSix, "Check preposition");
  }

  @Test
  void testDisambiguateInvalid01() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate((WSDSample) null));
  }

  @Test
  void testDisambiguateInvalid02() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(null, tags1, lemmas1));
  }

  @Test
  void testDisambiguateInvalid03() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence1, null, lemmas1));
  }

  @Test
  void testDisambiguateInvalid04() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence1, tags1, null));
  }

  @Test
  void testDisambiguateInvalid05() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(null, tags1, lemmas1, 1));
  }

  @Test
  void testDisambiguateInvalid06() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence1, null, lemmas1, 1));
  }

  @Test
  void testDisambiguateInvalid07() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence1, tags1, null, 1));
  }

  @Test
  void testDisambiguateInvalid08() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence1, tags1, lemmas1, -1));
  }

  @Test
  void testDisambiguateInvalid09() {
    Span span = new Span(3, 7);
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(null, tags2, lemmas2, span));
  }

  @Test
  void testDisambiguateInvalid10() {
    Span span = new Span(3, 7);
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence2, null, lemmas2, span));
  }

  @Test
  void testDisambiguateInvalid11() {
    Span span = new Span(3, 7);
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence2, tags2, null, span));
  }

  @Test
  void testDisambiguateInvalid12() {
    assertThrows(IllegalArgumentException.class, () -> wsdME.disambiguate(sentence2, tags2, lemmas2, null));
  }
}
