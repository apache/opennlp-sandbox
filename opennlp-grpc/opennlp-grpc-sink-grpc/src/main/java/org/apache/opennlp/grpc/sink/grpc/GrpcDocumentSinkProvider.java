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
package org.apache.opennlp.grpc.sink.grpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.spi.sink.DocumentSink;
import org.apache.opennlp.grpc.spi.sink.DocumentSinkProvider;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * Serves {@code sink.<instance>.provider=grpc} entries: every analyzed document streams
 * to the downstream {@code OpenNlpDocumentSinkService} receiver at
 * {@code sink.<instance>.target}, which any protobuf language can implement. The
 * optional {@code sink.<instance>.format} option names a deployed output formatter
 * whose rendering rides along on every item.
 *
 * <p>The channel uses plaintext: like the JSON gateway, the sink carries no credentials,
 * so targets belong on loopback or a trusted network.</p>
 */
public final class GrpcDocumentSinkProvider implements DocumentSinkProvider {

  /** Option naming the receiver's {@code host:port} target. */
  static final String TARGET_OPTION = "target";

  /** Option naming a deployed output format to attach to every item. */
  static final String FORMAT_OPTION = "format";

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public GrpcDocumentSinkProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public String sinkId() {
    return "grpc";
  }

  /** {@inheritDoc} */
  @Override
  public DocumentSink open(String instanceId, Map<String, String> options) {
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    String target = null;
    OutputFormatter<OpenNlpDocument> formatter = null;
    for (Map.Entry<String, String> option : options.entrySet()) {
      switch (option.getKey()) {
        case TARGET_OPTION -> target = option.getValue();
        case FORMAT_OPTION -> formatter = requireFormatter(instanceId, option.getValue());
        default -> throw new IllegalArgumentException("sink." + instanceId
            + " does not support option '" + option.getKey() + "'");
      }
    }
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("sink." + instanceId + "." + TARGET_OPTION
          + " must name the receiver's host:port");
    }
    final ManagedChannel channel =
        ManagedChannelBuilder.forTarget(target.trim()).usePlaintext().build();
    return new GrpcDocumentSink(instanceId, channel, channel, formatter);
  }

  /**
   * Resolves one deployed document formatter by format id.
   *
   * @param instanceId The configured instance id, for the error message.
   * @param formatId The requested format id.
   *
   * @return The formatter.
   * @throws IllegalArgumentException If no deployed formatter serves the id.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static OutputFormatter<OpenNlpDocument> requireFormatter(
      String instanceId, String formatId) {
    final List<String> available = new ArrayList<>();
    for (OutputFormatter formatter : ServiceLoader.load(OutputFormatter.class)) {
      if (!OpenNlpDocument.class.equals(formatter.inputType())) {
        continue;
      }
      if (formatter.formatId().equals(formatId)) {
        return formatter;
      }
      available.add(formatter.formatId());
    }
    available.sort(null);
    throw new IllegalArgumentException("sink." + instanceId + "." + FORMAT_OPTION
        + " names unknown output format '" + formatId + "'; available formats: " + available);
  }
}
