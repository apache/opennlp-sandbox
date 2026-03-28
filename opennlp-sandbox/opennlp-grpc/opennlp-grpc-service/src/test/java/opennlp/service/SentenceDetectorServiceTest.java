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

class SentenceDetectorServiceTest extends AbstractServiceTest {

  private static final String MODEL_NAME = "opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin";
  private static final String MODEL_HASH = "4de58d12b09421003c8b1ba13e8b58ec533b28e1f22a65272119dc0993d333e8";

  static final String SENTENCE = "The driver got badly injured by the accident. He was taken to the hospital!";
  static final String[] EXPECTED = new String[] {"The driver got badly injured by the accident.", "He was taken to the hospital!"};

  @Test
  public void testGetAvailableModels() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final SentenceDetectorService service = new SentenceDetectorService(Map.of("model.location", modelPath.toString()))) {

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
  public void testSentenceDetection() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final SentenceDetectorService service = new SentenceDetectorService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-models-sentdetect-en-*.jar",
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

      service.sentDetect(OpenNLPService.SentDetectRequest.newBuilder()
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
  public void testPosSentDetect() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final SentenceDetectorService service = new SentenceDetectorService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-models-sentdetect-en-*.jar",
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

     service.sentPosDetect(OpenNLPService.SentDetectPosRequest.newBuilder()
          .setModelHash(MODEL_HASH)
          .setSentence(SENTENCE)
          .build(), new TestStreamObserver<>() {
        @Override
        public void onNext(OpenNLPService.SpanList t) {
          assertNotNull(t);
          assertEquals(2, t.getValuesCount());
        }
      });
    }
  }
}
