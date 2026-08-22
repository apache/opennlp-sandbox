/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.apache.opennlp.grpc.vocabulary.store.ArtifactDigests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogModelBootstrapTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void addsVerifiedParserAndChunkerPathsBeforeRegistryConstruction() throws Exception {
    final Path root = temporaryDirectory.resolve("catalog");
    final CatalogModel parser = model(modelSource("gum-parser"), "gum-parser",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER);
    final CatalogModel chunker = model(modelSource("gum-chunker"), "gum-chunker",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_CHUNKER);
    install(root, List.of(parser, chunker), parser);
    install(root, List.of(parser, chunker), chunker);

    final Map<String, String> prepared = CatalogModelBootstrap.prepare(
        Map.of(CatalogModelStore.CATALOG_ROOT_KEY, root.toString()),
        List.of(parser, chunker));

    assertEquals(root.resolve(parser.descriptor().getCatalogId()).resolve("model.bin")
            .toAbsolutePath().normalize().toString(),
        prepared.get("model.parser.gum-parser.path"));
    assertEquals(root.resolve(chunker.descriptor().getCatalogId()).resolve("model.bin")
            .toAbsolutePath().normalize().toString(),
        prepared.get("model.chunker.gum-chunker.path"));
  }

  @Test
  void rejectsTamperedParserBytesBeforePublishingARegistryPath() throws Exception {
    final Path root = temporaryDirectory.resolve("tamper-catalog");
    final CatalogModel parser = install(root, "gum-parser",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER);
    Files.write(root.resolve(parser.descriptor().getCatalogId()).resolve("model.bin"),
        new byte[] {9, 9, 9, 9});

    final IOException failure = assertThrows(IOException.class,
        () -> CatalogModelBootstrap.prepare(
            Map.of(CatalogModelStore.CATALOG_ROOT_KEY, root.toString()), List.of(parser)));

    assertTrue(failure.getMessage().contains("size or SHA-256"));
  }

  @Test
  void refusesToReplaceAnOperatorConfiguredModelPath() throws Exception {
    final Path root = temporaryDirectory.resolve("collision-catalog");
    final CatalogModel parser = install(root, "gum-parser",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER);

    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> CatalogModelBootstrap.prepare(Map.of(
            CatalogModelStore.CATALOG_ROOT_KEY, root.toString(),
            "model.parser.gum-parser.path", "/operator/parser.bin"), List.of(parser)));

    assertTrue(failure.getMessage().contains("already configured"));
  }

  private CatalogModel install(Path root, String modelId, ModelArtifactRole role)
      throws Exception {
    final Path source = modelSource(modelId);
    final CatalogModel model = model(source, modelId, role);
    install(root, List.of(model), model);
    return model;
  }

  private void install(Path root, List<CatalogModel> models, CatalogModel model)
      throws Exception {
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    new CatalogModelStore(root, models, trainingStore(registry), registry,
        (file, target) -> {
          Files.createDirectories(target.getParent());
          final Path source = temporaryDirectory.resolve(
              model.descriptor().getModelId()).resolve(file.relativePath());
          Files.copy(source, target);
        }).install(InstallModelRequest.newBuilder()
            .setCatalogId(model.descriptor().getCatalogId())
            .setRevision(model.descriptor().getRevision())
            .setLicenseName(model.descriptor().getLicenseName())
            .setLicenseAcknowledged(true)
            .build(), ignored -> { }, () -> false);
  }

  private Path modelSource(String modelId) throws IOException {
    final Path source = Files.createDirectories(temporaryDirectory.resolve(modelId));
    Files.write(source.resolve("model.bin"), new byte[] {1, 2, 3, 4});
    return source;
  }

  private StaticModelArtifactStore trainingStore(TrainedModelEmbeddingProvider registry)
      throws Exception {
    final Path artifacts = temporaryDirectory.resolve("vocabulary-artifacts");
    final Map<String, String> configuration =
        Map.of("vocabulary.artifact_root", artifacts.toString());
    return StaticModelArtifactStore.fromConfiguration(configuration,
        VocabularyArtifactStore.fromConfiguration(
            configuration, DictionaryFormatRegistry.discover()),
        new TrainingTestSupport.RecordingTrainer(), registry);
  }

  private static CatalogModel model(
      Path source, String modelId, ModelArtifactRole role) throws IOException {
    final Path modelFile = source.resolve("model.bin");
    final ArtifactDigests.SizedDigest digest;
    try (InputStream input = Files.newInputStream(modelFile)) {
      digest = ArtifactDigests.digest(input);
    }
    final CatalogFile file = new CatalogFile(Path.of("model.bin"),
        URI.create("https://example.invalid/model.bin"), digest.size(), digest.hexDigest());
    return new CatalogModel(ModelCatalogDescriptor.newBuilder()
        .setCatalogId(modelId + "-catalog")
        .setDisplayName(modelId)
        .setRole(role)
        .setModelId(modelId)
        .setSourceUri("https://example.invalid/model")
        .setRevision("0123456789abcdef0123456789abcdef01234567")
        .setLicenseName("Test-License")
        .setLicenseUri("https://example.invalid/license")
        .setByteSize(digest.size())
        .addLanguages("en")
        .setDescription("Test model")
        .build(), List.of(file));
  }
}
