/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.coref;

import opennlp.tools.util.ObjectStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CorefTrainerTest extends AbstractCorefTest {

  private static String modelTrainingDir;

  @BeforeAll
  public static void initEnv() throws IOException, URISyntaxException {
    final URL modelInputDirectory = CorefTrainerTest.class.getResource(AbstractCorefTest.MODEL_DIR);
    URL modelTrainingDirectory = CorefTrainerTest.class.getResource(AbstractCorefTest.MODEL_TRAINING_DIR);
    assertNotNull(modelInputDirectory);
    assertNotNull(modelTrainingDirectory);
    modelTrainingDir = Path.of(modelTrainingDirectory.toURI()).toAbsolutePath().toString();
    // transfer resources to the training directory
    modelTrainingDirectory = CorefTrainerTest.class.getResource(AbstractCorefTest.MODEL_TRAINING_DIR + "/coref/");
    Path pOriginal = Paths.get(modelInputDirectory.toURI());
    Path pTraining = Paths.get(modelTrainingDirectory.toURI());

    Files.copy(pOriginal.resolve("gen.fem").toAbsolutePath(),
            pTraining.resolve("gen.fem").toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    Files.copy(pOriginal.resolve("gen.mas").toAbsolutePath(),
            pTraining.resolve("gen.mas").toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    Files.copy(pOriginal.resolve("acronyms").toAbsolutePath(),
            pTraining.resolve("acronyms").toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
  }

  @Disabled
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testTrainByTreebank(Boolean withTreebank) throws IOException {
    CorefSampleStreamFactory streamFactory = new CorefSampleStreamFactory();
    String[] args = new String[]{"-data",
            CorefTrainerTest.class.getResource("/models/training/coref/training-test.txt").getPath()};
    ObjectStream<CorefSample> samples = streamFactory.create(args);
    CorefTrainer.train(modelTrainingDir, samples, withTreebank, false);
  }
}
