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
package opennlp.service;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.OpenNLPService;
import opennlp.service.stubs.TestStreamObserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TokenizerServiceTest extends AbstractServiceTest {

  private static final String MODEL_NAME = "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin";
  private static final String MODEL_HASH = "95139111197ba9f1165fc12be8d4cbac05ea3912f995d167efa1a0e04e3e649a";

  static final String SENTENCE = "The driver got badly injured by the accident.";
  static final String[] EXPECTED = new String[] {"The", "driver", "got", "badly", "injured", "by", "the", "accident", "."};

  @Test
  public void testGetAvailableModels() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final TokenizerService service = new TokenizerService(Map.of("model.location", modelPath.toString()))) {

      service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
        }
      });

    }
  }

  @Test
  public void testDoTokenize() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final TokenizerService service = new TokenizerService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-models-tokenizer-en-*.jar",
            "model.recursive", "true"
        ))) {

      service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals(MODEL_NAME, m.getName());
          assertEquals(MODEL_HASH, m.getHash());
        }
      });

      service.tokenize(OpenNLPService.TokenizeRequest.newBuilder()
          .setModelHash(MODEL_HASH)
          .setSentence(SENTENCE)
          .build(), new TestStreamObserver<>() {
        @Override
        public void onNext(OpenNLPService.StringList t) {
          assertNotNull(t);
          assertArrayEquals(EXPECTED, t.getValuesList().toArray(new String[0]));
        }
      });
    }
  }

  @Test
  public void testDoTokenizePos() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final TokenizerService service = new TokenizerService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-models-tokenizer-en-*.jar",
            "model.recursive", "true"
        ))) {

      service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals(MODEL_NAME, m.getName());
          assertEquals(MODEL_HASH, m.getHash());
        }
      });

     service.tokenizePos(OpenNLPService.TokenizePosRequest.newBuilder()
          .setModelHash(MODEL_HASH)
          .setSentence(SENTENCE)
          .build(), new TestStreamObserver<>() {
        @Override
        public void onNext(OpenNLPService.SpanList t) {
          assertNotNull(t);
          assertEquals(9, t.getValuesCount());
        }
      });
    }
  }
}
