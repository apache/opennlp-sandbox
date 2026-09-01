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
package org.apache.opennlp.grpc.spi.model;

import java.util.List;
import java.util.Map;

/**
 * Service provider interface for named entity recognition backends discovered through
 * {@link java.util.ServiceLoader}. Each factory contributes the models configured in its
 * namespace.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface NerBackendFactory {

  /**
   * Returns the stable identifier of this factory.
   *
   * @return A stable, lower-case identifier for this factory, used in logging and to reject
   *     duplicate factories on the classpath. Distinct from the {@link NerModel#backendId()}
   *     reported per model.
   */
  String factoryId();

  /**
   * Loads the NER models this backend finds in the given configuration.
   *
   * @param configuration The full server configuration. Must not be {@code null}; a factory
   *     reads only the keys of its own namespace and ignores the rest.
   * @param context Shared resources a backend may need (e.g. a sentence detector). Must not
   *     be {@code null}.
   *
   * @return The recognizers configured for this backend, possibly empty; never {@code null}.
   *
   * @throws org.apache.opennlp.grpc.spi.AnalysisException If the configuration is
   *     invalid or a model fails to load.
   * @throws IllegalArgumentException If {@code configuration} or {@code context} is
   *     {@code null}.
   */
  List<NerModel> create(Map<String, String> configuration, NerBackendContext context);
}
