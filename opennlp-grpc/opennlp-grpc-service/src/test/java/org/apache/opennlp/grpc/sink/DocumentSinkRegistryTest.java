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
package org.apache.opennlp.grpc.sink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.sink.DocumentSink;
import org.apache.opennlp.grpc.spi.sink.DocumentSinkProvider;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests sink discovery, tee delivery with failure isolation, and the analyzer tee. */
class DocumentSinkRegistryTest {

  /** Recording sink that can be told to fail. */
  private static final class RecordingSink implements DocumentSink {

    private final List<String> accepted = new ArrayList<>();
    private final boolean failing;
    private boolean closed;

    private RecordingSink(boolean failing) {
      this.failing = failing;
    }

    @Override
    public void accept(OpenNlpDocument document) throws IOException {
      if (failing) {
        throw new IOException("downstream unavailable");
      }
      accepted.add(document.getDocId());
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /** Provider handing out one prepared sink under a fixed id. */
  private record FakeProvider(String sinkId, DocumentSink sink)
      implements DocumentSinkProvider {

    @Override
    public DocumentSink open(String instanceId, Map<String, String> options) {
      return sink;
    }
  }

  private static OpenNlpDocument document(String docId) {
    return OpenNlpDocument.newBuilder().setDocId(docId).build();
  }

  @Test
  void noConfiguredSinksYieldsAnEmptyRegistry() throws IOException {
    final DocumentSinkRegistry registry =
        DocumentSinkRegistry.fromConfiguration(Map.of("server.port", "7071"));
    assertTrue(registry.isEmpty());
  }

  @Test
  void teesEveryDocumentIntoEveryOpenSinkAndIsolatesFailures() throws IOException {
    final RecordingSink healthy = new RecordingSink(false);
    final DocumentSinkRegistry registry = DocumentSinkRegistry.create(
        Map.of("sink.broken.provider", "failing", "sink.healthy.provider", "recording"),
        List.of(new FakeProvider("recording", healthy),
            new FakeProvider("failing", new RecordingSink(true))));

    registry.tee(document("doc-1"));
    registry.tee(document("doc-2"));

    assertEquals(List.of("doc-1", "doc-2"), healthy.accepted);
    assertEquals(List.of("broken", "healthy"), registry.instanceIds());
  }

  @Test
  void unknownProviderFailsLoudListingTheAvailableIds() {
    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> DocumentSinkRegistry.create(
            Map.of("sink.down.provider", "kafka"),
            List.of(new FakeProvider("recording", new RecordingSink(false)))));
    assertTrue(failure.getMessage().contains("kafka"));
    assertTrue(failure.getMessage().contains("recording"));
    assertTrue(failure.getMessage().contains("add-on"));
  }

  @Test
  void missingProviderOptionFailsLoud() {
    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> DocumentSinkRegistry.create(
            Map.of("sink.down.target", "localhost:9"), List.of()));
    assertTrue(failure.getMessage().contains("sink.down.provider"));
  }

  @Test
  void duplicateSinkIdsAcrossProvidersFailLoud() {
    assertThrows(IllegalArgumentException.class, () -> DocumentSinkRegistry.create(
        Map.of(),
        List.of(new FakeProvider("grpc", new RecordingSink(false)),
            new FakeProvider("grpc", new RecordingSink(false)))));
  }

  @Test
  void closeClosesEveryOpenSink() throws IOException {
    final RecordingSink sink = new RecordingSink(false);
    final DocumentSinkRegistry registry = DocumentSinkRegistry.create(
        Map.of("sink.down.provider", "recording"),
        List.of(new FakeProvider("recording", sink)));

    registry.close();

    assertTrue(sink.closed);
  }

  @Test
  void theAnalyzerDecoratorTeesAnalyzeAndSessionResults() throws IOException {
    final RecordingSink sink = new RecordingSink(false);
    final DocumentSinkRegistry registry = DocumentSinkRegistry.create(
        Map.of("sink.down.provider", "recording"),
        List.of(new FakeProvider("recording", sink)));
    final SinkTeeingDocumentAnalyzer analyzer = new SinkTeeingDocumentAnalyzer(
        request -> AnalyzeDocumentResponse.newBuilder()
            .setDocument(request.getDocument()).build(),
        registry);

    analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(document("direct")).build());
    analyzer.openSession(AnalyzeStreamConfiguration.getDefaultInstance())
        .analyze(document("streamed"));

    assertEquals(List.of("direct", "streamed"), sink.accepted);
  }
}
