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

package opennlp.tools.coref.linker;

import opennlp.tools.coref.AbstractCorefTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultLinkerModelTrainingTest extends AbstractCorefTest {

  private static String modelTrainingDir;

  @BeforeAll
  public static void initEnv() throws IOException, URISyntaxException {
    final URL modelInputDirectory = DefaultLinkerModelTrainingTest.class.getResource(AbstractCorefTest.MODEL_DIR);
    final URL modelTrainingDirectory = DefaultLinkerModelTrainingTest.class.getResource(AbstractCorefTest.MODEL_TRAINING_DIR);
    assertNotNull(modelInputDirectory);
    assertNotNull(modelTrainingDirectory);
    modelTrainingDir = Path.of(modelTrainingDirectory.toURI()).toAbsolutePath().toString();
    // transfer resources to the training directory
    Path pOriginal = Paths.get(modelInputDirectory.toURI());
    Path pTraining = Paths.get(modelTrainingDirectory.toURI());

    Files.copy(pOriginal.resolve("gen.fem").toAbsolutePath(),
            pTraining.resolve("gen.fem").toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    Files.copy(pOriginal.resolve("gen.mas").toAbsolutePath(),
            pTraining.resolve("gen.mas").toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
    Files.copy(pOriginal.resolve("acronyms").toAbsolutePath(),
            pTraining.resolve("acronyms").toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
  }

  @Test
  void testModelTraining() throws IOException {
    DefaultLinker dnr = new DefaultLinker(modelTrainingDir, LinkerMode.TRAIN, false);
    dnr.train();
  }
}
