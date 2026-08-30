/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.vocabulary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import org.apache.opennlp.grpc.v1.DictionaryFormatDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider;

/** Immutable registry of dictionary format providers discovered at server startup. */
public final class DictionaryFormatRegistry {

  private final Map<String, DictionaryFormatProvider> providers;
  private final List<DictionaryFormatDescriptor> descriptors;

  /**
   * Validates and registers the supplied providers.
   *
   * @param providers Providers to register. Must not be {@code null} or contain null.
   * @throws IllegalArgumentException If a provider or descriptor is invalid or duplicated.
   */
  DictionaryFormatRegistry(Iterable<DictionaryFormatProvider> providers) {
    if (providers == null) {
      throw new IllegalArgumentException("dictionary format providers must not be null");
    }
    final Map<String, DictionaryFormatProvider> registered = new TreeMap<>();
    final List<DictionaryFormatDescriptor> described = new ArrayList<>();
    for (DictionaryFormatProvider provider : providers) {
      if (provider == null) {
        throw new IllegalArgumentException("dictionary format providers must not contain null");
      }
      final DictionaryFormatDescriptor descriptor = provider.descriptor();
      validateDescriptor(descriptor);
      final String key = key(descriptor.getFormat());
      if (registered.putIfAbsent(key, provider) != null) {
        throw new IllegalArgumentException("dictionary format '" + key
            + "' is declared more than once");
      }
      described.add(descriptor);
    }
    described.sort(Comparator.comparing(descriptor -> key(descriptor.getFormat())));
    this.providers = Collections.unmodifiableMap(registered);
    this.descriptors = List.copyOf(described);
  }

  /**
   * Discovers providers using the server module's class loader.
   *
   * @return Fully validated registry.
   */
  public static DictionaryFormatRegistry discover() {
    return new DictionaryFormatRegistry(ServiceLoader.load(
        DictionaryFormatProvider.class, DictionaryFormatRegistry.class.getClassLoader()));
  }

  /** @return Available format descriptors in stable selector order. */
  List<DictionaryFormatDescriptor> descriptors() {
    return descriptors;
  }

  /**
   * Resolves one exact selector.
   *
   * @param selector Requested selector.
   * @return Matching provider.
   * @throws IllegalArgumentException If the selector is invalid or unavailable.
   */
  DictionaryFormatProvider require(DictionaryFormatSelector selector) {
    final String requested = key(selector);
    final DictionaryFormatProvider provider = providers.get(requested);
    if (provider == null) {
      throw new IllegalArgumentException("Unknown dictionary format '" + requested
          + "'; available formats: " + providers.keySet());
    }
    return provider;
  }

  /** Validates one provider descriptor before registration. */
  private void validateDescriptor(DictionaryFormatDescriptor descriptor) {
    if (descriptor == null) {
      throw new IllegalArgumentException("dictionary format descriptor must not be null");
    }
    key(descriptor.getFormat());
    if (descriptor.getDisplayName().isBlank()
        || !descriptor.getDisplayName().equals(descriptor.getDisplayName().trim())) {
      throw new IllegalArgumentException(
          "dictionary format display_name must be trimmed and nonblank");
    }
    if (descriptor.getMediaTypesCount() == 0
        || descriptor.getMediaTypesList().stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("dictionary format must declare nonblank media types");
    }
  }

  /** Returns the stable registry key for one valid selector. */
  private String key(DictionaryFormatSelector selector) {
    if (selector == null) {
      throw new IllegalArgumentException("dictionary format selector must not be null");
    }
    return switch (selector.getKindCase()) {
      case STANDARD -> {
        final StandardDictionaryFormat standard = selector.getStandard();
        if (standard == StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_UNSPECIFIED
            || standard == StandardDictionaryFormat.UNRECOGNIZED) {
          throw new IllegalArgumentException("standard dictionary format must be specified");
        }
        yield "standard:" + standard.getNumber();
      }
      case CUSTOM -> {
        requireStableId(selector.getCustom());
        yield "custom:" + selector.getCustom();
      }
      case KIND_NOT_SET -> throw new IllegalArgumentException(
          "dictionary format selector kind must be set");
    };
  }

  /** Validates a custom provider id used in configuration and on the wire. */
  private void requireStableId(String id) {
    if (id == null || id.isEmpty() || !id.equals(id.trim())) {
      throw new IllegalArgumentException(
          "custom dictionary format id must be trimmed and nonblank");
    }
    for (int i = 0; i < id.length(); i++) {
      final char c = id.charAt(i);
      final boolean accepted = c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
          || (i > 0 && (c == '.' || c == '_' || c == '-'));
      if (!accepted) {
        throw new IllegalArgumentException(
            "custom dictionary format id contains invalid character '" + c + "'");
      }
    }
  }
}
