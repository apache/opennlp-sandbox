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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNlpGrpcServerIT {

  private static final String SENTENCE_MODEL_PREFIX = "opennlp-en-ud-ewt-sentence-";
  private static final String TOKENIZER_MODEL_PREFIX = "opennlp-en-ud-ewt-tokens-";

  private static OpenNlpGrpcServer server;
  private static ManagedChannel channel;

  @BeforeAll
  static void init() throws Exception {
    server = new OpenNlpGrpcServer();
    server.port = 0;
    server.config = writeIntegrationConfig().toString();
    server.start();

    channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
        .usePlaintext()
        .build();
  }

  @AfterAll
  static void tearDown() {
    if (server != null) {
      server.stop();
    }
    if (channel != null) {
      channel.shutdown();
    }
  }

  /**
   * Writes a server config with explicit model paths. Integration tests must not rely on
   * {@code DefaultClassPathModelProvider} classpath scanning, which is environment-dependent
   * across operating systems and Maven classloader layouts.
   */
  private static Path writeIntegrationConfig() throws IOException, URISyntaxException {
    final Path modelsDir = Paths.get(
        Objects.requireNonNull(OpenNlpGrpcServerIT.class.getResource("/models/")).toURI());
    final Path sentenceModel = requireModelFile(modelsDir, SENTENCE_MODEL_PREFIX);
    final Path tokenizerModel = requireModelFile(modelsDir, TOKENIZER_MODEL_PREFIX);

    final Properties properties = new Properties();
    properties.setProperty("server.enable_reflection", "false");
    properties.setProperty("server.max_inbound_message_size", "10485760");
    properties.setProperty("model.sentence_detector.path", sentenceModel.toAbsolutePath().toString());
    properties.setProperty("model.tokenizer.path", tokenizerModel.toAbsolutePath().toString());

    final Path config = Files.createTempFile("opennlp-grpc-it-", ".ini");
    config.toFile().deleteOnExit();
    // The server parses the config with Properties.load, so the file must be written with
    // Properties.store: it escapes backslashes, which would otherwise corrupt Windows paths.
    try (OutputStream out = Files.newOutputStream(config)) {
      properties.store(out, null);
    }
    return config;
  }

  private static Path requireModelFile(Path modelsDir, String prefix) throws IOException {
    try (Stream<Path> files = Files.list(modelsDir)) {
      return files.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(prefix))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Expected a model file with prefix '" + prefix + "' under " + modelsDir));
    }
  }

  @Test
  void analyzeDocumentOverGrpc() {
    assertFalse(channel.isTerminated());

    final String text =
        "The driver got badly injured by the accident. He was taken to the hospital!";

    final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub v1 =
        OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);

    final var serviceInfo = v1.getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
    assertEquals("v1", serviceInfo.getApiVersion());
    assertTrue(serviceInfo.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(serviceInfo.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_TOKENIZE));

    final var response = v1.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("it-doc-1")
            .setRawText(text)
            .build())
        .build());

    assertEquals("it-doc-1", response.getDocument().getDocId());
    assertEquals(2, response.getDocument().getSentencesCount());
    assertFalse(response.getDocument().getSentences(0).getTokensList().isEmpty());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
  }
}
