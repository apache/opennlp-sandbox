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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.AbstractTest;
import opennlp.tools.disambiguator.datareader.SemcorReaderExtended;
import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is the test class for {@link WSDModel}.
 */
class WSDModelTest extends AbstractTest {

  private static final String WORD_TAG = "pleased.a";

  private static WSDModel trainedModel;
  private static Path trainingDir;

  @BeforeAll
  static void createSimpleWSDModel(@TempDir(cleanup = CleanupMode.ALWAYS) Path tmpDir) {
    Path workDir = tmpDir.resolve("models" + File.separatorChar);
    trainingDir = workDir.resolve("training" + File.separatorChar)
            .resolve("supervised" + File.separatorChar);
    File folder = trainingDir.toFile();
    if (!folder.exists()) {
      assertTrue(folder.mkdirs());
    }

    final TrainingParameters params = TrainingParameters.defaultParams();
    params.put(TrainingParameters.THREADS_PARAM, 4);
    final WSDDefaultParameters wsdParams = WSDDefaultParameters.defaultParams();
    wsdParams.putIfAbsent(WSDDefaultParameters.TRAINING_DIR_PARAM, trainingDir.toAbsolutePath().toString());

    final WSDisambiguatorFactory factory = new WSDisambiguatorFactory();
    final SemcorReaderExtended sr = new SemcorReaderExtended(SEMCOR_DIR);
    final ObjectStream<WSDSample> samples = sr.getSemcorDataStream(WORD_TAG);

    try {
      trainedModel = WSDisambiguatorME.train("en", samples, params, wsdParams, factory);
      assertNotNull(trainedModel);
    } catch (IOException e1) {
      fail("Exception in training: " + e1.getMessage());
    }
  }

  @Test
  void testWSDModelSerialization() throws IOException {
    assertFalse(trainedModel.isLoadedFromSerialized());
    assertNotNull(trainedModel.getContextEntries());

    try (ByteArrayOutputStream arrayOut = new ByteArrayOutputStream()) {
      trainedModel.serialize(arrayOut);
      WSDModel modelRestored = new WSDModel(new ByteArrayInputStream(arrayOut.toByteArray()));
      assertNotNull(modelRestored);
      assertTrue(modelRestored.isLoadedFromSerialized());
      assertEquals(WSDisambiguatorFactory.class, modelRestored.getDefaultFactory());

      // some extra checks
      assertEquals(trainedModel, modelRestored);
      assertEquals(trainedModel.hashCode(), modelRestored.hashCode());
      assertEquals(2, modelRestored.getNgram());
      assertEquals(3, modelRestored.getWindowSize());
      assertEquals("pleased.a", modelRestored.getWordTag());
      assertNotNull(modelRestored.getWSDMaxentModel());
      assertInstanceOf(GISModel.class, modelRestored.getWSDMaxentModel());
    }
  }

  @Test
  void testCreateWSDModelFromFileOrPathOrURL() {
    File modelFile = new File(trainingDir + WORD_TAG + ".wsd.model");
    modelFile.deleteOnExit();
    try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
      trainedModel.serialize(modelOut);
    } catch (IOException e) {
      fail(e.getMessage(), e);
    }

    // Test
    assertDoesNotThrow(() -> new WSDModel(modelFile));
    assertDoesNotThrow(() -> new WSDModel(modelFile.toPath()));
    assertDoesNotThrow(() -> new WSDModel(modelFile.toURI().toURL()));
  }

  @Test
  void testEqualsWithSame() {
    assertEquals(trainedModel, trainedModel); // on purpose!
  }

  @Test
  void testEqualsWithDifferentObject() {
    assertNotEquals(trainedModel, "foo"); // on purpose!
  }
  
  @Test
  void testHashCodeWithSame() {
    assertEquals(trainedModel.hashCode(), trainedModel.hashCode()); // on purpose!
  }

  @Test
  void testHashCodeWithDifferentObject() {
    assertNotEquals(trainedModel.hashCode(), "foo".hashCode()); // on purpose!
  }

  @Test
  void testGetMaxentModel() {
    final MaxentModel maxentModel = trainedModel.getWSDMaxentModel();
    assertNotNull(maxentModel);
  }

}
