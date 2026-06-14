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
package org.apache.opennlp.grpc.model;

import java.util.List;
import java.util.Map;

/**
 * Service provider interface for document categorization backends, the document-classification
 * analogue of {@link NerBackendFactory}. Each factory parses its own configuration namespace and
 * produces {@link DocCategorizerModel}s; {@link DocCategorizerRegistry} discovers all factories
 * via {@link java.util.ServiceLoader} and aggregates their models, so several backends can be
 * active at once.
 *
 * <p>Ship a jar with an implementation registered in
 * {@code META-INF/services/org.apache.opennlp.grpc.model.DocCategorizerBackendFactory} to add a
 * backend (a remote classifier, a custom model format, any runtime in any language) alongside the
 * built-in classic ({@code opennlp-me}) and ONNX ({@code onnx}/{@code cuda}) backends — no change
 * to the server. Implementations must have a public no-arg constructor.</p>
 */
public interface DocCategorizerBackendFactory {

  /**
   * @return A stable, lower-case identifier for this factory, used in logging and to reject
   *     duplicate factories on the classpath. Distinct from the
   *     {@link DocCategorizerModel#backendId()} reported per model.
   */
  String factoryId();

  /**
   * Loads the document categorizers this backend finds in the given configuration.
   *
   * @param configuration The full server configuration. Must not be {@code null}; a factory
   *     reads only the keys of its own namespace and ignores the rest.
   *
   * @return The categorizers configured for this backend, possibly empty; never {@code null}.
   *
   * @throws org.apache.opennlp.grpc.processor.AnalysisException If the configuration is invalid
   *     or a model fails to load.
   */
  List<DocCategorizerModel> create(Map<String, String> configuration);
}
