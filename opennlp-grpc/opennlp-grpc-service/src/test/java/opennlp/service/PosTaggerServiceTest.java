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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.OpenNLPService;
import opennlp.service.stubs.TestStreamObserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PosTaggerServiceTest extends AbstractServiceTest {

  public static final String[] SENTENCE = new String[] {"The", "driver", "got", "badly", "injured", "by", "the", "accident", "."};
  public static final String[] EXPECTED = new String[] {"DET", "NOUN", "VERB", "ADV", "VERB", "ADP", "DET", "NOUN", "PUNCT"};

  @Test
  public void testGetAvailableModels() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final PosTaggerService taggerService = new PosTaggerService(Map.of("model.location", modelPath.toString()))) {

      taggerService.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(2, t.getModelsCount());
        }
      });

    }
  }

  @Test
  public void testGetAvailableModelsCustomPattern() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final PosTaggerService taggerService = new PosTaggerService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-pos-*.jar",
            "model.model.recursive", "true"
        ))) {

      taggerService.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals("opennlp-de-test2.bin", m.getName());
        }
      });

    }
  }

  @Test
  public void testGetAvailableModelsCustomPatternNotRecursive() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final PosTaggerService taggerService = new PosTaggerService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-pos-*.jar",
            "model.recursive", "false"
        ))) {

      taggerService.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(0, t.getModelsCount());
        }
      });

    }
  }

  @Test
  public void testDoTagging() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final PosTaggerService taggerService = new PosTaggerService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-models-pos-en-*.jar",
            "model.recursive", "true"
        ))) {

      final String hash = "5af913a52fa0b014e22c4c4411e146720f1222bdebde9ce1f1a3174df974d26d";

      //check if we have the EN tagger available
      taggerService.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals("opennlp-en-ud-ewt-pos-1.2-2.5.0.bin", m.getName());
          assertEquals(hash, m.getHash());
        }
      });

      //simulate a tagging session
      taggerService.tag(OpenNLPService.TagRequest.newBuilder()
          .setModelHash(hash)
          .addAllSentence(Arrays.stream(SENTENCE).toList())
          .build(), new TestStreamObserver<>() {
        @Override
        public void onNext(opennlp.OpenNLPService.StringList t) {
          assertNotNull(t);
          assertArrayEquals(EXPECTED, t.getValuesList().toArray(new String[0]));
        }
      });


    }
  }

  @Test
  public void testDoTaggingWithContext() throws URISyntaxException {
    final Path modelPath = getModelDirectory();

    try (final PosTaggerService taggerService = new PosTaggerService(
        Map.of(
            "model.location", modelPath.toString(),
            "model.pos.wildcard.pattern", "opennlp-models-pos-en-*.jar",
            "model.recursive", "true"
        ))) {

      final String hash = "5af913a52fa0b014e22c4c4411e146720f1222bdebde9ce1f1a3174df974d26d";

      //check if we have the EN tagger available
      taggerService.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {

        @Override
        public void onNext(OpenNLPService.AvailableModels t) {
          assertNotNull(t);
          assertEquals(1, t.getModelsCount());
          OpenNLPService.Model m = t.getModels(0);
          assertNotNull(m);
          assertEquals("opennlp-en-ud-ewt-pos-1.2-2.5.0.bin", m.getName());
          assertEquals(hash, m.getHash());
        }
      });

      //simulate a tagging session
      taggerService.tagWithContext(OpenNLPService.TagWithContextRequest.newBuilder()
          .addAllAdditionalContext(List.of("test"))
          .setModelHash(hash).addAllSentence(Arrays.stream(SENTENCE).toList()).build(), new TestStreamObserver<>() {
        @Override
        public void onNext(opennlp.OpenNLPService.StringList t) {
          assertNotNull(t);
          assertArrayEquals(EXPECTED, t.getValuesList().toArray(new String[0]));
        }
      });

    }
  }

}
