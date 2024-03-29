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

package org.apache.opennlp.namefinder;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

class PredictTest {

  // Note: As of Feb 2023, this test won't work on all platforms and, for instance, fails with
  //  "Cannot find TensorFlow native library for OS: darwin, architecture: aarch64"
  // That's why it is disabled via the architecture system property.
  // @DisabledIfSystemProperty(named = "os.arch", matches = "aarch64")
  @Test
  @Disabled
  // TODO This test won't work as the required TF model is missing and needs to be re-trained.
  //      Further details, see: https://github.com/apache/opennlp-sandbox/pull/89
  void testFindTokens() throws IOException {

    // can be changed to File or InputStream
    String words = PredictTest.class.getResource("/words.txt.gz").getPath();
    String chars = PredictTest.class.getResource("/chars.txt.gz").getPath();
    String tags = PredictTest.class.getResource("/tags.txt.gz").getPath();
    // Load model takes a String path!!
    Path model = Path.of("savedmodel");

    PredictionConfiguration config = new PredictionConfiguration(words, chars, tags, model.toString());

    try (SequenceTagging tagger = new SequenceTagging(config)) {
      String[] tokens = "Stormy Cars ' friend says she also plans to sue Michael Cohen .".split("\\s+");
      Span[] pred = tagger.find(tokens);

      for (int i = 0; i < tokens.length; i++) {
        System.out.print(tokens[i] + "/" + pred[i] + " ");
      }
      System.out.println();
    }

  }
}
