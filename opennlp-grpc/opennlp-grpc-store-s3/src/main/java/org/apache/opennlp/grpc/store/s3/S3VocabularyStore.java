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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@link VocabularyStore} over one S3 bucket and key prefix.
 *
 * <p>S3 has no atomic multi-object rename, so publication uses a marker object: entry
 * objects live under {@code <prefix>artifacts/<kind>/<artifactId>/<entryName>} and an
 * artifact becomes visible only when its marker object
 * {@code <prefix>published/<kind>/<artifactId>} exists. The marker is written last by
 * one atomic PUT, and deletion removes the marker first, so readers never observe a
 * partial artifact. Staged entries accumulate in a local temporary directory and reach
 * the bucket only during {@link ArtifactWriter#commit()}.</p>
 */
final class S3VocabularyStore implements VocabularyStore {

  private static final String ARTIFACTS_SEGMENT = "artifacts/";
  private static final String PUBLISHED_SEGMENT = "published/";

  private final S3Client client;
  private final String bucket;
  private final String prefix;

  /**
   * Creates a store over one bucket and normalized key prefix.
   *
   * @param client The S3 client. Must not be {@code null}.
   * @param bucket The bucket name. Must not be blank.
   * @param prefix The key prefix, empty or ending in {@code /}. Must not be {@code null}.
   */
  S3VocabularyStore(S3Client client, String bucket, String prefix) {
    if (client == null) {
      throw new IllegalArgumentException("client must not be null");
    }
    this.client = client;
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("bucket must not be blank");
    }
    this.bucket = bucket;
    if (prefix == null || (!prefix.isEmpty() && !prefix.endsWith("/"))) {
      throw new IllegalArgumentException("prefix must be empty or end in '/'");
    }
    this.prefix = prefix;
  }

  /** {@inheritDoc} */
  @Override
  public List<String> list(String kind) throws IOException {
    final String markers = prefix + PUBLISHED_SEGMENT + plainName(kind, "kind") + "/";
    final List<String> ids = new ArrayList<>();
    try {
      String continuationToken = null;
      do {
        final ListObjectsV2Response page = client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(bucket).prefix(markers).continuationToken(continuationToken).build());
        for (S3Object object : page.contents()) {
          final String id = object.key().substring(markers.length());
          if (id.isEmpty() || id.indexOf('/') >= 0) {
            throw new IOException("Vocabulary kind '" + kind
                + "' holds a foreign entry: " + object.key());
          }
          ids.add(id);
        }
        continuationToken = page.nextContinuationToken();
      } while (continuationToken != null);
    } catch (SdkException e) {
      throw new IOException("Failed to list vocabulary artifacts of kind '" + kind + "'", e);
    }
    ids.sort(null);
    return List.copyOf(ids);
  }

  /** {@inheritDoc} */
  @Override
  public InputStream read(String kind, String artifactId, String entryName) throws IOException {
    plainName(kind, "kind");
    plainName(artifactId, "artifactId");
    plainName(entryName, "entryName");
    if (!published(kind, artifactId)) {
      throw new IOException("Vocabulary artifact '" + artifactId
          + "' of kind '" + kind + "' is not published");
    }
    try {
      return client.getObject(GetObjectRequest.builder()
          .bucket(bucket).key(entryKey(kind, artifactId, entryName)).build());
    } catch (NoSuchKeyException e) {
      throw new IOException("Vocabulary entry '" + entryName + "' of artifact '"
          + artifactId + "' does not exist", e);
    } catch (SdkException e) {
      throw new IOException("Failed to read vocabulary entry '" + entryName
          + "' of artifact '" + artifactId + "'", e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public ArtifactWriter write(String kind, String artifactId) throws IOException {
    plainName(kind, "kind");
    plainName(artifactId, "artifactId");
    return new StagedArtifact(kind, artifactId);
  }

  /** {@inheritDoc} */
  @Override
  public boolean delete(String kind, String artifactId) throws IOException {
    plainName(kind, "kind");
    plainName(artifactId, "artifactId");
    if (!published(kind, artifactId)) {
      return false;
    }
    try {
      // The marker goes first so no reader can resolve the artifact while its
      // entries disappear underneath it.
      client.deleteObject(DeleteObjectRequest.builder()
          .bucket(bucket).key(markerKey(kind, artifactId)).build());
      final String entries = prefix + ARTIFACTS_SEGMENT + kind + "/" + artifactId + "/";
      String continuationToken = null;
      do {
        final ListObjectsV2Response page = client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(bucket).prefix(entries).continuationToken(continuationToken).build());
        for (S3Object object : page.contents()) {
          client.deleteObject(DeleteObjectRequest.builder()
              .bucket(bucket).key(object.key()).build());
        }
        continuationToken = page.nextContinuationToken();
      } while (continuationToken != null);
    } catch (SdkException e) {
      throw new IOException("Failed to delete vocabulary artifact '" + artifactId
          + "' of kind '" + kind + "'", e);
    }
    return true;
  }

  /** Tests whether the publication marker of one artifact exists. */
  private boolean published(String kind, String artifactId) throws IOException {
    try {
      client.headObject(HeadObjectRequest.builder()
          .bucket(bucket).key(markerKey(kind, artifactId)).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (SdkException e) {
      throw new IOException("Failed to probe vocabulary artifact '" + artifactId
          + "' of kind '" + kind + "'", e);
    }
  }

  /** Builds the object key of one artifact entry. */
  private String entryKey(String kind, String artifactId, String entryName) {
    return prefix + ARTIFACTS_SEGMENT + kind + "/" + artifactId + "/" + entryName;
  }

  /** Builds the object key of one artifact publication marker. */
  private String markerKey(String kind, String artifactId) {
    return prefix + PUBLISHED_SEGMENT + kind + "/" + artifactId;
  }

  /**
   * Validates a plain name: not blank, no separators, and never a path traversal token.
   *
   * @param name The name to validate.
   * @param what The name's role, for the error message.
   *
   * @return The validated name.
   * @throws IllegalArgumentException If {@code name} is not a plain name.
   */
  private static String plainName(String name, String what) {
    if (name == null || name.isBlank() || name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException(what + " must be a plain name, was '" + name + "'");
    }
    for (int i = 0; i < name.length(); i++) {
      final char character = name.charAt(i);
      if (character == '/' || character == '\\' || character == 0) {
        throw new IllegalArgumentException(what + " must be a plain name, was '" + name + "'");
      }
    }
    return name;
  }

  /** One staged artifact: entries buffer in a local temporary directory until commit. */
  private final class StagedArtifact implements ArtifactWriter {

    private final String kind;
    private final String artifactId;
    private final Path staging;
    private final Map<String, Path> entries = new LinkedHashMap<>();
    private boolean committed;

    /** Creates local staging for one artifact. */
    private StagedArtifact(String kind, String artifactId) throws IOException {
      this.kind = kind;
      this.artifactId = artifactId;
      this.staging = Files.createTempDirectory("opennlp-s3-vocabulary-");
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream entry(String entryName) throws IOException {
      plainName(entryName, "entryName");
      if (committed) {
        throw new IOException("Artifact '" + artifactId + "' is already committed");
      }
      if (entries.containsKey(entryName)) {
        throw new IOException("Entry '" + entryName + "' is already staged for artifact '"
            + artifactId + "'");
      }
      final Path file = staging.resolve(entryName);
      entries.put(entryName, file);
      return Files.newOutputStream(file);
    }

    /** {@inheritDoc} */
    @Override
    public void commit() throws IOException {
      if (committed) {
        return;
      }
      if (published(kind, artifactId)) {
        throw new IOException("Vocabulary artifact '" + artifactId + "' of kind '"
            + kind + "' is already published");
      }
      try {
        for (Map.Entry<String, Path> entry : entries.entrySet()) {
          client.putObject(PutObjectRequest.builder()
                  .bucket(bucket).key(entryKey(kind, artifactId, entry.getKey())).build(),
              RequestBody.fromFile(entry.getValue()));
        }
        // The marker PUT is the single atomic publication step.
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(markerKey(kind, artifactId)).build(),
            RequestBody.fromString(String.join("\n", entries.keySet())));
      } catch (SdkException e) {
        throw new IOException("Failed to publish vocabulary artifact '" + artifactId
            + "' of kind '" + kind + "'", e);
      }
      committed = true;
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
      for (Path file : entries.values()) {
        Files.deleteIfExists(file);
      }
      if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
        Files.delete(staging);
      }
    }
  }
}
