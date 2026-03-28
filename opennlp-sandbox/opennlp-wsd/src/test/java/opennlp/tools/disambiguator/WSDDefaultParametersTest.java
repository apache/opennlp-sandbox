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
    WSDDefaultParameters params = WSDDefaultParameters.defaultParams();
    params.putIfAbsent(WSDDefaultParameters.TRAINING_DIR_PARAM, trainingDir.toAbsolutePath().toString());
    assertNotNull(params);
    assertInstanceOf(WSDParameters.class, params);
    assertTrue(params.areValid());

    assertEquals(WSDDefaultParameters.NGRAM_DEFAULT,
            params.getIntParameter(WSDDefaultParameters.NGRAM_PARAM, WSDDefaultParameters.NGRAM_DEFAULT));
    assertEquals(WSDDefaultParameters.WINDOW_SIZE_DEFAULT,
            params.getIntParameter(WSDDefaultParameters.WINDOW_SIZE_PARAM, WSDDefaultParameters.WINDOW_SIZE_DEFAULT));
    assertEquals(WSDDefaultParameters.LANG_CODE_DEFAULT,
            params.getStringParameter(WSDDefaultParameters.LANG_CODE, WSDDefaultParameters.LANG_CODE_DEFAULT));
    assertEquals(WSDDefaultParameters.SOURCE_DEFAULT,
            WSDParameters.SenseSource.valueOf(
                    params.getStringParameter(WSDDefaultParameters.SENSE_SOURCE_PARAM, WSDDefaultParameters.SOURCE_DEFAULT.name())));
  }
}
