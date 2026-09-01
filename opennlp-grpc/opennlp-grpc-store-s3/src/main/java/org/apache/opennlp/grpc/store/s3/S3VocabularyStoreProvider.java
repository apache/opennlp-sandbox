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
package org.apache.opennlp.grpc.store.s3;

import java.io.IOException;
import java.net.URI;

import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStoreProvider;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Serves {@code s3} artifact roots such as {@code s3://bucket/team/vocabularies}. The
 * bucket is the URI host, the path becomes the key prefix, and the AWS region and
 * credentials resolve through the standard SDK chain (environment, system properties,
 * profile, or instance metadata).
 */
public final class S3VocabularyStoreProvider implements VocabularyStoreProvider {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public S3VocabularyStoreProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public String scheme() {
    return "s3";
  }

  /** {@inheritDoc} */
  @Override
  public VocabularyStore open(URI root) throws IOException {
    return new S3VocabularyStore(S3Client.builder().build(), bucket(root), prefix(root));
  }

  /**
   * Extracts the bucket from a validated {@code s3} root URI.
   *
   * @param root The artifact root. Must not be {@code null}.
   *
   * @return The bucket name.
   * @throws IllegalArgumentException If the URI is not a plain {@code s3://bucket[/prefix]}
   *     root with a bucket and no user info, port, query, or fragment.
   */
  static String bucket(URI root) {
    validate(root);
    return root.getHost();
  }

  /**
   * Extracts the normalized key prefix from a validated {@code s3} root URI: empty for a
   * bare bucket, otherwise the path without its leading slash and with a trailing slash.
   *
   * @param root The artifact root. Must not be {@code null}.
   *
   * @return The normalized prefix.
   * @throws IllegalArgumentException If the URI is not a plain {@code s3://bucket[/prefix]}
   *     root with a bucket and no user info, port, query, or fragment.
   */
  static String prefix(URI root) {
    validate(root);
    final String path = root.getPath();
    if (path == null || path.isEmpty() || path.equals("/")) {
      return "";
    }
    final String trimmed = path.startsWith("/") ? path.substring(1) : path;
    return trimmed.endsWith("/") ? trimmed : trimmed + "/";
  }

  /** Validates the shape of one {@code s3} artifact root. */
  private static void validate(URI root) {
    if (root == null) {
      throw new IllegalArgumentException("root must not be null");
    }
    if (!"s3".equalsIgnoreCase(root.getScheme())) {
      throw new IllegalArgumentException("root must use the s3 scheme, was '" + root + "'");
    }
    if (root.getHost() == null || root.getHost().isBlank()) {
      throw new IllegalArgumentException("root must name a bucket, was '" + root + "'");
    }
    if (root.getUserInfo() != null || root.getPort() != -1
        || root.getQuery() != null || root.getFragment() != null) {
      throw new IllegalArgumentException(
          "root must be a plain s3://bucket[/prefix] URI, was '" + root + "'");
    }
  }
}
