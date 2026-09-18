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

import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * Loads Protocol Buffer file descriptors from one source, such as a serialized
 * descriptor set or generated classes on the classpath.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface DescriptorLoader {

  /**
   * Loads every file descriptor available from this loader's source.
   *
   * @return File descriptors, never {@code null}.
   * @throws DescriptorLoadException If loading fails.
   */
  List<FileDescriptor> loadDescriptors() throws DescriptorLoadException;

  /**
   * Loads one file descriptor by proto file name.
   *
   * @param fileName Proto file name, such as {@code my_types.proto}.
   * @return The matching file descriptor, or {@code null} when not found.
   * @throws DescriptorLoadException If loading fails.
   */
  FileDescriptor loadDescriptor(String fileName) throws DescriptorLoadException;

  /**
   * Loads the file descriptor defining one message type. Unlike
   * {@link #loadDescriptor(String)}, which looks up by proto file name, this looks up by
   * the fully qualified name of a message type contained in a file, nested types included.
   *
   * @param fullTypeName Fully qualified message type name, such as {@code my.pkg.MyType}.
   * @return The file descriptor containing the type, or {@code null} when no file defines it.
   * @throws DescriptorLoadException If loading fails.
   */
  default FileDescriptor loadDescriptorForType(String fullTypeName)
      throws DescriptorLoadException {
    for (FileDescriptor file : loadDescriptors()) {
      for (Descriptor message : file.getMessageTypes()) {
        if (containsType(message, fullTypeName)) {
          return file;
        }
      }
    }
    return null;
  }

  /**
   * Tests whether a message or one of its nested types has the given full name.
   *
   * @param message Message descriptor to test.
   * @param fullTypeName Fully qualified type name.
   * @return {@code true} when the message or a nested type matches.
   */
  private static boolean containsType(Descriptor message, String fullTypeName) {
    if (message.getFullName().equals(fullTypeName)) {
      return true;
    }
    for (Descriptor nested : message.getNestedTypes()) {
      if (containsType(nested, fullTypeName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tests whether this loader's source is present.
   *
   * @return {@code true} when {@link #loadDescriptors()} can be attempted.
   */
  boolean isAvailable();

  /**
   * Names this loader for logs and diagnostics.
   *
   * @return Human-readable loader name.
   */
  String getLoaderType();

  /** Reports a failure to read or build descriptors from a loader source. */
  class DescriptorLoadException extends Exception {

    private static final long serialVersionUID = -1062498362801133287L;

    /**
     * Creates an exception without a cause.
     *
     * @param message Failure description.
     */
    public DescriptorLoadException(String message) {
      super(message);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param message Failure description.
     * @param cause Underlying failure.
     */
    public DescriptorLoadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
