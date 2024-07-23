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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.disambiguator.datareader.SemcorReaderExtended;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * This is the test class for {@link WSDisambiguatorME}.
 * <p/>
 * The scope of this test is to make sure that the WSDisambiguatorME code can be
 * executed. This test can not detect mistakes which lead to incorrect feature
 * generation or other mistakes which decrease the disambiguation performance of
 * the disambiguator.
 * <p/>
 * In this test the {@link WSDisambiguatorME} is trained with Semcor
 * and then the computed model is used to predict sentences
 * from the training sentences.
 */
// TODO write more tests
// TODO modify when we fix the parameter model
class WSDTest extends AbstractWSDTest {

  static WSDDefaultParameters params;
  static WSDModel model;

  /*
   * The class under test
   */
  private WSDisambiguatorME wsdME;

  /*
   * Setup the testing variables
   */
  @BeforeAll
  static void setUpAndTraining() {
    final String test = "please.v";
    
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    File file = new File(classLoader.getResource("models").getPath());

    params = new WSDDefaultParameters(file.getPath() + File.separatorChar);
    final TrainingParameters trainingParams = new TrainingParameters();
    final SemcorReaderExtended sr = new SemcorReaderExtended(SEMCOR_DIR);
    final ObjectStream<WSDSample> sampleStream = sr.getSemcorDataStream(test);

    /*
     * Tests training the disambiguator
     * We test both writing and reading a model file trained by semcor
    */
    try {
      WSDModel writeModel = WSDisambiguatorME.train("en", sampleStream, trainingParams, params);
      assertNotNull(writeModel, "Checking the model to be written");
      writeModel.writeModel(params.getTrainingDataDirectory() + test);
      File outFile = new File(params.getTrainingDataDirectory() + test + ".wsd.model");
      model = new WSDModel(outFile);
      assertNotNull(model, "Checking the read model");

    } catch (IOException e1) {
      fail("Exception in training: " + e1.getMessage());
    }
  }

  @BeforeEach
  public void setup() {
    wsdME = new WSDisambiguatorME(model, params);
    assertNotNull(wsdME, "Checking the disambiguator");
  }

  /*
   * Tests disambiguating only one word : The ambiguous word "please"
   */
  @Test
  void testOneWordDisambiguation() {
    String sense = wsdME.disambiguate(sentence1, tags1, lemmas1, 8);
    assertEquals("WORDNET please%2:37:00::", sense, "Check 'please' sense ID");
  }

  /*
   * Tests disambiguating a word Span In this case we test a mix of monosemous
   * and polysemous words as well as words that do not need disambiguation such
   * as determiners
   */
  @Test
  void testWordSpanDisambiguation() {
    Span span = new Span(3, 7);
    List<String> senses = wsdME.disambiguate(sentence2, tags2, lemmas2, span);

    assertEquals(5, senses.size(), "Check number of returned words");
    assertEquals("WORDNET highly%4:02:01::", senses.get(0), "Check 'highly' sense ID");
    assertEquals(
        "WORDNET radioactive%3:00:00::", senses.get(1), "Check 'radioactive' sense ID");
    assertEquals("WSDHELPER to", senses.get(2), "Check preposition");
    assertEquals("WSDHELPER determiner", senses.get(3), "Check determiner");
  }

  /*
   * Tests disambiguating all the words
   */
  @Test
  void testAllWordsDisambiguation() {
    List<String> senses = wsdME.disambiguate(sentence3, tags3, lemmas3);

    assertEquals(15, senses.size(), "Check number of returned words");
    assertEquals("WSDHELPER personal pronoun", senses.get(6), "Check preposition");
  }

}
