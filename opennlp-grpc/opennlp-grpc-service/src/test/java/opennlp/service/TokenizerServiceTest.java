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
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.OpenNLPService;
import opennlp.service.stubs.TestStreamObserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TokenizerServiceTest extends AbstractServiceTest {

  public static final String SENTENCE = "The driver got badly injured by the accident.";
  public static final String[] EXPECTED = new String[] {"The", "driver", "got", "badly", "injured", "by", "the", "accident", "."};

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

      final String hash = "9e7e4149010a56417e0236c15daab01cb7543f6089a8f0e85a53dc1183a9b1d3";

      service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals("opennlp-en-ud-ewt-tokens-1.2-2.5.0.bin", m.getName());
          assertEquals(hash, m.getHash());
        }
      });

      service.tokenize(OpenNLPService.TokenizeRequest.newBuilder()
          .setModelHash(hash)
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

      final String hash = "9e7e4149010a56417e0236c15daab01cb7543f6089a8f0e85a53dc1183a9b1d3";

      service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals("opennlp-en-ud-ewt-tokens-1.2-2.5.0.bin", m.getName());
          assertEquals(hash, m.getHash());
        }
      });

     service.tokenizePos(OpenNLPService.TokenizePosRequest.newBuilder()
          .setModelHash(hash)
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
