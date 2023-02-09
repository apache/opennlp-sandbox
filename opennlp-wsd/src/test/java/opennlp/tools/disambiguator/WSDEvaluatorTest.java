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

  static SensevalReader seReader;

  static String trainingDataDirectory = "src/test/resources/supervised/models/";

  static WSDDefaultParameters params = new WSDDefaultParameters("");
  static WSDisambiguatorME wsdME;
  static WSDModel model;

  static ArrayList<String> testWords;

  @Test
  @Disabled // TODO OPENNLP-1446: Investigate why test fails while parsing 'EnglishLS.train'
  void testTraining() {

    WSDHelper.print("Evaluation Started");

    seReader = new SensevalReader();
    testWords = seReader.getSensevalWords();
    params = new WSDDefaultParameters("");
    params.setTrainingDataDirectory(trainingDataDirectory);

    TrainingParameters trainingParams = new TrainingParameters();
    SemcorReaderExtended sr = new SemcorReaderExtended();

    WSDHelper.print("Training Started");
    for (String word : testWords) {
      // don't take verbs because they are not from WordNet
      if (!word.split("\\.")[1].equals("v")) {

        ArrayList<WSDSample> instances = seReader.getSensevalData(word);
        if (instances != null && instances.size() > 1) {
          WSDHelper.print("------------------" + word + "------------------");
          ObjectStream<WSDSample> sampleStream = sr.getSemcorDataStream(word);

          WSDModel writeModel = null;
          /*
           * Tests training the disambiguator We test both writing and reading a model
           * file trained by semcor
           */
          File outFile;
          try {
            writeModel = WSDisambiguatorME
                .train("en", sampleStream, trainingParams, params);
            assertNotNull(writeModel, "Checking the model to be written");
            writeModel.writeModel(params.getTrainingDataDirectory() + word);
            outFile = new File(
                params.getTrainingDataDirectory() + word + ".wsd.model");
            model = new WSDModel(outFile);
            assertNotNull(model, "Checking the read model");
            wsdME = new WSDisambiguatorME(model, params);
            assertNotNull(wsdME, "Checking the disambiguator");
          } catch (IOException e1) {
            e1.printStackTrace();
            fail("Exception in training");
          }
        }
      }
    }
  }

  @Test
  @Disabled  // Make this work once we have migrated to JUnit5 in the sandbox components
  void testDisambiguationEval() {

    WSDHelper.print("Evaluation Started");

    for (String word : testWords) {
      WSDEvaluator evaluator = new WSDEvaluator(wsdME);

      // don't take verbs because they are not from WordNet
      if (!word.split("\\.")[1].equals("v")) {

        ArrayList<WSDSample> instances = seReader.getSensevalData(word);
        if (instances != null && instances.size() > 1) {
          WSDHelper.print("------------------" + word + "------------------");
          for (WSDSample instance : instances) {
            if (instance.getSenseIDs() != null && !instance.getSenseIDs()[0]
                .equals("null")) {
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
