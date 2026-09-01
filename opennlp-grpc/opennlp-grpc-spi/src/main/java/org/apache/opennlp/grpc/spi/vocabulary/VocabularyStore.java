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
package org.apache.opennlp.grpc.spi.vocabulary;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Durable storage for published vocabulary artifacts, the swappable layer beneath the
 * artifact business logic. An artifact is a named group of entries under one kind
 * (for example {@code vocabularies/vocabulary-<uuid>} holding a data file and a
 * descriptor). Writes are staged and become visible only through one atomic
 * {@link ArtifactWriter#commit()}, so readers never observe a partial artifact.
 *
 * <p>Implementations are selected by the scheme of the configured artifact root through
 * the server's vocabulary store resolver; the filesystem implementation ships here, and remote tiers
 * such as S3 arrive as separate JARs contributing a {@link VocabularyStoreProvider}.</p>
 */
public interface VocabularyStore {

  /**
   * Lists the published artifact ids beneath one kind.
   *
   * @param kind The artifact kind. Must be a plain name.
   * @return The published ids in lexicographic order. Never {@code null}.
   * @throws IOException Thrown if listing fails or the kind holds a foreign entry.
   * @throws IllegalArgumentException Thrown if {@code kind} is not a plain name.
   */
  List<String> list(String kind) throws IOException;

  /**
   * Opens one entry of a published artifact.
   *
   * @param kind The artifact kind. Must be a plain name.
   * @param artifactId The published artifact id. Must be a plain name.
   * @param entryName The entry to open. Must be a plain name.
   * @return The entry content. The caller must close it.
   * @throws IOException Thrown if the entry does not exist or cannot be opened safely.
   * @throws IllegalArgumentException Thrown if a name is not a plain name.
   */
  InputStream read(String kind, String artifactId, String entryName) throws IOException;

  /**
   * Stages one new artifact. Nothing is visible until {@link ArtifactWriter#commit()};
   * closing the writer without committing discards everything staged.
   *
   * @param kind The artifact kind. Must be a plain name.
   * @param artifactId The id to publish under. Must be a plain name.
   * @return The staged writer. The caller must close it.
   * @throws IOException Thrown if staging cannot be created.
   * @throws IllegalArgumentException Thrown if a name is not a plain name.
   */
  ArtifactWriter write(String kind, String artifactId) throws IOException;

  /**
   * Deletes one published artifact and every entry it holds.
   *
   * @param kind The artifact kind. Must be a plain name.
   * @param artifactId The published artifact id. Must be a plain name.
   * @return {@code true} when the artifact existed and was deleted.
   * @throws IOException Thrown if deletion fails part way.
   * @throws IllegalArgumentException Thrown if a name is not a plain name.
   */
  boolean delete(String kind, String artifactId) throws IOException;

  /** One staged artifact: entries accumulate invisibly until the atomic commit. */
  interface ArtifactWriter extends Closeable {

    /**
     * Opens one staged entry for writing.
     *
     * @param entryName The entry name. Must be a plain name, new to this artifact.
     * @return The entry sink. The caller must close it before the next entry.
     * @throws IOException Thrown if the staged entry cannot be created.
     * @throws IllegalArgumentException Thrown if the name is not a plain name.
     */
    OutputStream entry(String entryName) throws IOException;

    /**
     * Publishes every staged entry atomically. After a successful commit,
     * {@link #close()} is a no-op.
     *
     * @throws IOException Thrown if the artifact id is already published or the
     *         atomic publication fails.
     */
    void commit() throws IOException;

    /** Discards the staged artifact when it was not committed. */
    @Override
    void close() throws IOException;
  }
}
