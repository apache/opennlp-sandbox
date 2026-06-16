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
package org.apache.opennlp.grpc.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.ComponentModelRef;
import org.apache.opennlp.grpc.v1.ComponentType;

/**
 * Indexes loaded model artifacts by {@link ComponentType} and SHA-256 hash for catalog
 * reporting and request-time pinning via {@code ModelBundleRef.component_models}.
 */
public final class ModelArtifactRegistry {

  /**
   * One registered model artifact.
   *
   * @param componentType The pipeline component role.
   * @param hash          The lowercase hex SHA-256 digest.
   * @param name          The logical model name or id.
   */
  public record Artifact(ComponentType componentType, String hash, String name) {
    /** Validates non-null fields. */
    public Artifact {
      Objects.requireNonNull(componentType, "componentType");
      Objects.requireNonNull(hash, "hash");
      Objects.requireNonNull(name, "name");
    }
  }

  private final Map<ComponentType, Map<String, Artifact>> byTypeAndHash;

  /** Creates a registry from a fully populated builder map. */
  private ModelArtifactRegistry(Map<ComponentType, Map<String, Artifact>> byTypeAndHash) {
    this.byTypeAndHash = Map.copyOf(byTypeAndHash);
  }

  /**
   * Returns every artifact registered for {@code componentType}.
   *
   * @param componentType The component role. Must not be {@code null}.
   *
   * @return The artifacts for that type, possibly empty. Never {@code null}.
   */
  public List<Artifact> artifacts(ComponentType componentType) {
    final Map<String, Artifact> artifacts = byTypeAndHash.get(componentType);
    if (artifacts == null) {
      return List.of();
    }
    return List.copyOf(artifacts.values());
  }

  /**
   * Looks up a registered artifact by type and hash.
   *
   * @param componentType The component role. Must not be {@code null}.
   * @param hash          The lowercase hex SHA-256 digest. Must not be {@code null}.
   *
   * @return The matching artifact, if any.
   */
  public Optional<Artifact> find(ComponentType componentType, String hash) {
    final Map<String, Artifact> artifacts = byTypeAndHash.get(componentType);
    if (artifacts == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(artifacts.get(normalizeHash(hash)));
  }

  /**
   * Validates {@code componentModels} against the loaded catalog.
   *
   * @param componentModels The per-component pins from the request profile.
   *
   * @throws AnalysisException If a pin is malformed or does not match a loaded artifact.
   */
  public void validateComponentModels(List<ComponentModelRef> componentModels) {
    if (componentModels == null || componentModels.isEmpty()) {
      return;
    }
    final Set<ComponentType> seenTypes = new HashSet<>();
    for (ComponentModelRef ref : componentModels) {
      final ComponentType type = ref.getComponentType();
      if (type == ComponentType.COMPONENT_TYPE_UNSPECIFIED || type == ComponentType.UNRECOGNIZED) {
        throw AnalysisException.invalidArgument(
            "component_models.component_type must identify a pipeline component");
      }
      if (!seenTypes.add(type)) {
        throw AnalysisException.invalidArgument(
            "component_models must contain at most one entry per component_type (duplicate "
                + type + ")");
      }
      final String hash = ref.getModelHash();
      if (hash == null || hash.isBlank()) {
        throw AnalysisException.invalidArgument(
            "component_models.model_hash is required for " + type);
      }
      if (find(type, hash).isEmpty()) {
        throw AnalysisException.notFound(
            "No loaded model artifact matches component_models entry " + type + " hash '"
                + hash + "'");
      }
    }
  }

  /**
   * Returns the logical model name bound to an embedder artifact hash, when pinned.
   *
   * @param hash The lowercase hex SHA-256 digest. Must not be {@code null}.
   *
   * @return The embedding model id, if the hash refers to a loaded embedder artifact.
   */
  public Optional<String> embedderModelIdForHash(String hash) {
    return find(ComponentType.COMPONENT_TYPE_EMBEDDER, hash).map(Artifact::name);
  }

  /**
   * Creates a registry builder.
   *
   * @return A new builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Mutable builder for {@link ModelArtifactRegistry}. */
  public static final class Builder {
    /** Prevents direct instantiation; use {@link ModelArtifactRegistry#builder()}. */
    private Builder() {
    }

    private final Map<ComponentType, Map<String, Artifact>> byTypeAndHash = new EnumMap<>(ComponentType.class);

    /**
     * Registers one artifact.
     *
     * @param componentType The component role. Must not be {@code null}.
     * @param hash          The lowercase hex SHA-256 digest. Must not be blank.
     * @param name          The logical model name or id. Must not be blank.
     *
     * @return This builder.
     */
    public Builder register(ComponentType componentType, String hash, String name) {
      Objects.requireNonNull(componentType, "componentType");
      if (hash == null || hash.isBlank()) {
        throw new IllegalArgumentException("hash must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
      }
      final String normalizedHash = normalizeHash(hash);
      byTypeAndHash
          .computeIfAbsent(componentType, ignored -> new HashMap<>())
          .put(normalizedHash, new Artifact(componentType, normalizedHash, name));
      return this;
    }

    /**
     * Builds an immutable registry.
     *
     * @return The completed registry.
     */
    public ModelArtifactRegistry build() {
      final Map<ComponentType, Map<String, Artifact>> copy = new EnumMap<>(ComponentType.class);
      for (Map.Entry<ComponentType, Map<String, Artifact>> entry : byTypeAndHash.entrySet()) {
        copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
      }
      return new ModelArtifactRegistry(copy);
    }
  }

  /** Normalizes a client-supplied hash to lowercase trimmed hex. */
  private static String normalizeHash(String hash) {
    return hash.trim().toLowerCase();
  }
}
