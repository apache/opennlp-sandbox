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
package org.apache.opennlp.grpc.webapp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;

final class WebUiExtensionRegistry {

  private final List<WebUiExtension> extensions;

  /**
   * Validates and orders UI extensions.
   *
   * @param extensions The discovered extensions.
   * @throws IllegalArgumentException If the list or a provider is invalid or duplicated.
   */
  WebUiExtensionRegistry(List<WebUiExtension> extensions) {
    if (extensions == null) {
      throw new IllegalArgumentException("extensions must not be null");
    }
    Set<String> ids = new HashSet<>();
    Set<String> mounts = new HashSet<>();
    List<WebUiExtension> checked = new ArrayList<>(extensions.size());
    for (WebUiExtension extension : extensions) {
      if (extension == null) {
        throw new IllegalArgumentException("web UI extension must not be null");
      }
      WebUiExtensionDescriptor descriptor = extension.descriptor();
      if (descriptor == null) {
        throw new IllegalArgumentException("web UI extension descriptor must not be null: "
            + extension.getClass().getName());
      }
      String id = descriptor.id().value();
      if (!ids.add(id)) {
        throw new IllegalArgumentException("duplicate extension id: " + id);
      }
      String mount = descriptor.mountPath().value();
      if (!mounts.add(mount)) {
        throw new IllegalArgumentException("duplicate mount path: " + mount);
      }
      if (extension.resourceClassLoader() == null) {
        throw new IllegalArgumentException("web UI extension class loader must not be null: " + id);
      }
      checked.add(extension);
    }
    checked.sort(Comparator
        .comparingInt((WebUiExtension extension) ->
            extension.descriptor().mountPath().value().length())
        .reversed()
        .thenComparing(extension -> extension.descriptor().id().value()));
    this.extensions = List.copyOf(checked);
  }

  /**
   * Discovers UI extensions through Java ServiceLoader.
   *
   * @param classLoader The provider class loader.
   * @return The validated extension registry.
   * @throws IllegalArgumentException If {@code classLoader} is {@code null}.
   */
  static WebUiExtensionRegistry load(ClassLoader classLoader) {
    if (classLoader == null) {
      throw new IllegalArgumentException("classLoader must not be null");
    }
    List<WebUiExtension> discovered = ServiceLoader.load(WebUiExtension.class, classLoader)
        .stream()
        .map(ServiceLoader.Provider::get)
        .toList();
    return new WebUiExtensionRegistry(discovered);
  }

  /** @return The immutable extensions ordered by descending mount specificity. */
  List<WebUiExtension> extensions() {
    return extensions;
  }
}
