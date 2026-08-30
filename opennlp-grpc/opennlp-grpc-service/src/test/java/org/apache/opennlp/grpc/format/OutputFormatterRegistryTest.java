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
package org.apache.opennlp.grpc.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.protobuf.Message;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the ServiceLoader aggregation and resolution of output formatters. */
class OutputFormatterRegistryTest {

  /** Minimal fake formatter with a settable id and input type. */
  private record Fake<M extends Message>(Class<M> inputType, String formatId)
      implements OutputFormatter<M> {

    @Override
    public String displayName() {
      return formatId;
    }

    @Override
    public String mediaType() {
      return "text/plain";
    }

    @Override
    public String fileExtension() {
      return "txt";
    }

    @Override
    public void format(M reply, OutputStream output) throws IOException {
      output.write(formatId.getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  void discoversTheBuiltInDocumentFormatters() {
    assertEquals(List.of("proto", "protojson", "tsv"),
        OutputFormatterRegistry.discover(OpenNlpDocument.class).descriptors().stream()
            .map(descriptor -> descriptor.getFormatId()).toList());
  }

  @Test
  void groupsFormattersByInputType() {
    final OutputFormatterRegistry<OpenNlpDocument> registry = OutputFormatterRegistry.create(
        OpenNlpDocument.class, List.of(
            new Fake<>(OpenNlpDocument.class, "doc"),
            new Fake<>(AnalyzeDocumentResponse.class, "reply")));

    assertEquals(1, registry.descriptors().size());
    assertEquals("doc", registry.descriptors().getFirst().getFormatId());
  }

  @Test
  void rejectsDuplicateFormatIdsForOneInputType() {
    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> OutputFormatterRegistry.create(OpenNlpDocument.class, List.of(
            new Fake<>(OpenNlpDocument.class, "twice"),
            new Fake<>(OpenNlpDocument.class, "twice"))));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, failure.getFailureType());
    assertTrue(failure.getMessage().contains("twice"));
  }

  @Test
  void rejectsInvalidFormatIds() {
    for (String id : List.of("", " ", "Upper", "with space")) {
      assertThrows(AnalysisException.class, () -> OutputFormatterRegistry.create(
          OpenNlpDocument.class, List.of(new Fake<>(OpenNlpDocument.class, id))));
    }
  }

  @Test
  void unknownFormatFailsLoudListingTheAvailableIds() {
    final OutputFormatterRegistry<OpenNlpDocument> registry =
        OutputFormatterRegistry.discover(OpenNlpDocument.class);

    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> registry.require("conllu-missing"));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, failure.getFailureType());
    assertTrue(failure.getMessage().contains("proto"));
    assertTrue(failure.getMessage().contains("add-on"));
    assertThrows(AnalysisException.class, () -> registry.require(" "));
  }

  @Test
  void resolvesIdsCaseInsensitively() {
    assertEquals("tsv", OutputFormatterRegistry.discover(OpenNlpDocument.class)
        .require(" TSV ").formatId());
  }
}
