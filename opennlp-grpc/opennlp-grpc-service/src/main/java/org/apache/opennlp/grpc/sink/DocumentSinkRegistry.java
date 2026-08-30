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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.sink.DocumentSink;
import org.apache.opennlp.grpc.spi.sink.DocumentSinkProvider;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The open document sinks of one server, built from {@code sink.<instance>.provider}
 * configuration entries over {@link DocumentSinkProvider} services discovered via
 * {@link ServiceLoader}. Every analyzed document is teed into every open sink; a sink
 * failure is logged and isolated, never failing the analysis or the other sinks.
 */
public final class DocumentSinkRegistry implements AutoCloseable {

  /** Prefix of one sink instance configuration entry: {@code sink.<instance>.<option>}. */
  static final String KEY_PREFIX = "sink.";

  /** Option selecting the provider of one sink instance. */
  static final String PROVIDER_OPTION = "provider";

  private static final Logger logger = LoggerFactory.getLogger(DocumentSinkRegistry.class);

  private final Map<String, DocumentSink> sinksByInstance;

  private DocumentSinkRegistry(Map<String, DocumentSink> sinksByInstance) {
    this.sinksByInstance = sinksByInstance;
  }

  /**
   * Opens every configured sink instance over the discovered providers.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, empty when no sink is configured.
   *
   * @throws IOException If a configured sink cannot be opened.
   * @throws IllegalArgumentException If an instance names an unknown provider, omits
   *     the provider option, or two providers declare the same sink id.
   */
  public static DocumentSinkRegistry fromConfiguration(Map<String, String> configuration)
      throws IOException {
    return create(configuration, ServiceLoader.load(DocumentSinkProvider.class));
  }

  /**
   * Opens sinks from the given providers instead of {@link ServiceLoader} discovery;
   * package-private so tests can drive the provider set directly.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param providers The discovered sink providers.
   *
   * @return A registry, empty when no sink is configured.
   *
   * @throws IOException If a configured sink cannot be opened.
   * @throws IllegalArgumentException If an instance names an unknown provider, omits
   *     the provider option, or two providers declare the same sink id.
   */
  static DocumentSinkRegistry create(
      Map<String, String> configuration, Iterable<DocumentSinkProvider> providers)
      throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final Map<String, DocumentSinkProvider> providersById = new TreeMap<>();
    for (DocumentSinkProvider provider : providers) {
      final DocumentSinkProvider duplicate =
          providersById.putIfAbsent(provider.sinkId(), provider);
      if (duplicate != null) {
        throw new IllegalArgumentException("Sink id '" + provider.sinkId()
            + "' is declared by both " + duplicate.getClass().getName()
            + " and " + provider.getClass().getName());
      }
    }
    final Map<String, Map<String, String>> optionsByInstance = new TreeMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX)) {
        continue;
      }
      final String remainder = key.substring(KEY_PREFIX.length());
      final int dot = remainder.indexOf('.');
      if (dot <= 0 || dot == remainder.length() - 1) {
        throw new IllegalArgumentException("Invalid sink configuration key: " + key);
      }
      optionsByInstance
          .computeIfAbsent(remainder.substring(0, dot), ignored -> new LinkedHashMap<>())
          .put(remainder.substring(dot + 1), entry.getValue());
    }
    final Map<String, DocumentSink> sinks = new LinkedHashMap<>();
    try {
      for (Map.Entry<String, Map<String, String>> instance : optionsByInstance.entrySet()) {
        final Map<String, String> options = new LinkedHashMap<>(instance.getValue());
        final String sinkId = options.remove(PROVIDER_OPTION);
        if (sinkId == null || sinkId.isBlank()) {
          throw new IllegalArgumentException("sink." + instance.getKey() + "."
              + PROVIDER_OPTION + " must name a sink provider");
        }
        final DocumentSinkProvider provider =
            providersById.get(StringUtil.toLowerCase(sinkId.trim()));
        if (provider == null) {
          throw new IllegalArgumentException("sink." + instance.getKey()
              + " names unknown provider '" + sinkId + "'; available providers: "
              + providersById.keySet()
              + ". Further providers arrive as sink add-on jars on the classpath");
        }
        sinks.put(instance.getKey(), provider.open(instance.getKey(), options));
      }
    } catch (IOException | RuntimeException e) {
      closeAll(sinks);
      throw e;
    }
    return new DocumentSinkRegistry(sinks);
  }

  /**
   * Reports whether any sink is open.
   *
   * @return {@code true} when at least one sink instance is configured.
   */
  public boolean isEmpty() {
    return sinksByInstance.isEmpty();
  }

  /**
   * Delivers one analyzed document to every open sink. A failing sink is logged and
   * skipped; delivery to the remaining sinks continues and the caller never observes
   * the failure.
   *
   * @param document The analyzed document. Must not be {@code null}.
   */
  public void tee(OpenNlpDocument document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    for (Map.Entry<String, DocumentSink> sink : sinksByInstance.entrySet()) {
      try {
        sink.getValue().accept(document);
      } catch (IOException | RuntimeException e) {
        logger.error("Document sink '{}' failed to accept document '{}'",
            sink.getKey(), document.getDocId(), e);
      }
    }
  }

  /** {@inheritDoc} Closes every sink, keeping later failures after an earlier one. */
  @Override
  public void close() {
    closeAll(sinksByInstance);
  }

  /** Closes the given sinks, logging failures instead of propagating them. */
  private static void closeAll(Map<String, DocumentSink> sinks) {
    for (Map.Entry<String, DocumentSink> sink : sinks.entrySet()) {
      try {
        sink.getValue().close();
      } catch (IOException | RuntimeException e) {
        logger.error("Document sink '{}' failed to close", sink.getKey(), e);
      }
    }
  }

  /** Returns the configured instance ids, in stable order. Package-private for tests. */
  List<String> instanceIds() {
    return new ArrayList<>(sinksByInstance.keySet());
  }
}
