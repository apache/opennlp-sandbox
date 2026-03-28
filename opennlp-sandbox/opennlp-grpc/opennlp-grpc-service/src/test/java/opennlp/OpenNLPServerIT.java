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
package opennlp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.service.stubs.TestStreamObserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenNLPServerIT {

  private static OpenNLPServer server;
  private static ExecutorService executor;
  private static ManagedChannel channel;

  @BeforeAll
  static void init() throws IOException, URISyntaxException {
    int port;
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      port = serverSocket.getLocalPort(); // Automatically assigns a free port
    }
    assertTrue(port > 0);

    server = new OpenNLPServer();
    server.port = port;
    server.config = getConfig().toString();

    executor = Executors.newSingleThreadExecutor();
    executor.submit(() -> {
      try {
        server.start();
      } catch (Exception e) {
        throw new RuntimeException("Failed to start the server", e);
      }
    });

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> server.isReady());

    channel = ManagedChannelBuilder.forAddress("localhost", server.port)
        .usePlaintext()
        .build();
  }

  @AfterAll
  static void tearDown() {
    if (server != null) {
      server.stop();
    }
    if (executor != null) {
      executor.shutdownNow();
    }

    if (channel != null) {
      channel.shutdown();
    }
  }

  private static Path getConfig() throws URISyntaxException {
    return Paths.get(
        Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("config-test.ini"))
            .toURI()
    ).toAbsolutePath();
  }

  @Test
  void testTagging() {
    assertFalse(channel.isTerminated(), "The server should be running and the channel open.");
    final String hash = "5af913a52fa0b014e22c4c4411e146720f1222bdebde9ce1f1a3174df974d26d";

    final String[] sentence = new String[] {"The", "driver", "got", "badly", "injured", "by", "the", "accident", "."};
    final String[] expected = new String[] {"DET", "NOUN", "VERB", "ADV", "VERB", "ADP", "DET", "NOUN", "PUNCT"};

    PosTaggerServiceGrpc.PosTaggerServiceStub service = PosTaggerServiceGrpc.newStub(channel);

    service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {
      @Override
      public void onNext(OpenNLPService.AvailableModels t) {
        assertNotNull(t);
        assertEquals(1, t.getModelsCount());
        OpenNLPService.Model m = t.getModels(0);
        assertNotNull(m);
        assertEquals("opennlp-en-ud-ewt-pos-1.3-2.5.4.bin", m.getName());
        assertEquals(hash, m.getHash());
      }
    });

    //simulate a tagging session
    service.tagWithContext(OpenNLPService.TagWithContextRequest.newBuilder()
        .addAllAdditionalContext(List.of("test"))
        .setModelHash(hash).addAllSentence(Arrays.stream(sentence).toList()).build(), new TestStreamObserver<>() {
      @Override
      public void onNext(opennlp.OpenNLPService.StringList t) {
        assertNotNull(t);
        assertArrayEquals(expected, t.getValuesList().toArray(new String[0]));
      }
    });
  }

  @Test
  void testTokenize() {
    assertFalse(channel.isTerminated(), "The server should be running and the channel open.");

    final String sentence = "The driver got badly injured by the accident.";
    final String[] expected = new String[] {"The", "driver", "got", "badly", "injured", "by", "the", "accident", "."};

    final String hash = "9e7e4149010a56417e0236c15daab01cb7543f6089a8f0e85a53dc1183a9b1d3";

    TokenizerTaggerServiceGrpc.TokenizerTaggerServiceStub service = TokenizerTaggerServiceGrpc.newStub(channel);

    service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {
      @Override
      public void onNext(OpenNLPService.AvailableModels t) {
        assertNotNull(t);
        assertEquals(1, t.getModelsCount());
        OpenNLPService.Model m = t.getModels(0);
        assertNotNull(m);
        assertEquals("opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin", m.getName());
        assertEquals(hash, m.getHash());
      }
    });

    //simulate a tagging session
    service.tokenize(OpenNLPService.TokenizeRequest.newBuilder()
        .setSentence(sentence)
        .setModelHash(hash).build(), new TestStreamObserver<>() {
      @Override
      public void onNext(OpenNLPService.StringList t) {
        assertNotNull(t);
        assertArrayEquals(expected, t.getValuesList().toArray(new String[0]));
      }
    });

  }

  @Test
  void testSentenceDetection() {
    assertFalse(channel.isTerminated(), "The server should be running and the channel open.");

    final String sentence = "The driver got badly injured by the accident. He was taken to the hospital!";
    final String[] expected = new String[] {"The driver got badly injured by the accident.", "He was taken to the hospital!"};

    final String hash = "3735a5ae356c72ca028c93efcf4022f1d3a1c474337a379c474f07de990dd38b";

    SentenceDetectorServiceGrpc.SentenceDetectorServiceStub service = SentenceDetectorServiceGrpc.newStub(channel);

    service.getAvailableModels(OpenNLPService.Empty.newBuilder().build(), new TestStreamObserver<>() {
      @Override
      public void onNext(OpenNLPService.AvailableModels t) {
        assertNotNull(t);
        assertEquals(1, t.getModelsCount());
        OpenNLPService.Model m = t.getModels(0);
        assertNotNull(m);
        assertEquals("opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin", m.getName());
        assertEquals(hash, m.getHash());
      }
    });

    service.sentDetect(OpenNLPService.SentDetectRequest.newBuilder()
        .setSentence(sentence)
        .setModelHash(hash).build(), new TestStreamObserver<>() {
      @Override
      public void onNext(OpenNLPService.StringList t) {
        assertNotNull(t);
        assertArrayEquals(expected, t.getValuesList().toArray(new String[0]));
      }
    });

  }
}
