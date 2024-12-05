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

package opennlp.tools.cmdline.wsd;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.AbstractTest;
import opennlp.tools.disambiguator.WSDDefaultParameters;
import opennlp.tools.disambiguator.WSDModel;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDisambiguatorME;
import opennlp.tools.disambiguator.datareader.SemcorReaderExtended;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class WSDModelLoaderTest extends AbstractTest {

  private static final String WORD_TAG = "pleased.a";

  private static WSDModel trainedModel;
  private static Path trainingDir;

  // SUT
  private WSDModelLoader loader;

  @BeforeAll
  static void createSimpleWSDModel(@TempDir(cleanup = CleanupMode.ALWAYS) Path tmpDir) {

    Path workDir = tmpDir.resolve("models" + File.separatorChar);
    trainingDir = workDir.resolve("training" + File.separatorChar)
            .resolve("supervised" + File.separatorChar);
    final TrainingParameters params = TrainingParameters.defaultParams();
    params.put(TrainingParameters.THREADS_PARAM, 4);

    final SemcorReaderExtended sr = new SemcorReaderExtended(SEMCOR_DIR);
    final ObjectStream<WSDSample> samples = sr.getSemcorDataStream(WORD_TAG);

    try {
      WSDDefaultParameters wsdParams = new WSDDefaultParameters(trainingDir);
      trainedModel = WSDisambiguatorME.train("en", samples, params, wsdParams);
      assertNotNull(trainedModel);
      File modelFile = new File(wsdParams.getTrainingDataDirectory() +
              Character.toString(File.separatorChar) + WORD_TAG + ".wsd.model");
      try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
        trainedModel.serialize(modelOut);
      }
    } catch (IOException e1) {
      fail("Exception in training: " + e1.getMessage());
    }
  }

  @BeforeEach
  public void setup() {
    loader = new WSDModelLoader();
  }

  @ParameterizedTest(name = "Verify \"{0}\" is loading")
  @ValueSource(strings = {"pleased.a.wsd.model"})
  public void testLoadModelViaResource(String modelName) throws IOException {
    WSDModel model = loader.loadModel(Files.newInputStream(trainingDir.resolve(modelName)));
    assertNotNull(model);
    assertTrue(model.isLoadedFromSerialized());
    assertEquals(trainedModel, model);
  }
}
