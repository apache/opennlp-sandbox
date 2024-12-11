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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WSDDefaultParametersTest {

  private static Path trainingDir;

  @BeforeAll
  static void initEnv(@TempDir(cleanup = CleanupMode.ALWAYS) Path tmpDir) {
    Path workDir = tmpDir.resolve("models" + File.separatorChar);
    trainingDir = workDir.resolve("training" + File.separatorChar)
            .resolve("supervised" + File.separatorChar);
  }

  @Test
  void testCreate() {
    WSDDefaultParameters params = new WSDDefaultParameters(trainingDir);
    assertNotNull(params);
    assertInstanceOf(WSDParameters.class, params);
    assertTrue(params.areValid());
    assertEquals(trainingDir, params.getTrainingDataDirectory());

    assertEquals(WSDDefaultParameters.DFLT_NGRAM, params.getNgram());
    assertEquals(WSDDefaultParameters.DFLT_WIN_SIZE, params.getWindowSize());
    assertEquals(WSDDefaultParameters.DFLT_LANG_CODE, params.getLanguageCode());
    assertEquals(WSDDefaultParameters.DFLT_SOURCE, params.getSenseSource());
  }
}
