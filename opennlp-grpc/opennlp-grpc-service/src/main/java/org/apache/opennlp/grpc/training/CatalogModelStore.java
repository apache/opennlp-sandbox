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

import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.google.protobuf.Timestamp;
import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.tools.util.ResourceInstaller;
import org.apache.opennlp.grpc.v1.InstallModelProgress;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelStage;
import org.apache.opennlp.grpc.v1.InstalledModelDescriptor;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.apache.opennlp.grpc.vocabulary.store.ArtifactDigests;

/**
 * Node-local store for immutable models selected from the discovered model catalog
 * (see {@link ModelCatalogs}). It
 * downloads only catalog-owned URIs, verifies exact sizes and SHA-256 values, publishes
 * atomically, and registers the model with training or embedding services.
 */
public final class CatalogModelStore {

  /** Configuration key enabling node-local catalog installation. */
  public static final String CATALOG_ROOT_KEY = "model.catalog_root";

  private static final String DESCRIPTOR_FILE = "installed-model.pb";
  private static final String STAGING_PREFIX = ".install-";
  private static final int MAX_CATALOG_MODELS = 32;
  private static final Duration CATALOG_READ_TIMEOUT = Duration.ofMinutes(30);

  private final Path root;
  private final Map<String, CatalogModel> catalog;
  private final StaticModelArtifactStore trainingStore;
  private final TrainedModelEmbeddingProvider embeddingRegistry;
  private final CatalogFileInstaller fileInstaller;
  private final LongSupplier usableBytes;

  /** Free bytes an installation must leave behind on the catalog root. */
  static final long FREE_SPACE_MARGIN_BYTES = 64L * 1024 * 1024;
  private final CatalogTreeDeleter treeDeleter;
  private final Map<String, InstalledModelDescriptor> installed = new ConcurrentHashMap<>();
  private final Semaphore installPermit = new Semaphore(1);

  /**
   * Creates the standard catalog from server configuration.
   *
   * @param configuration Server configuration, including an optional catalog root.
   * @param trainingStore Store that accepts installed teacher models.
   * @param embeddingRegistry Registry that accepts installed static embedding models.
   * @return A catalog store, with installation disabled when no root is configured.
   * @throws IOException If the configured root or an installed model is invalid.
   * @throws IllegalArgumentException If an argument or catalog entry is invalid.
   */
  public static CatalogModelStore fromConfiguration(
      Map<String, String> configuration,
      StaticModelArtifactStore trainingStore,
      TrainedModelEmbeddingProvider embeddingRegistry) throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final Path root = configuredRoot(configuration);
    return new CatalogModelStore(root, ModelCatalogs.discover(), trainingStore,
        embeddingRegistry, CatalogModelStore::installFile);
  }

  /**
   * Creates a catalog store with injectable catalog metadata and file installation.
   *
   * @param root Node-local installation root, or {@code null} to disable installation.
   * @param models Immutable catalog entries.
   * @param trainingStore Store that accepts teacher models.
   * @param embeddingRegistry Registry that accepts static embedding models.
   * @param fileInstaller Bounded file installation operation.
   * @throws IOException If the root or an existing installation is invalid.
   * @throws IllegalArgumentException If an argument or catalog entry is invalid.
   */
  CatalogModelStore(
      Path root,
      List<CatalogModel> models,
      StaticModelArtifactStore trainingStore,
      TrainedModelEmbeddingProvider embeddingRegistry,
      CatalogFileInstaller fileInstaller) throws IOException {
    this(root, models, trainingStore, embeddingRegistry, fileInstaller,
        CatalogModelStore::deleteTree);
  }

  /**
   * Creates a catalog store with injectable installation and cleanup operations.
   *
   * @param root Node-local installation root, or {@code null} to disable installation.
   * @param models Immutable catalog entries.
   * @param trainingStore Store that accepts teacher models.
   * @param embeddingRegistry Registry that accepts static embedding models.
   * @param fileInstaller Bounded file installation operation.
   * @param treeDeleter Staging and rollback cleanup operation.
   * @throws IOException If the root or an existing installation is invalid.
   * @throws IllegalArgumentException If an argument or catalog entry is invalid.
   */
  CatalogModelStore(
      Path root,
      List<CatalogModel> models,
      StaticModelArtifactStore trainingStore,
      TrainedModelEmbeddingProvider embeddingRegistry,
      CatalogFileInstaller fileInstaller,
      CatalogTreeDeleter treeDeleter) throws IOException {
    this(root, models, trainingStore, embeddingRegistry, fileInstaller, treeDeleter,
        root == null ? () -> Long.MAX_VALUE : () -> usableSpace(root));
  }

  CatalogModelStore(
      Path root,
      List<CatalogModel> models,
      StaticModelArtifactStore trainingStore,
      TrainedModelEmbeddingProvider embeddingRegistry,
      CatalogFileInstaller fileInstaller,
      CatalogTreeDeleter treeDeleter,
      LongSupplier usableBytes) throws IOException {
    // An empty catalog is legal: without the opennlp-grpc-installer add-on no provider
    // contributes entries, and the store then lists nothing and refuses installs honestly.
    if (models == null || models.size() > MAX_CATALOG_MODELS) {
      throw new IllegalArgumentException("models must contain at most "
          + MAX_CATALOG_MODELS + " entries");
    }
    if (trainingStore == null) {
      throw new IllegalArgumentException("trainingStore must not be null");
    }
    if (embeddingRegistry == null) {
      throw new IllegalArgumentException("embeddingRegistry must not be null");
    }
    if (fileInstaller == null) {
      throw new IllegalArgumentException("fileInstaller must not be null");
    }
    if (treeDeleter == null) {
      throw new IllegalArgumentException("treeDeleter must not be null");
    }
    if (usableBytes == null) {
      throw new IllegalArgumentException("usableBytes must not be null");
    }
    final Map<String, CatalogModel> byId = new TreeMap<>();
    for (CatalogModel model : models) {
      final String id = model.descriptor().getCatalogId();
      requireIdentifier(id, "catalog_id");
      if (byId.putIfAbsent(id, model) != null) {
        throw new IllegalArgumentException("Duplicate catalog_id '" + id + "'");
      }
    }
    this.root = root;
    this.catalog = Map.copyOf(byId);
    this.trainingStore = trainingStore;
    this.embeddingRegistry = embeddingRegistry;
    this.fileInstaller = fileInstaller;
    this.treeDeleter = treeDeleter;
    this.usableBytes = usableBytes;
    if (root != null) {
      createRoot(root);
      loadInstalled();
    }
  }

  /**
   * Reports whether this node has a catalog installation root.
   *
   * @return Whether catalog installation is enabled.
   */
  public boolean installsEnabled() {
    return root != null;
  }

  /**
   * Lists the public catalog entries.
   *
   * @return Catalog entries in stable catalog-id order.
   */
  public List<ModelCatalogDescriptor> catalogModels() {
    return catalog.values().stream().map(CatalogRoles::describe)
        .sorted(Comparator.comparing(ModelCatalogDescriptor::getCatalogId)).toList();
  }

  /**
   * Lists models installed on this node.
   *
   * @return Installed models in stable catalog-id order.
   */
  public List<InstalledModelDescriptor> installedModels() {
    return installed.values().stream()
        .sorted(Comparator.comparing(model -> model.getCatalog().getCatalogId())).toList();
  }

  /**
   * Downloads, verifies, atomically publishes, and activates one catalog model.
   *
   * @param request Pinned catalog identity and explicit license acknowledgement.
   * @param progress Receives bounded per-file progress.
   * @param cancelled Transport cancellation probe.
   * @return Installed and active model descriptor.
   * @throws IOException If download, verification, publication, or loading fails.
   * @throws IllegalArgumentException If the request does not exactly match the catalog.
   * @throws IllegalStateException If installation is disabled or the model already exists.
   * @throws CancellationException If cancellation is observed before publication.
   */
  public InstalledModelDescriptor install(
      InstallModelRequest request,
      Consumer<InstallModelProgress> progress,
      BooleanSupplier cancelled) throws IOException {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    if (progress == null) {
      throw new IllegalArgumentException("progress must not be null");
    }
    if (cancelled == null) {
      throw new IllegalArgumentException("cancelled must not be null");
    }
    requireEnabled();
    final CatalogModel model = validateRequest(request);
    if (!installPermit.tryAcquire()) {
      throw new ConcurrentModelInstallException("another model installation is active");
    }
    try {
      return installAdmitted(model, progress, cancelled);
    } finally {
      installPermit.release();
    }
  }

  /** Performs an admitted installation while the single install permit is held. */
  private InstalledModelDescriptor installAdmitted(
      CatalogModel model,
      Consumer<InstallModelProgress> progress,
      BooleanSupplier cancelled) throws IOException {
    final String catalogId = model.descriptor().getCatalogId();
    if (installed.containsKey(catalogId)
        || Files.exists(root.resolve(catalogId), LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("Catalog model '" + catalogId + "' is already installed");
    }
    requireFreeSlot(model);
    requireFreeSpace(model);
    requireActive(cancelled);
    progress.accept(progress(model, InstallModelStage.INSTALL_MODEL_STAGE_VALIDATING,
        "", 0, 0, "Validated catalog identity and license acknowledgement"));
    final Path stagingPath =
        Files.createDirectory(root.resolve(STAGING_PREFIX + UUID.randomUUID()));
    try (StagingDirectory staging = new StagingDirectory(stagingPath)) {
      long completedBytes = 0;
      int completedFiles = 0;
      for (CatalogFile file : model.files()) {
        requireActive(cancelled);
        progress.accept(progress(model, InstallModelStage.INSTALL_MODEL_STAGE_DOWNLOADING,
            portablePath(file.relativePath()), completedFiles, completedBytes,
            "Downloading " + portablePath(file.relativePath())));
        final Path target = staging.path().resolve(file.relativePath()).normalize();
        if (!target.startsWith(staging.path())) {
          throw new IOException("Catalog path escapes staging directory");
        }
        try {
          fileInstaller.install(file, target);
        } catch (CatalogChecksumException e) {
          throw e;
        } catch (IOException e) {
          // The source host is safe to show; the transport detail stays in the log.
          throw new CatalogDownloadException("Download of " + portablePath(file.relativePath())
              + " from " + file.source().getHost() + " failed", e);
        }
        verifyFile(file, target);
        completedFiles++;
        completedBytes = Math.addExact(completedBytes, file.byteSize());
      }
      requireActive(cancelled);
      progress.accept(progress(model, InstallModelStage.INSTALL_MODEL_STAGE_VERIFYING,
          "", completedFiles, completedBytes, "Verified every catalog file"));
      verifyLayout(model, staging.path(), false);
      final String artifactHash = artifactHash(model);
      progress.accept(progress(model, InstallModelStage.INSTALL_MODEL_STAGE_LOADING,
          "", completedFiles, completedBytes, loadingMessage(model)));
      final StaticEmbeddingModel staticModel = model.descriptor().getRole()
          == ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING
              ? StaticEmbeddingModel.load(staging.path()) : null;
      verifyLoadedDimension(model, staticModel);
      final InstalledModelDescriptor descriptor = InstalledModelDescriptor.newBuilder()
          .setCatalog(CatalogRoles.describe(model))
          .setArtifactHash(artifactHash)
          .setByteSize(model.descriptor().getByteSize())
          .setInstalledAt(now())
          .setLoaded(!requiresRestart(model.descriptor().getRole()))
          .build();
      try (OutputStream output =
          Files.newOutputStream(staging.path().resolve(DESCRIPTOR_FILE))) {
        descriptor.writeTo(output);
      }
      requireActive(cancelled);
      final Path published = root.resolve(catalogId);
      try {
        Files.move(staging.path(), published, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        throw new IOException("Catalog root does not support atomic model publication", e);
      }
      try {
        activate(model, published, artifactHash, staticModel);
        installed.put(catalogId, descriptor);
      } catch (IllegalArgumentException e) {
        try {
          treeDeleter.delete(published);
        } catch (IOException cleanupFailure) {
          e.addSuppressed(cleanupFailure);
        }
        throw e;
      }
      progress.accept(progress(model, InstallModelStage.INSTALL_MODEL_STAGE_PUBLISHED,
          "", completedFiles, completedBytes, publicationMessage(model)));
      return descriptor;
    }
  }

  /** Returns the catalog model named by an acknowledged, pinned request. */
  private CatalogModel validateRequest(InstallModelRequest request) {
    final CatalogModel model = catalog.get(request.getCatalogId());
    if (model == null) {
      throw new IllegalArgumentException(catalog.isEmpty()
          ? "Unknown catalog_id '" + request.getCatalogId() + "'; the model catalog is empty "
              + "because no catalog provider (opennlp-grpc-installer) is on the classpath"
          : "Unknown catalog_id '" + request.getCatalogId() + "'");
    }
    if (!request.getLicenseAcknowledged()) {
      throw new IllegalArgumentException("license_acknowledged must be true");
    }
    if (!model.descriptor().getRevision().equals(request.getRevision())) {
      throw new IllegalArgumentException("revision does not match the immutable catalog entry");
    }
    if (!model.descriptor().getLicenseName().equals(request.getLicenseName())) {
      throw new IllegalArgumentException(
          "license_name does not match the immutable catalog entry");
    }
    return model;
  }

  /** Verifies and activates every model already present under the catalog root. */
  private void loadInstalled() throws IOException {
    cleanupStagingDirectories();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        final String catalogId = entry.getFileName().toString();
        final CatalogModel model = catalog.get(catalogId);
        if (model == null) {
          throw new IOException("Unknown entry in model catalog root: " + entry);
        }
        if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("Catalog model entry is not a directory: " + entry);
        }
        final InstalledModelDescriptor descriptor = verifyInstalled(model, entry);
        final StaticEmbeddingModel staticModel = model.descriptor().getRole()
            == ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING
                ? StaticEmbeddingModel.load(entry) : null;
        verifyLoadedDimension(model, staticModel);
        activate(model, entry, descriptor.getArtifactHash(), staticModel);
        installed.put(catalogId, requiresRestart(model.descriptor().getRole())
            ? descriptor.toBuilder().setLoaded(true).build() : descriptor);
      }
    }
  }

  /** Registers one verified model with its role-specific runtime store. */
  private void activate(
      CatalogModel model, Path directory, String artifactHash, StaticEmbeddingModel staticModel) {
    final ModelCatalogDescriptor descriptor = model.descriptor();
    if (descriptor.getRole() == ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING) {
      embeddingRegistry.register(descriptor.getModelId(), staticModel, artifactHash);
    } else if (descriptor.getRole()
        == ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER) {
      trainingStore.registerCatalogTeacher(
          descriptor.getModelId(), descriptor.getDisplayName(), directory,
          new TeacherProvenance(descriptor.getSourceUri(), descriptor.getRevision(),
              descriptor.getLicenseName(), descriptor.getLicenseUri(),
              descriptor.getLanguagesList()));
    } else if (!requiresRestart(descriptor.getRole())) {
      throw new IllegalArgumentException("Unsupported catalog model role " + descriptor.getRole());
    }
  }

  /**
   * Strips the fields a listing derives from the role and files, leaving the pinned entry.
   *
   * @param descriptor A catalog descriptor, bare or decorated.
   * @return The descriptor without format, unlocks, restart flag, or files.
   */
  private static ModelCatalogDescriptor bare(ModelCatalogDescriptor descriptor) {
    return descriptor.toBuilder().clearFormat().clearUnlocks().clearRequiresRestart()
        .clearFiles().build();
  }

  /** Verifies that a loaded static table has its catalog dimension. */
  private static void verifyLoadedDimension(
      CatalogModel model, StaticEmbeddingModel staticModel) throws IOException {
    if (staticModel != null
        && staticModel.dimension() != model.descriptor().getDimension()) {
      throw new IOException("Loaded static embedding dimension " + staticModel.dimension()
          + " does not match catalog dimension " + model.descriptor().getDimension());
    }
  }

  /** Downloads one immutable catalog file through {@link ResourceInstaller}. */
  private static void installFile(CatalogFile file, Path target) throws IOException {
    Files.createDirectories(target.getParent());
    final ResourceInstaller.Limits limits = downloadLimits(file.byteSize());
    final Path installed = ResourceInstaller.install(
        file.source(), target.getParent(), file.sha256(), limits);
    verifyInstallDirectory(target, installed);
  }

  /**
   * Verifies the publication directory returned by {@link ResourceInstaller}.
   *
   * @param target Expected installed file.
   * @param installed Directory returned by the installer.
   * @throws IOException If the installer returned a different directory.
   */
  static void verifyInstallDirectory(Path target, Path installed) throws IOException {
    final Path expectedDirectory = target.getParent().toAbsolutePath().normalize();
    if (!installed.toAbsolutePath().normalize().equals(expectedDirectory)) {
      throw new IOException("Installer returned unexpected catalog directory " + installed);
    }
  }

  /**
   * Creates the download limits for one immutable catalog file.
   *
   * @param byteSize Exact expected file size.
   * @return Limits that admit exactly that file size and a long-running read.
   */
  static ResourceInstaller.Limits downloadLimits(long byteSize) {
    return ResourceInstaller.Limits.builder()
        .readTimeout(CATALOG_READ_TIMEOUT)
        .maxDownloadBytes(byteSize)
        .maxExpandedBytes(byteSize)
        .maxEntries(1)
        .build();
  }

  /** Verifies the type, size, and digest of one installed file. */
  private static void verifyFile(CatalogFile expected, Path file) throws IOException {
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Catalog file is absent or not regular: " + file);
    }
    try (InputStream input = Files.newInputStream(file)) {
      final ArtifactDigests.SizedDigest actual = ArtifactDigests.digest(input);
      if (actual.size() != expected.byteSize()
          || !actual.hexDigest().equals(expected.sha256())) {
        throw new CatalogChecksumException("Catalog file failed size or SHA-256 verification: "
            + file.getFileName() + "; the download is corrupt or the source changed");
      }
    }
  }

  /**
   * Verifies that a model directory contains exactly its declared files.
   *
   * @param model Immutable catalog entry.
   * @param directory Published or staged model directory.
   * @param requireDescriptor Whether the installed descriptor must be present.
   * @throws IOException If a file or the complete layout differs from the catalog.
   */
  static void verifyLayout(
      CatalogModel model, Path directory, boolean requireDescriptor) throws IOException {
    final Set<Path> expected = new HashSet<>();
    for (CatalogFile file : model.files()) {
      expected.add(file.relativePath());
      verifyFile(file, directory.resolve(file.relativePath()));
    }
    if (requireDescriptor) {
      expected.add(Path.of(DESCRIPTOR_FILE));
    }
    final Set<Path> actual = new HashSet<>();
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.toList()) {
        if (path.equals(directory)) {
          continue;
        }
        if (Files.isSymbolicLink(path)) {
          throw new IOException("Catalog model contains a symbolic link: " + path);
        }
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
          actual.add(directory.relativize(path));
        } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("Catalog model contains an unsupported entry: " + path);
        }
      }
    }
    if (!actual.equals(expected)) {
      throw new IOException("Catalog model layout differs from the immutable file list");
    }
  }

  /**
   * Computes the stable digest of a catalog entry's file identities.
   *
   * @param model Immutable catalog entry.
   * @return SHA-256 digest over ordered file metadata.
   */
  static String artifactHash(CatalogModel model) {
    final MessageDigest digest = ArtifactDigests.newSha256();
    for (CatalogFile file : model.files()) {
      final String line = portablePath(file.relativePath()) + "\t" + file.byteSize()
          + "\t" + file.sha256() + "\n";
      digest.update(line.getBytes(StandardCharsets.UTF_8));
    }
    return ArtifactDigests.hex(digest.digest());
  }

  /** Creates one bounded file-level progress update. */
  private static InstallModelProgress progress(
      CatalogModel model, InstallModelStage stage, String currentFile,
      int completedFiles, long completedBytes, String message) {
    return InstallModelProgress.newBuilder()
        .setStage(stage)
        .setCurrentFile(currentFile)
        .setCompletedFiles(completedFiles)
        .setTotalFiles(model.files().size())
        .setCompletedBytes(completedBytes)
        .setTotalBytes(model.descriptor().getByteSize())
        .setMessage(message)
        .build();
  }

  /** Removes staging directories left by interrupted processes. */
  private void cleanupStagingDirectories() throws IOException {
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        if (entry.getFileName().toString().startsWith(STAGING_PREFIX)) {
          treeDeleter.delete(entry);
        }
      }
    }
  }

  /**
   * Creates and validates the configured catalog root.
   *
   * @param root Configured node-local root.
   * @throws IOException If the root cannot be created or is not a regular directory.
   */
  static void createRoot(Path root) throws IOException {
    if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
      throw new IOException("model.catalog_root must not be a symbolic link");
    }
    Files.createDirectories(root);
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("model.catalog_root is not a directory: " + root);
    }
  }

  /**
   * Returns the normalized catalog root.
   *
   * @param configuration Server configuration.
   * @return Catalog root, or {@code null} when installation is disabled.
   */
  static Path configuredRoot(Map<String, String> configuration) {
    final String configured = configuration.get(CATALOG_ROOT_KEY);
    return configured == null || configured.isBlank()
        ? null : Path.of(configured.trim()).toAbsolutePath().normalize();
  }

  /**
   * Reads and verifies one published installation descriptor and every declared file.
   *
   * @param model Immutable catalog entry.
   * @param directory Published model directory.
   * @return Verified installed descriptor.
   * @throws IOException If the bytes, layout, or descriptor differ from the catalog.
   */
  static InstalledModelDescriptor verifyInstalled(CatalogModel model, Path directory)
      throws IOException {
    verifyLayout(model, directory, true);
    final InstalledModelDescriptor descriptor;
    try (InputStream input = Files.newInputStream(directory.resolve(DESCRIPTOR_FILE))) {
      descriptor = InstalledModelDescriptor.parseFrom(input);
    }
    // Records written before the listing carried derived fields hold the bare descriptor;
    // both forms describe the same pinned entry, so the comparison ignores those fields.
    if (!bare(descriptor.getCatalog()).equals(bare(model.descriptor()))
        || descriptor.getByteSize() != model.descriptor().getByteSize()
        || !descriptor.getArtifactHash().equals(artifactHash(model))) {
      throw new IOException("Installed catalog descriptor does not match '"
          + model.descriptor().getCatalogId() + "'");
    }
    return descriptor;
  }

  /** Returns whether a role can only become active during server startup. */
  private static boolean requiresRestart(ModelArtifactRole role) {
    return CatalogRoles.requiresRestart(role);
  }

  /**
   * Refuses an installation whose restart slot another installed model already claims,
   * since the next boot would otherwise refuse to start.
   *
   * @param model The entry about to be installed.
   * @throws IllegalStateException If another installed catalog model publishes the same key.
   */
  private void requireFreeSlot(CatalogModel model) {
    final String key = CatalogRoles.restartConfigurationKey(model.descriptor());
    if (key == null) {
      return;
    }
    for (InstalledModelDescriptor other : installed.values()) {
      final String claimed = CatalogRoles.restartConfigurationKey(other.getCatalog());
      if (key.equals(claimed)) {
        throw new IllegalStateException("Pipeline slot '" + key + "' is already claimed by "
            + "installed catalog model '" + other.getCatalog().getCatalogId()
            + "'; uninstall it first");
      }
    }
  }

  /**
   * Refuses an installation the catalog root cannot hold with a margin to spare.
   *
   * @param model The entry about to be installed.
   * @throws InsufficientDiskSpaceException If the usable space is short.
   */
  private void requireFreeSpace(CatalogModel model) throws InsufficientDiskSpaceException {
    final long needed = Math.addExact(model.descriptor().getByteSize(), FREE_SPACE_MARGIN_BYTES);
    final long usable = usableBytes.getAsLong();
    if (usable < needed) {
      throw new InsufficientDiskSpaceException("Not enough free space on the model catalog "
          + "root for '" + model.descriptor().getCatalogId() + "': needs "
          + mebibytes(needed) + " MiB including a " + mebibytes(FREE_SPACE_MARGIN_BYTES)
          + " MiB margin, has " + mebibytes(usable) + " MiB");
    }
  }

  /** Whole mebibytes, rounded down. */
  private static long mebibytes(long bytes) {
    return bytes / (1024 * 1024);
  }

  /**
   * Reads the usable space of the file store holding the catalog root.
   *
   * @param root The catalog root.
   * @return Usable bytes, or {@code Long.MAX_VALUE} when the store cannot be read.
   */
  private static long usableSpace(Path root) {
    try {
      return Files.getFileStore(root).getUsableSpace();
    } catch (IOException e) {
      return Long.MAX_VALUE;
    }
  }

  /** Describes the role-specific loading phase without claiming early activation. */
  private static String loadingMessage(CatalogModel model) {
    return requiresRestart(model.descriptor().getRole())
        ? "Preparing the verified model for restart activation"
        : "Loading the verified model";
  }

  /** Describes publication and any required follow-up action. */
  private static String publicationMessage(CatalogModel model) {
    final String catalogId = model.descriptor().getCatalogId();
    return requiresRestart(model.descriptor().getRole())
        ? "Installed " + catalogId + "; restart required"
        : "Installed and activated " + catalogId;
  }

  /** Requires this node to have catalog installation enabled. */
  private void requireEnabled() {
    if (!installsEnabled()) {
      throw new IllegalStateException(
          CATALOG_ROOT_KEY + " is not configured; catalog installation is disabled");
    }
  }

  /** Rejects an installation after its transport has been cancelled. */
  private static void requireActive(BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("Model installation is cancelled");
    }
  }

  /** Requires a lowercase catalog identifier accepted as one path segment. */
  private static void requireIdentifier(String value, String name) {
    if (value == null || value.isBlank() || !value.equals(value.trim())) {
      throw new IllegalArgumentException(name + " must be trimmed and nonblank");
    }
    for (int index = 0; index < value.length(); index++) {
      final char character = value.charAt(index);
      if (!((character >= 'a' && character <= 'z')
          || (character >= '0' && character <= '9') || character == '-')) {
        throw new IllegalArgumentException(name + " contains an unsupported character");
      }
    }
  }

  /** Renders a relative path with portable separators for metadata and progress. */
  private static String portablePath(Path path) {
    final StringBuilder value = new StringBuilder();
    for (Path element : path) {
      if (!value.isEmpty()) {
        value.append('/');
      }
      value.append(element);
    }
    return value.toString();
  }

  /** Returns the current time as a protobuf timestamp. */
  private static Timestamp now() {
    final Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }

  /** Deletes a directory tree without following symbolic links. */
  static void deleteTree(Path tree) throws IOException {
    if (tree == null || !Files.exists(tree, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    final List<Path> paths = new ArrayList<>();
    try (var walk = Files.walk(tree)) {
      walk.forEach(paths::add);
    }
    paths.sort(Comparator.<Path>comparingInt(Path::getNameCount).reversed());
    for (Path path : paths) {
      Files.delete(path);
    }
  }

  /** Deletes one unpublished staging directory when its installation scope closes. */
  private final class StagingDirectory implements AutoCloseable {

    private final Path path;

    /** Creates a cleanup scope for one staging directory. */
    private StagingDirectory(Path path) {
      this.path = path;
    }

    /** Returns the staging directory path. */
    private Path path() {
      return path;
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
      treeDeleter.delete(path);
    }
  }
}
