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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import opennlp.tools.disambiguator.datareader.SemcorReaderExtended;
import opennlp.tools.disambiguator.datareader.SensevalReader;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

// TODO improve the tests improve parameters
class WSDEvaluatorTest extends AbstractEvaluatorTest {

  static WSDisambiguatorME wsdME;
  static WSDModel model;

  static List<String> testWords;

  /*
   * Setup the testing variables
   */
  @BeforeAll
  static void setUpAndTraining() {
    testWords = seReader.getSensevalWords();

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    File file = new File(classLoader.getResource("models").getPath());

    WSDDefaultParameters params = new WSDDefaultParameters(
            file.getPath() + File.separatorChar + "supervised" + File.separatorChar);
    final TrainingParameters trainingParams = new TrainingParameters();
    final SemcorReaderExtended sr = new SemcorReaderExtended(SEMCOR_DIR);

    for (String word : testWords) {
      // don't take verbs because they are not from WordNet
      if (!SPLIT.split(word)[1].equals("v")) {

        List<WSDSample> instances = seReader.getSensevalData(word);
        if (instances != null && instances.size() > 1) {
          ObjectStream<WSDSample> sampleStream = sr.getSemcorDataStream(word);
          /*
           * Tests training the disambiguator We test both writing and reading a model
           * file trained by semcor
           */
          File outFile;
          try {
            WSDModel writeModel = WSDisambiguatorME.train("en", sampleStream, trainingParams, params);
            assertNotNull(writeModel, "Checking the model to be written");
            writeModel.writeModel(params.getTrainingDataDirectory() + word);
            outFile = new File(params.getTrainingDataDirectory() + word + ".wsd.model");
            model = new WSDModel(outFile);
            assertNotNull(model, "Checking the read model");
            wsdME = new WSDisambiguatorME(model, params);
            assertNotNull(wsdME, "Checking the disambiguator");
          } catch (IOException e1) {
            fail("Exception in training");
          }
        }
      }
    }

  }

  @Test
  void testDisambiguationEval() {

    WSDHelper.print("Evaluation Started");

    for (String word : testWords) {
      WSDEvaluator evaluator = new WSDEvaluator(wsdME);
      // don't take verbs because they are not from WordNet
      if (!SPLIT.split(word)[1].equals("v")) {

        List<WSDSample> instances = seReader.getSensevalData(word);
        assertNotNull(instances);

        if (instances != null && instances.size() > 1) {
          WSDHelper.print("------------------" + word + "------------------");
          for (WSDSample instance : instances) {
            if (instance.getSenseIDs() != null && !instance.getSenseIDs()[0].equals("null")) {
              evaluator.evaluateSample(instance);
            }
          }
          WSDHelper.print(evaluator.toString());
        } else {
          WSDHelper.print("null instances");
        }
      }

    }
  }

}
