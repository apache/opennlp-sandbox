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
 * KIND, either express or implied.  See the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.descriptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of Protocol Buffer message descriptors with lookup by full or simple type
 * name, backed by pluggable {@link DescriptorLoader} sources for on-demand resolution.
 *
 * <p>Safe for concurrent lookups and registrations.</p>
 */
public class DescriptorRegistry {

  private static final Logger logger = LoggerFactory.getLogger(DescriptorRegistry.class);

  /** Bound on the negative-lookup cache; when reached, the cache is reset, not grown. */
  private static final int MAX_KNOWN_MISSING_TYPES = 1024;

  private final Map<String, Descriptor> descriptorsByFullName = new ConcurrentHashMap<>();
  private final Map<String, Descriptor> descriptorsBySimpleName = new ConcurrentHashMap<>();
  private final List<DescriptorLoader> loaders = new CopyOnWriteArrayList<>();

  /**
   * Full type names that on-demand resolution already failed to find. Without this,
   * every {@link #findDescriptorByFullName(String)} miss would re-consult (and typically
   * re-parse) every loader. Cleared by {@link #clear()} and
   * {@link #addLoader(DescriptorLoader)}.
   */
  private final Set<String> knownMissingTypes = ConcurrentHashMap.newKeySet();

  private volatile boolean autoLoadAttempted;

  /** Creates a registry seeded with the well-known Google protobuf types. */
  public DescriptorRegistry() {
    registerWellKnownTypes();
  }

  /** Registers the linked-in well-known Google protobuf types. */
  private void registerWellKnownTypes() {
    register(com.google.protobuf.Struct.getDescriptor());
    register(com.google.protobuf.Value.getDescriptor());
    register(com.google.protobuf.ListValue.getDescriptor());
    register(com.google.protobuf.Timestamp.getDescriptor());
    register(com.google.protobuf.Duration.getDescriptor());
    register(com.google.protobuf.Any.getDescriptor());
    register(com.google.protobuf.Empty.getDescriptor());
  }

  /**
   * Registers one descriptor.
   *
   * <p>Full-name registrations always win (last write). For simple-name lookups, the
   * first registration of a given simple name wins: registering a different type with
   * the same simple name later logs a warning and leaves the original mapping in place,
   * so {@link #findDescriptorBySimpleName(String)} stays deterministic. Use full names
   * to disambiguate colliding types.</p>
   *
   * @param descriptor Descriptor to register.
   */
  public void register(Descriptor descriptor) {
    descriptorsByFullName.put(descriptor.getFullName(), descriptor);
    final Descriptor existing =
        descriptorsBySimpleName.putIfAbsent(descriptor.getName(), descriptor);
    if (existing != null) {
      if (existing.getFullName().equals(descriptor.getFullName())) {
        // Same type re-registered, possibly a rebuilt descriptor instance: keep current.
        descriptorsBySimpleName.put(descriptor.getName(), descriptor);
      } else {
        logger.warn("Simple name collision for '{}': keeping first registration {} and "
                + "ignoring {}; use findDescriptorByFullName to disambiguate",
            descriptor.getName(), existing.getFullName(), descriptor.getFullName());
      }
    }
    knownMissingTypes.remove(descriptor.getFullName());
  }

  /**
   * Registers every message type in a file, nested types included.
   *
   * @param fileDescriptor File whose message types should be registered.
   */
  public void registerFile(FileDescriptor fileDescriptor) {
    for (Descriptor messageType : fileDescriptor.getMessageTypes()) {
      register(messageType);
      registerNestedTypes(messageType);
    }
  }

  /**
   * Registers a message's nested types recursively.
   *
   * @param descriptor Message whose nested types should be registered.
   */
  private void registerNestedTypes(Descriptor descriptor) {
    for (Descriptor nested : descriptor.getNestedTypes()) {
      register(nested);
      registerNestedTypes(nested);
    }
  }

  /**
   * Registers the descriptor of one message instance.
   *
   * @param message Message whose descriptor should be registered.
   */
  public void registerFromMessage(Message message) {
    register(message.getDescriptorForType());
  }

  /**
   * Finds a descriptor by fully qualified type name, consulting loaders on a miss.
   *
   * @param fullName Fully qualified type name.
   * @return The descriptor, or {@code null} when no source defines it.
   */
  public Descriptor findDescriptorByFullName(String fullName) {
    final Descriptor registered = descriptorsByFullName.get(fullName);
    return registered != null ? registered : resolveOnDemand(fullName);
  }

  /**
   * Resolves one type through the configured loaders.
   *
   * @param typeName Fully qualified type name.
   * @return The resolved descriptor, or {@code null}.
   */
  private Descriptor resolveOnDemand(String typeName) {
    if (knownMissingTypes.contains(typeName)) {
      return null;
    }
    for (DescriptorLoader loader : loaders) {
      if (loader.isAvailable()) {
        try {
          // Ask for the file DEFINING this type; loadDescriptor(name) would treat the
          // type name as a proto file name and never match.
          final FileDescriptor file = loader.loadDescriptorForType(typeName);
          if (file != null) {
            registerFile(file);
            // Look up directly (non-recursive): re-entering resolution for the same
            // name would loop forever when the loaded file lacks the requested type.
            final Descriptor resolved = descriptorsByFullName.get(typeName);
            if (resolved != null) {
              return resolved;
            }
          }
        } catch (DescriptorLoader.DescriptorLoadException e) {
          logger.debug("Loader {} failed resolving {}: {}",
              loader.getLoaderType(), typeName, e.getMessage());
        }
      }
    }
    // Negative-cache the miss so repeated lookups do not re-consult every loader.
    if (knownMissingTypes.size() >= MAX_KNOWN_MISSING_TYPES) {
      knownMissingTypes.clear();
    }
    knownMissingTypes.add(typeName);
    return null;
  }

  /**
   * Finds a descriptor by simple type name, running auto-load once on a miss.
   *
   * @param simpleName Simple type name.
   * @return The descriptor, or {@code null} when no source defines it.
   */
  public Descriptor findDescriptorBySimpleName(String simpleName) {
    final Descriptor registered = descriptorsBySimpleName.get(simpleName);
    if (registered != null) {
      return registered;
    }
    autoLoadDescriptors();
    return descriptorsBySimpleName.get(simpleName);
  }

  /**
   * Finds a descriptor by full name first, then by simple name.
   *
   * @param name Full or simple type name.
   * @return The descriptor, or {@code null} when no source defines it.
   */
  public Descriptor findDescriptor(String name) {
    final Descriptor byFullName = findDescriptorByFullName(name);
    return byFullName != null ? byFullName : findDescriptorBySimpleName(name);
  }

  /**
   * Tests whether a type is registered.
   *
   * @param fullName Fully qualified type name.
   * @return {@code true} when registered.
   */
  public boolean isRegistered(String fullName) {
    return descriptorsByFullName.containsKey(fullName);
  }

  /**
   * Counts registered descriptors by full name.
   *
   * @return Number of registered descriptors.
   */
  public int size() {
    return descriptorsByFullName.size();
  }

  /**
   * Snapshots the registered message descriptors, for building a
   * {@code JsonFormat.TypeRegistry} or similar consumers.
   *
   * @return Immutable descriptor snapshot.
   */
  public List<Descriptor> registeredDescriptors() {
    return List.copyOf(descriptorsByFullName.values());
  }

  /**
   * Loads and registers every descriptor from one loader.
   *
   * @param loader Loader to drain.
   * @return Number of top-level message types registered.
   * @throws DescriptorLoader.DescriptorLoadException If loading fails.
   */
  public int loadFrom(DescriptorLoader loader)
      throws DescriptorLoader.DescriptorLoadException {
    int count = 0;
    for (FileDescriptor file : loader.loadDescriptors()) {
      registerFile(file);
      count += file.getMessageTypes().size();
    }
    return count;
  }

  /**
   * Clears all registered descriptors except well-known types and resets the auto-load
   * and negative-lookup state so descriptors can be reloaded.
   */
  public void clear() {
    descriptorsByFullName.clear();
    descriptorsBySimpleName.clear();
    knownMissingTypes.clear();
    autoLoadAttempted = false;
    registerWellKnownTypes();
  }

  /**
   * Adds a loader consulted by on-demand resolution and auto-loading.
   *
   * @param loader Loader to add; {@code null} is ignored.
   */
  public void addLoader(DescriptorLoader loader) {
    if (loader != null) {
      loaders.add(loader);
      // Allow the next auto-load to pick up the new loader, and retry lookups that
      // previously missed since this loader may supply them.
      autoLoadAttempted = false;
      knownMissingTypes.clear();
    }
  }

  /** Loads descriptors from every available loader, at most once until reset. */
  public synchronized void autoLoadDescriptors() {
    if (autoLoadAttempted) {
      return;
    }
    autoLoadAttempted = true;
    for (DescriptorLoader loader : new ArrayList<>(loaders)) {
      if (loader.isAvailable()) {
        try {
          final int count = loadFrom(loader);
          logger.info("Loaded {} descriptors from {}", count, loader.getLoaderType());
        } catch (DescriptorLoader.DescriptorLoadException e) {
          logger.warn("Failed to load descriptors from {}: {}",
              loader.getLoaderType(), e.getMessage());
        }
      }
    }
  }
}
