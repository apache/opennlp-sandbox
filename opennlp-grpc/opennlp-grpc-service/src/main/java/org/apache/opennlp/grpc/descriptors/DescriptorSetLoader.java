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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * Loads Protocol Buffer descriptors from a serialized {@link FileDescriptorSet} resource,
 * such as the set the api jar ships under {@link #DEFAULT_DESCRIPTOR_PATH}.
 */
public final class DescriptorSetLoader implements DescriptorLoader {

  /** Classpath location of the descriptor set shipped inside the api jar. */
  public static final String DEFAULT_DESCRIPTOR_PATH =
      "META-INF/opennlp/descriptors/opennlp-grpc-v1.protobin";

  private final String descriptorPath;
  private final ClassLoader classLoader;

  /** Creates a loader for the descriptor set shipped inside the api jar. */
  public DescriptorSetLoader() {
    this(DEFAULT_DESCRIPTOR_PATH);
  }

  /**
   * Creates a loader for one classpath resource.
   *
   * @param descriptorPath Classpath location of a serialized descriptor set.
   */
  public DescriptorSetLoader(String descriptorPath) {
    this(descriptorPath, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a loader for one classpath resource on an explicit class loader.
   *
   * @param descriptorPath Classpath location of a serialized descriptor set.
   * @param classLoader Class loader to resolve the resource, or {@code null} for the
   *     system class loader.
   */
  public DescriptorSetLoader(String descriptorPath, ClassLoader classLoader) {
    this.descriptorPath = descriptorPath;
    this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
  }

  /**
   * Returns the classpath location this loader reads.
   *
   * @return Classpath resource path.
   */
  public String getDescriptorPath() {
    return descriptorPath;
  }

  /** {@inheritDoc} */
  @Override
  public List<FileDescriptor> loadDescriptors() throws DescriptorLoadException {
    try (InputStream inputStream = classLoader.getResourceAsStream(descriptorPath)) {
      if (inputStream == null) {
        throw new DescriptorLoadException(
            "Descriptor set not found on classpath: " + descriptorPath);
      }
      return buildFileDescriptors(FileDescriptorSet.parseFrom(inputStream));
    } catch (IOException e) {
      throw new DescriptorLoadException("Failed to read descriptor set: " + descriptorPath, e);
    } catch (DescriptorValidationException | RuntimeException e) {
      throw new DescriptorLoadException(
          "Failed to build descriptors from: " + descriptorPath, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public FileDescriptor loadDescriptor(String fileName) throws DescriptorLoadException {
    return loadDescriptors().stream()
        .filter(file -> file.getName().equals(fileName))
        .findFirst()
        .orElse(null);
  }

  /**
   * Builds runtime descriptors from an already parsed descriptor set, for callers that
   * transport descriptor data outside the classpath.
   *
   * @param descriptorSet Parsed descriptor set.
   * @return File descriptors in dependency-resolved order.
   * @throws DescriptorLoadException If the set is invalid or cyclic.
   */
  public static List<FileDescriptor> fromDescriptorSet(FileDescriptorSet descriptorSet)
      throws DescriptorLoadException {
    try {
      return buildFileDescriptors(descriptorSet);
    } catch (DescriptorValidationException | RuntimeException e) {
      throw new DescriptorLoadException("Failed to build descriptors from descriptor set", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAvailable() {
    try (InputStream stream = classLoader.getResourceAsStream(descriptorPath)) {
      return stream != null;
    } catch (IOException e) {
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getLoaderType() {
    return "Descriptor Set " + descriptorPath;
  }

  /**
   * Builds every file in a descriptor set, resolving declared dependencies.
   *
   * @param descriptorSet Parsed descriptor set.
   * @return File descriptors keyed order of first resolution.
   * @throws DescriptorValidationException If a file fails protobuf validation.
   * @throws DescriptorLoadException If dependencies are cyclic or missing.
   */
  private static List<FileDescriptor> buildFileDescriptors(FileDescriptorSet descriptorSet)
      throws DescriptorValidationException, DescriptorLoadException {
    final Map<String, FileDescriptorProto> protoByName = new HashMap<>();
    for (FileDescriptorProto proto : descriptorSet.getFileList()) {
      protoByName.put(proto.getName(), proto);
    }
    final Map<String, FileDescriptor> built = new HashMap<>();
    for (FileDescriptorProto proto : descriptorSet.getFileList()) {
      if (!built.containsKey(proto.getName())) {
        buildFileDescriptor(proto, protoByName, built, new LinkedHashSet<>());
      }
    }
    return new ArrayList<>(built.values());
  }

  /**
   * Recursively builds one file and its dependencies.
   *
   * @param proto File to build.
   * @param protoByName Every file in the set by name.
   * @param built Already built files by name.
   * @param inProgress Files currently being built, insertion ordered.
   * @return The built file descriptor.
   * @throws DescriptorValidationException If the file fails protobuf validation.
   * @throws DescriptorLoadException If dependencies are cyclic or missing.
   */
  private static FileDescriptor buildFileDescriptor(
      FileDescriptorProto proto,
      Map<String, FileDescriptorProto> protoByName,
      Map<String, FileDescriptor> built,
      Set<String> inProgress) throws DescriptorValidationException, DescriptorLoadException {
    final FileDescriptor existing = built.get(proto.getName());
    if (existing != null) {
      return existing;
    }
    // Guard against cyclic dependency declarations, which would otherwise recurse until
    // StackOverflowError. The insertion-ordered set doubles as the chain in the message.
    if (!inProgress.add(proto.getName())) {
      throw new DescriptorLoadException(
          "dependency cycle: " + String.join(" -> ", inProgress) + " -> " + proto.getName());
    }
    final List<FileDescriptor> dependencies = new ArrayList<>();
    for (String dependency : proto.getDependencyList()) {
      FileDescriptor resolved = built.get(dependency);
      if (resolved == null) {
        final FileDescriptorProto dependencyProto = protoByName.get(dependency);
        if (dependencyProto != null) {
          resolved = buildFileDescriptor(dependencyProto, protoByName, built, inProgress);
        } else {
          resolved = wellKnownFile(dependency);
          if (resolved == null) {
            throw new DescriptorLoadException(
                "Missing dependency: " + dependency + " for " + proto.getName());
          }
        }
      }
      dependencies.add(resolved);
    }
    final FileDescriptor descriptor =
        FileDescriptor.buildFrom(proto, dependencies.toArray(new FileDescriptor[0]));
    built.put(proto.getName(), descriptor);
    inProgress.remove(proto.getName());
    return descriptor;
  }

  /**
   * Resolves a well-known Google proto file absent from the descriptor set.
   *
   * @param fileName Proto file name.
   * @return The linked-in file descriptor, or {@code null} when not well known.
   */
  private static FileDescriptor wellKnownFile(String fileName) {
    return switch (fileName) {
      case "google/protobuf/any.proto" -> com.google.protobuf.Any.getDescriptor().getFile();
      case "google/protobuf/struct.proto" -> com.google.protobuf.Struct.getDescriptor().getFile();
      case "google/protobuf/timestamp.proto" ->
          com.google.protobuf.Timestamp.getDescriptor().getFile();
      case "google/protobuf/duration.proto" ->
          com.google.protobuf.Duration.getDescriptor().getFile();
      case "google/protobuf/empty.proto" -> com.google.protobuf.Empty.getDescriptor().getFile();
      case "google/protobuf/field_mask.proto" ->
          com.google.protobuf.FieldMask.getDescriptor().getFile();
      case "google/protobuf/wrappers.proto" ->
          com.google.protobuf.StringValue.getDescriptor().getFile();
      default -> null;
    };
  }
}
