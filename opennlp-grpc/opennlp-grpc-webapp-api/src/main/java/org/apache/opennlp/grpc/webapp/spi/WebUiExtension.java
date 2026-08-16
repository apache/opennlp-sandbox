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
package org.apache.opennlp.grpc.webapp.spi;

import java.util.ServiceLoader;

/**
 * Supplies static user interface assets to the OpenNLP gRPC web application.
 *
 * <p>Implementations are discovered with {@link ServiceLoader}. A classpath provider registers
 * its implementation class in
 * {@code META-INF/services/org.apache.opennlp.grpc.webapp.spi.WebUiExtension}. A named Java
 * module instead uses a {@code provides WebUiExtension with ...} directive. Providers must have
 * a public no-argument constructor, as required by {@code ServiceLoader}.</p>
 *
 * <p>This SPI contributes metadata and classpath resources only. The web application host owns
 * HTTP routing, API endpoints, authentication, security headers, request limits, and lifecycle.
 * Extensions cannot install HTTP handlers or server callbacks through this API.</p>
 */
public interface WebUiExtension {

  /**
   * Returns this extension's validated, immutable metadata.
   *
   * @return The extension descriptor, never {@code null}.
   */
  WebUiExtensionDescriptor descriptor();

  /**
   * Returns the class loader from which the host resolves this extension's resources.
   *
   * <p>The default is the provider implementation's defining class loader. Providers loaded by
   * the bootstrap loader fall back to the system class loader.</p>
   *
   * @return The class loader for {@link WebUiExtensionDescriptor#resourceRoot()}, never
   *     {@code null} in a standard application runtime.
   */
  default ClassLoader resourceClassLoader() {
    final ClassLoader providerClassLoader = getClass().getClassLoader();
    return providerClassLoader == null ? ClassLoader.getSystemClassLoader() : providerClassLoader;
  }
}
