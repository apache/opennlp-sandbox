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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves descriptors from generated protobuf classes on the classpath.
 *
 * <p>Resolution is on demand and by message type name: the fully qualified proto type
 * name is mapped to candidate generated Java class names, and the first candidate that
 * is a {@link Message} contributes its file descriptor. Types whose generated code lives
 * in an outer holder class (a file compiled without {@code java_multiple_files}) or under
 * a {@code java_package} that differs from the proto package are not resolvable this way;
 * use a {@link DescriptorSetLoader} for those.</p>
 */
public final class ClasspathDescriptorLoader implements DescriptorLoader {

  private static final Logger logger = LoggerFactory.getLogger(ClasspathDescriptorLoader.class);

  /** Creates a loader over the current thread's context class loader. */
  public ClasspathDescriptorLoader() {
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always empty: scanning the classpath for every descriptor is expensive and this
   * loader serves on-demand lookups through {@link #loadDescriptorForType(String)}.</p>
   */
  @Override
  public List<FileDescriptor> loadDescriptors() {
    return Collections.emptyList();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This loader has no proto-file index; a caller passing a type name is still
   * served.</p>
   */
  @Override
  public FileDescriptor loadDescriptor(String fileName) throws DescriptorLoadException {
    return loadDescriptorForType(fileName);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides the interface default, which enumerates {@link #loadDescriptors()} and
   * would therefore never match: this loader does not enumerate.</p>
   */
  @Override
  public FileDescriptor loadDescriptorForType(String fullTypeName) {
    if (fullTypeName == null || fullTypeName.isEmpty()) {
      return null;
    }
    for (String candidate : candidateClassNames(fullTypeName)) {
      final FileDescriptor file = resolveFromClass(candidate);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  /**
   * Java class names that may hold the generated code for a proto type name. Message
   * nesting is expressed with {@code $} in binary class names, and the split between
   * package and nesting is not recoverable from the proto name alone, so every split
   * point is tried, outermost first.
   *
   * @param fullTypeName Fully qualified proto type name.
   * @return Candidate binary class names.
   */
  private static List<String> candidateClassNames(String fullTypeName) {
    final List<String> parts = new ArrayList<>();
    int partStart = 0;
    for (int index = 0; index <= fullTypeName.length(); index++) {
      if (index == fullTypeName.length() || fullTypeName.charAt(index) == '.') {
        parts.add(fullTypeName.substring(partStart, index));
        partStart = index + 1;
      }
    }
    final List<String> candidates = new ArrayList<>(parts.size());
    candidates.add(fullTypeName);
    for (int split = parts.size() - 1; split >= 1; split--) {
      final StringBuilder name = new StringBuilder();
      for (int index = 0; index < parts.size(); index++) {
        if (index > 0) {
          name.append(index < split ? '.' : '$');
        }
        name.append(parts.get(index));
      }
      final String candidate = name.toString();
      if (!candidate.equals(fullTypeName)) {
        candidates.add(candidate);
      }
    }
    return candidates;
  }

  /**
   * Resolves a file descriptor from one generated class, when present.
   *
   * @param className Candidate binary class name.
   * @return The class's file descriptor, or {@code null}.
   */
  private FileDescriptor resolveFromClass(String className) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if (loader == null) {
        loader = getClass().getClassLoader();
      }
      final Class<?> clazz = Class.forName(className, false, loader);
      if (Message.class.isAssignableFrom(clazz)) {
        final Method getDescriptor = clazz.getMethod("getDescriptor");
        if (getDescriptor.invoke(null) instanceof Descriptor descriptor) {
          return descriptor.getFile();
        }
      }
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      // Normal miss: the candidate class is not on the classpath.
    } catch (ReflectiveOperationException | RuntimeException e) {
      logger.warn("Failed to resolve descriptor from class {}: {}", className, e.getMessage());
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAvailable() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String getLoaderType() {
    return "Classpath Class Resolver";
  }
}
