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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import com.google.protobuf.Message;
import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.OutputFormatDescriptor;

/**
 * Catalog of {@link OutputFormatter} services for one reply message type, keyed by
 * format id. Formatters are discovered via {@link ServiceLoader}; the built-in binary
 * protobuf and protobuf JSON formatters ship with the server, and add-on jars
 * contribute further formats by registering their own formatter.
 *
 * @param <M> The reply message type this registry serves.
 */
public final class OutputFormatterRegistry<M extends Message> {

  private final Class<M> inputType;
  private final Map<String, OutputFormatter<M>> formattersById;

  private OutputFormatterRegistry(
      Class<M> inputType, Map<String, OutputFormatter<M>> formattersById) {
    this.inputType = inputType;
    // Map.copyOf would discard the sorted iteration order the descriptors rely on.
    this.formattersById = Collections.unmodifiableMap(new LinkedHashMap<>(formattersById));
  }

  /**
   * Discovers all registered formatters for one reply type.
   *
   * @param <M> The reply message type.
   * @param inputType The reply message type to serve. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no formatter for the type is deployed.
   *
   * @throws AnalysisException If two formatters declare the same format id for the
   *     type or a formatter declares an invalid id.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <M extends Message> OutputFormatterRegistry<M> discover(Class<M> inputType) {
    return create(inputType, (Iterable) ServiceLoader.load(OutputFormatter.class));
  }

  /**
   * Creates a registry from the given formatters instead of {@link ServiceLoader}
   * discovery; package-private so tests can drive the formatter set directly.
   *
   * @param <M> The reply message type.
   * @param inputType The reply message type to serve. Must not be {@code null}.
   * @param formatters The discovered formatters of every input type.
   *
   * @return A registry over the formatters matching {@code inputType}.
   *
   * @throws AnalysisException If two formatters declare the same format id for the
   *     type or a formatter declares an invalid id.
   */
  @SuppressWarnings("unchecked")
  static <M extends Message> OutputFormatterRegistry<M> create(
      Class<M> inputType, Iterable<? extends OutputFormatter<?>> formatters) {
    if (inputType == null) {
      throw new IllegalArgumentException("inputType must not be null");
    }
    final Map<String, OutputFormatter<M>> byId = new TreeMap<>();
    for (OutputFormatter<?> formatter : formatters) {
      if (!inputType.equals(formatter.inputType())) {
        continue;
      }
      final String id = formatter.formatId();
      if (id == null || id.isBlank() || !id.equals(StringUtil.toLowerCase(id))
          || id.chars().anyMatch(Character::isWhitespace)) {
        throw AnalysisException.invalidArgument(formatter.getClass().getName()
            + " declares an invalid format id '" + id
            + "'; format ids must be non-blank, lower-case, and contain no whitespace");
      }
      final OutputFormatter<M> duplicate =
          byId.putIfAbsent(id, (OutputFormatter<M>) formatter);
      if (duplicate != null) {
        throw AnalysisException.invalidArgument("Output format id '" + id
            + "' for " + inputType.getSimpleName() + " is declared by both "
            + duplicate.getClass().getName() + " and " + formatter.getClass().getName());
      }
    }
    return new OutputFormatterRegistry<>(inputType, byId);
  }

  /**
   * Lists the deployed formats in stable format-id order.
   *
   * @return One descriptor per deployed formatter, possibly empty.
   */
  public List<OutputFormatDescriptor> descriptors() {
    final List<OutputFormatDescriptor> descriptors = new ArrayList<>(formattersById.size());
    for (OutputFormatter<M> formatter : formattersById.values()) {
      descriptors.add(OutputFormatDescriptor.newBuilder()
          .setFormatId(formatter.formatId())
          .setDisplayName(formatter.displayName())
          .setMediaType(formatter.mediaType())
          .setFileExtension(formatter.fileExtension())
          .build());
    }
    return List.copyOf(descriptors);
  }

  /**
   * Resolves one deployed formatter by id.
   *
   * @param formatId The requested format id. Must not be blank.
   *
   * @return The formatter.
   *
   * @throws AnalysisException {@code INVALID_ARGUMENT} if the id is blank;
   *     {@code NOT_FOUND} if no deployed formatter serves the id, listing the
   *     available ids.
   */
  public OutputFormatter<M> require(String formatId) {
    if (formatId == null || formatId.isBlank()) {
      throw AnalysisException.invalidArgument("format_id must not be blank");
    }
    final OutputFormatter<M> formatter =
        formattersById.get(StringUtil.toLowerCase(formatId.trim()));
    if (formatter == null) {
      throw AnalysisException.notFound("Unknown output format '" + formatId
          + "' for " + inputType.getSimpleName() + "; available formats: "
          + formattersById.keySet()
          + ". Further formats arrive as formatter add-on jars on the classpath");
    }
    return formatter;
  }
}
