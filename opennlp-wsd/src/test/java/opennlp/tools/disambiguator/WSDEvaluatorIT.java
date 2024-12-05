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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.disambiguator.datareader.SemcorReaderExtended;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class WSDEvaluatorIT extends AbstractEvaluatorTest {

  private static final Logger LOG = LoggerFactory.getLogger(WSDEvaluatorIT.class);

  private static WSDDefaultParameters wsdParams;

  /*
   * Set up the testing variables
   */
  @BeforeAll
  static void prepareEnv(@TempDir(cleanup = CleanupMode.ALWAYS) Path tmpDir) {
    Path workDir = tmpDir.resolve("models" + File.separatorChar);
    Path trainingDir = workDir.resolve("training" + File.separatorChar)
                              .resolve("supervised" + File.separatorChar);
    wsdParams = new WSDDefaultParameters(trainingDir);

    final SemcorReaderExtended seReader = new SemcorReaderExtended(SEMCOR_DIR);

    // train the models in parallel
    sampleTestWordMapping.keySet().parallelStream().forEach(word -> {
      // don't take verbs because they are not from WordNet
      if (!SPLIT.split(word)[1].equals("v")) {
        List<WSDSample> instances = sampleTestWordMapping.get(word);
        if (instances != null && instances.size() > 1) {
          ObjectStream<WSDSample> sampleStream = seReader.getSemcorDataStream(word);
          /*
           * Tests training the disambiguator We test both writing and reading a model
           * file trained by semcor
           */
          try {
            final TrainingParameters trainingParams = TrainingParameters.defaultParams();
            trainingParams.put(TrainingParameters.THREADS_PARAM, 4);
            WSDModel trained = WSDisambiguatorME.train("en", sampleStream, trainingParams, wsdParams);
            assertNotNull(trained, "Checking the model to be written");
            File modelFile = new File(wsdParams.getTrainingDataDirectory() +
                    Character.toString(File.separatorChar) + word + ".wsd.model");
            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
              trained.serialize(modelOut);
            }
          } catch (IOException e1) {
            fail("Exception in training");
          }
        }
      }
    });

  }

  @Test
  void testDisambiguationEval() {
    sampleTestWordMapping.keySet().parallelStream().forEach(word -> {
      // don't take verbs because they are not from WordNet
      if (!SPLIT.split(word)[1].equals("v")) {
        File modelFile = new File(wsdParams.getTrainingDataDirectory() +
                Character.toString(File.separatorChar) + word + ".wsd.model");
        WSDModel model = null;
        try {
          model = new WSDModel(modelFile);
        } catch (IOException e) {
          fail(e.getMessage());
        }
        final AbstractWSDisambiguator wsdME = new WSDisambiguatorME(model, wsdParams);
        final WSDEvaluator evaluator = new WSDEvaluator(wsdME);

        List<WSDSample> instances = sampleTestWordMapping.get(word);
        assertNotNull(instances);

        if (instances.size() > 1) {
          StringBuilder sb = new StringBuilder();
          sb.append("------------------").append(word).append("------------------").append('\n');
          for (WSDSample instance : instances) {
            if (instance.getSenseIDs() != null && !instance.getSenseIDs()[0].equals("null")) {
              evaluator.evaluateSample(instance);
            }
          }
          sb.append(evaluator);
          LOG.info(sb.toString());
        } else {
          LOG.debug("null instances");
        }
      }
    });
  }

}
