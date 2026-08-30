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
package org.apache.opennlp.grpc.training;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BooleanSupplier;

import com.google.protobuf.Timestamp;
import opennlp.embeddings.ModelDistiller;
import opennlp.embeddings.StaticEmbeddingModel;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingModelPlan;
import org.apache.opennlp.grpc.v1.TeacherDescriptor;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.apache.opennlp.grpc.vocabulary.store.ArtifactDigests;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;
import org.apache.opennlp.grpc.vocabulary.store.VocabularyStores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded, atomic store for static embedding models distilled from learned vocabulary
 * artifacts. Models publish through the same durable {@link VocabularyStore} seam as
 * dictionaries and vocabularies, under a {@code models} kind, with a hashed manifest
 * naming the exact size and SHA-256 of every model file. A published model is loaded
 * into a local cache directory, verified against its manifest, and registered with the
 * {@link TrainedModelEmbeddingProvider} so it serves immediately and again after a
 * restart.
 */
public final class StaticModelArtifactStore {

  private static final Logger logger = LoggerFactory.getLogger(StaticModelArtifactStore.class);

  /** Default principal components kept when a request selects 0. */
  static final int DEFAULT_PCA_DIMS = 256;
  /** Default maximum accepted {@code pca_dims}. */
  static final int DEFAULT_MAX_PCA_DIMS = 512;
  /** Default trainings admitted at once. */
  static final int DEFAULT_MAX_CONCURRENT_TRAININGS = 1;

  private static final int MAX_PCA_DIMS_LIMIT = 4096;
  private static final int MAX_CONCURRENT_TRAININGS_LIMIT = 4;
  private static final String ARTIFACT_ROOT_KEY = "vocabulary.artifact_root";
  private static final String TEACHER_KEY_PREFIX = "training.teacher.";
  private static final String TEACHER_REF_SUFFIX = ".ref";
  private static final String TEACHER_DISPLAY_NAME_SUFFIX = ".display_name";
  private static final String MAX_PCA_DIMS_KEY = "training.max_pca_dims";
  private static final String MAX_CONCURRENT_TRAININGS_KEY =
      "training.max_concurrent_trainings";
  private static final String MODEL_CACHE_DIR_KEY = "training.model_cache_dir";
  private static final String MODELS_KIND = "models";
  private static final String MODEL_MANIFEST = "manifest.tsv";
  private static final String MODEL_DESCRIPTOR = "model.pb";
  private static final String ARTIFACT_ID_PREFIX = "static-model-";

  private final VocabularyStore store;
  private final VocabularyArtifactStore vocabularies;
  private final StaticModelTrainer trainer;
  private final TrainedModelEmbeddingProvider registry;
  private final Map<String, TeacherConfiguration> teachers;
  private final int maxPcaDims;
  private final int maxConcurrentTrainings;
  private final Path cacheRoot;
  private final Map<String, StaticModelDescriptor> models = new ConcurrentHashMap<>();
  private volatile PublicationListener publicationListener;

  /**
   * Receives one notification per successfully published model artifact.
   *
   * <p>Thread safety is implementation specific.</p>
   */
  @FunctionalInterface
  public interface PublicationListener {

    /**
     * Called after one model artifact is published and served.
     *
     * @param modelArtifactId Published model artifact id.
     * @param vocabularyArtifactId Vocabulary artifact the model was distilled from.
     */
    void modelPublished(String modelArtifactId, String vocabularyArtifactId);
  }

  /**
   * Registers the listener notified after each successful model publication.
   *
   * @param listener Listener, or {@code null} to remove the current one.
   */
  public void setPublicationListener(PublicationListener listener) {
    this.publicationListener = listener;
  }

  /** Creates the store and loads, verifies, and registers every published model. */
  private StaticModelArtifactStore(
      VocabularyStore store,
      VocabularyArtifactStore vocabularies,
      StaticModelTrainer trainer,
      TrainedModelEmbeddingProvider registry,
      Map<String, TeacherConfiguration> teachers,
      int maxPcaDims,
      int maxConcurrentTrainings,
      Path cacheRoot) throws IOException {
    this.store = store;
    this.vocabularies = vocabularies;
    this.trainer = trainer;
    this.registry = registry;
    this.teachers = new ConcurrentSkipListMap<>(teachers);
    this.maxPcaDims = maxPcaDims;
    this.maxConcurrentTrainings = maxConcurrentTrainings;
    this.cacheRoot = cacheRoot;
    if (store != null) {
      loadExistingModels();
    }
  }

  /**
   * Creates a disabled or enabled store from server configuration. The model artifacts
   * share the {@code vocabulary.artifact_root} durable store, so a remote store scheme
   * covers dictionaries, vocabularies, and trained models alike. Teachers are declared
   * as {@code training.teacher.<id>.ref} entries naming a local directory or a Hugging
   * Face model id; only configured teachers are accepted for training.
   *
   * @param configuration Server configuration map.
   * @param vocabularies Store resolving the vocabulary artifacts terms come from.
   * @param trainer Distillation implementation.
   * @param registry Serving registry that trained models are registered with.
   * @return Configured store.
   * @throws IOException If an enabled root or cache cannot be created or verified.
   * @throws IllegalArgumentException If an argument or a configured value is invalid.
   */
  public static StaticModelArtifactStore fromConfiguration(
      Map<String, String> configuration,
      VocabularyArtifactStore vocabularies,
      StaticModelTrainer trainer,
      TrainedModelEmbeddingProvider registry) throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    if (vocabularies == null) {
      throw new IllegalArgumentException("vocabularies must not be null");
    }
    if (trainer == null) {
      throw new IllegalArgumentException("trainer must not be null");
    }
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    final String configuredRoot = configuration.get(ARTIFACT_ROOT_KEY);
    final VocabularyStore store = configuredRoot == null || configuredRoot.isBlank()
        ? null : VocabularyStores.open(configuredRoot);
    return new StaticModelArtifactStore(
        store,
        vocabularies,
        trainer,
        registry,
        configuredTeachers(configuration),
        configuredPositiveInt(configuration, MAX_PCA_DIMS_KEY,
            DEFAULT_MAX_PCA_DIMS, MAX_PCA_DIMS_LIMIT),
        configuredPositiveInt(configuration, MAX_CONCURRENT_TRAININGS_KEY,
            DEFAULT_MAX_CONCURRENT_TRAININGS, MAX_CONCURRENT_TRAININGS_LIMIT),
        store == null ? null : cacheRoot(configuration));
  }

  /** Reports whether training is enabled.
   *
   * @return Whether training and model operations have a configured artifact root. */
  public boolean writesEnabled() {
    return store != null;
  }

  /** Reports the configured PCA bound.
   *
   * @return Largest accepted {@code pca_dims}. */
  public int maxPcaDims() {
    return maxPcaDims;
  }

  /** Reports the configured training admission bound.
   *
   * @return Trainings admitted at once. */
  public int maxConcurrentTrainings() {
    return maxConcurrentTrainings;
  }

  /**
   * Validates streaming-session model controls before corpus documents are accepted.
   *
   * @param plan Model controls whose vocabulary id is supplied by the session.
   * @throws IllegalStateException If model training is disabled.
   * @throws IllegalArgumentException If a control or teacher id is invalid.
   */
  public void validateTrainingPlan(StreamingTrainingModelPlan plan) {
    requireEnabled();
    if (plan == null) {
      throw new IllegalArgumentException("model plan must not be null");
    }
    validateTrainingControls(plan.getTeacherId(), plan.getDisplayName(),
        plan.getPcaDims(), plan.getProvenanceSummary());
  }

  /**
   * Describes the configured teachers.
   *
   * @return Descriptors in stable teacher-id order; {@code local} reflects whether the
   *     reference currently is an existing local directory.
   */
  public List<TeacherDescriptor> teachers() {
    final List<TeacherDescriptor> descriptors = new ArrayList<>(teachers.size());
    for (TeacherConfiguration teacher : teachers.values()) {
      descriptors.add(TeacherDescriptor.newBuilder()
          .setTeacherId(teacher.teacherId())
          .setDisplayName(teacher.displayName())
          .setReference(teacher.reference())
          .setLocal(Files.isDirectory(Path.of(teacher.reference())))
          .build());
    }
    return descriptors;
  }

  /**
   * Registers one verified node-local catalog teacher for immediate distillation use.
   *
   * @param teacherId Stable teacher identifier.
   * @param displayName User-facing teacher name.
   * @param directory Verified local teacher directory.
   * @throws IllegalArgumentException If an argument is invalid or the identifier is registered.
   */
  void registerCatalogTeacher(String teacherId, String displayName, Path directory) {
    registerCatalogTeacher(teacherId, displayName, directory,
        TeacherProvenance.unknown(directory == null ? "" : directory.toString()));
  }

  /**
   * Registers a verified catalog teacher together with what the catalog knows about it,
   * so every model distilled from it records the teacher's origin and license.
   *
   * @param teacherId Stable teacher identifier.
   * @param displayName User-facing teacher name.
   * @param directory Verified local teacher directory.
   * @param provenance The teacher's origin, revision, license and languages.
   * @throws IllegalArgumentException If an argument is invalid or the identifier is registered.
   */
  void registerCatalogTeacher(
      String teacherId, String displayName, Path directory, TeacherProvenance provenance) {
    requireTrimmed(teacherId, "teacher_id");
    requireTrimmed(displayName, "teacher display_name");
    if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("teacher directory must be an existing directory");
    }
    if (provenance == null) {
      throw new IllegalArgumentException("provenance must not be null");
    }
    final TeacherConfiguration teacher = new TeacherConfiguration(
        teacherId, displayName, directory.toAbsolutePath().normalize().toString(), provenance);
    if (teachers.putIfAbsent(teacherId, teacher) != null) {
      throw new IllegalArgumentException("Teacher id '" + teacherId + "' is already registered");
    }
  }

  /**
   * Lists every published model.
   *
   * @return Descriptors in stable artifact-id order.
   */
  public List<StaticModelDescriptor> models() {
    final List<StaticModelDescriptor> descriptors = new ArrayList<>(models.values());
    descriptors.sort(Comparator.comparing(StaticModelDescriptor::getArtifactId));
    return descriptors;
  }

  /**
   * Distills, atomically publishes, and starts serving one static model.
   *
   * @param request Training controls naming a teacher and a vocabulary artifact.
   * @param listener Receives one call per distillation progress line; may be {@code null}.
   * @return Published model descriptor.
   * @throws IOException If distillation, publication, or verification fails.
   * @throws IllegalStateException If no artifact root is configured.
   * @throws IllegalArgumentException If the request violates the training contract.
   */
  public StaticModelDescriptor trainStaticModel(
      TrainStaticModelRequest request, ModelDistiller.ProgressListener listener)
      throws IOException {
    return trainStaticModel(request, listener, () -> false);
  }

  /**
   * Distills and publishes while observing transport cancellation between durable stages.
   *
   * @param request Training controls.
   * @param listener Progress listener.
   * @param cancelled Cancellation probe.
   * @return Published descriptor.
   * @throws IOException If distillation, publication, or verification fails.
   * @throws CancellationException If cancellation is observed before publication completes.
   */
  public StaticModelDescriptor trainStaticModel(TrainStaticModelRequest request,
      ModelDistiller.ProgressListener listener, BooleanSupplier cancelled) throws IOException {
    if (cancelled == null) {
      throw new IllegalArgumentException("cancelled must not be null");
    }
    requireEnabled();
    final TeacherConfiguration teacher = validateRequest(request);
    final int pcaDims = request.getPcaDims() == 0 ? DEFAULT_PCA_DIMS : request.getPcaDims();
    final List<String> terms = readVocabularyTerms(request.getVocabularyArtifactId());
    final ModelDistiller.ProgressListener progress =
        listener != null ? listener : message -> { };

    final Path scratch = Files.createTempDirectory("static-model-training-");
    try {
      requireActive(cancelled);
      final ModelDistiller.Result result =
          trainer.train(teacher.reference(), scratch, pcaDims, terms, progress);
      requireActive(cancelled);
      final String artifactId = ARTIFACT_ID_PREFIX + UUID.randomUUID();
      final StaticModelDescriptor descriptor =
          publish(artifactId, scratch, request, teacher, result);
      boolean registered = false;
      try {
        requireActive(cancelled);
        final Path cached = materializeCache(artifactId, scratch);
        requireActive(cancelled);
        registry.register(artifactId, StaticEmbeddingModel.load(cached),
            descriptor.getArtifactHash());
        registered = true;
        requireActive(cancelled);
        models.put(artifactId, descriptor);
        requireActive(cancelled);
      } catch (IOException | RuntimeException e) {
        models.remove(artifactId);
        if (registered) {
          registry.unregister(artifactId);
        }
        try {
          deleteTree(cacheRoot.resolve(artifactId));
        } catch (IOException cleanupFailure) {
          e.addSuppressed(cleanupFailure);
        }
        try {
          store.delete(MODELS_KIND, artifactId);
        } catch (IOException cleanupFailure) {
          e.addSuppressed(cleanupFailure);
        }
        throw e;
      }
      final PublicationListener published = publicationListener;
      if (published != null) {
        try {
          published.modelPublished(artifactId, descriptor.getVocabularyArtifactId());
        } catch (RuntimeException e) {
          logger.warn("Published static model '{}' but its lifecycle notification failed",
              artifactId, e);
        }
      }
      return descriptor;
    } finally {
      deleteTree(scratch);
    }
  }

  /** Throws when the caller cancelled the training operation. */
  private static void requireActive(BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("Static model training is cancelled");
    }
  }

  /**
   * Deletes one published model and stops serving it.
   *
   * @param artifactId Server-owned model artifact id.
   * @return {@code true} when the model existed and was deleted.
   * @throws IOException If artifact deletion fails part way.
   * @throws IllegalStateException If no artifact root is configured.
   * @throws IllegalArgumentException If the artifact id is malformed.
   */
  public boolean deleteModel(String artifactId) throws IOException {
    requireEnabled();
    requireArtifactId(artifactId);
    final StaticModelDescriptor existing = models.get(artifactId);
    if (existing == null) {
      return false;
    }
    deleteTree(cacheRoot.resolve(artifactId));
    store.delete(MODELS_KIND, artifactId);
    registry.unregister(artifactId);
    models.remove(artifactId);
    return true;
  }

  /** Publishes the distilled files, their manifest, and the descriptor atomically. */
  private StaticModelDescriptor publish(
      String artifactId,
      Path modelDirectory,
      TrainStaticModelRequest request,
      TeacherConfiguration teacher,
      ModelDistiller.Result result) throws IOException {
    final List<Path> files = distilledFiles(modelDirectory);
    try (VocabularyStore.ArtifactWriter writer = store.write(MODELS_KIND, artifactId)) {
      final StringBuilder manifest = new StringBuilder();
      long totalBytes = 0;
      for (Path file : files) {
        final String name = file.getFileName().toString();
        final ArtifactDigests.HashingOutputStream entry =
            new ArtifactDigests.HashingOutputStream(writer.entry(name));
        try (entry; InputStream input = Files.newInputStream(file)) {
          input.transferTo(entry);
        }
        manifest.append(name).append('\t').append(entry.count())
            .append('\t').append(entry.hexDigest()).append('\n');
        totalBytes += entry.count();
      }
      final byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
      final String artifactHash;
      try (InputStream input = new ByteArrayInputStream(manifestBytes)) {
        artifactHash = ArtifactDigests.digest(input).hexDigest();
      }
      try (OutputStream out = writer.entry(MODEL_MANIFEST)) {
        out.write(manifestBytes);
      }
      final StaticModelDescriptor descriptor = StaticModelDescriptor.newBuilder()
          .setArtifactId(artifactId)
          .setDisplayName(request.getDisplayName())
          .setVocabularyArtifactId(request.getVocabularyArtifactId())
          .setTeacherId(teacher.teacherId())
          .setFamily(result.family())
          .setDimension(result.dimension())
          .setVocabularySize(result.vocabularySize())
          .setTermCount(result.termCount())
          .setExplainedVarianceRatio(result.explainedVarianceRatio())
          .setArtifactHash(artifactHash)
          .setByteSize(totalBytes)
          .setProvenanceSummary(request.getProvenanceSummary())
          .setCreatedAt(now())
          .setTeacherReference(teacher.provenance().reference())
          .setTeacherRevision(teacher.provenance().revision())
          .setLicenseName(teacher.provenance().licenseName())
          .setLicenseUri(teacher.provenance().licenseUri())
          .addAllLanguages(teacher.provenance().languages())
          .build();
      try (OutputStream out = writer.entry(MODEL_DESCRIPTOR)) {
        descriptor.writeTo(out);
      }
      writer.commit();
      return descriptor;
    }
  }

  /** Lists the flat distilled model files in stable name order. */
  private static List<Path> distilledFiles(Path modelDirectory) throws IOException {
    final List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelDirectory)) {
      for (Path file : stream) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("Distillation produced a non-file entry: " + file);
        }
        final String name = file.getFileName().toString();
        if (MODEL_MANIFEST.equals(name) || MODEL_DESCRIPTOR.equals(name)) {
          throw new IOException("Distillation produced a reserved file name: " + name);
        }
        files.add(file);
      }
    }
    if (files.isEmpty()) {
      throw new IOException("Distillation produced no model files");
    }
    files.sort(Comparator.comparing(file -> file.getFileName().toString()));
    return files;
  }

  /** Loads, verifies, caches, and registers every model published before this process. */
  private void loadExistingModels() throws IOException {
    for (String artifactId : store.list(MODELS_KIND)) {
      final StaticModelDescriptor descriptor;
      try (InputStream input = store.read(MODELS_KIND, artifactId, MODEL_DESCRIPTOR)) {
        descriptor = StaticModelDescriptor.parseFrom(input);
      }
      if (!artifactId.equals(descriptor.getArtifactId())) {
        throw new IOException("Model descriptor id '" + descriptor.getArtifactId()
            + "' does not match published id '" + artifactId + "'");
      }
      final Path cached = materializeVerifiedArtifact(artifactId, descriptor);
      registry.register(artifactId, StaticEmbeddingModel.load(cached),
          descriptor.getArtifactHash());
      if (models.putIfAbsent(artifactId, descriptor) != null) {
        throw new IOException("Duplicate model artifact id '" + artifactId + "'");
      }
    }
  }

  /** Verifies the manifest and every file of one published model into the local cache. */
  private Path materializeVerifiedArtifact(
      String artifactId, StaticModelDescriptor descriptor) throws IOException {
    final byte[] manifestBytes;
    try (InputStream input = store.read(MODELS_KIND, artifactId, MODEL_MANIFEST)) {
      manifestBytes = input.readAllBytes();
    }
    try (InputStream input = new ByteArrayInputStream(manifestBytes)) {
      if (!ArtifactDigests.digest(input).hexDigest().equals(descriptor.getArtifactHash())) {
        throw new IOException("Model artifact '" + artifactId + "' manifest SHA-256 mismatch");
      }
    }
    final Path cached = freshCacheDirectory(artifactId);
    long totalBytes = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        new ByteArrayInputStream(manifestBytes), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        final ManifestEntry entry = ManifestEntry.parse(artifactId, line);
        final ArtifactDigests.HashingOutputStream out = new ArtifactDigests.HashingOutputStream(
            Files.newOutputStream(cached.resolve(entry.name())));
        try (out; InputStream input = store.read(MODELS_KIND, artifactId, entry.name())) {
          input.transferTo(out);
        }
        if (out.count() != entry.size() || !out.hexDigest().equals(entry.hexDigest())) {
          throw new IOException("Model artifact '" + artifactId + "' file '" + entry.name()
              + "' does not match its manifest");
        }
        totalBytes += out.count();
      }
    }
    if (totalBytes != descriptor.getByteSize()) {
      throw new IOException("Model artifact '" + artifactId + "' byte size mismatch: expected "
          + descriptor.getByteSize() + ", found " + totalBytes);
    }
    return cached;
  }

  /** Copies one freshly distilled model into the local cache. */
  private Path materializeCache(String artifactId, Path modelDirectory) throws IOException {
    final Path cached = freshCacheDirectory(artifactId);
    for (Path file : distilledFiles(modelDirectory)) {
      Files.copy(file, cached.resolve(file.getFileName().toString()));
    }
    return cached;
  }

  /** Creates one empty cache directory for an artifact, replacing any stale copy. */
  private Path freshCacheDirectory(String artifactId) throws IOException {
    final Path cached = cacheRoot.resolve(artifactId);
    deleteTree(cached);
    return Files.createDirectories(cached);
  }

  /** Reads the vocabulary's terms sorted by descending frequency for distillation. */
  private List<String> readVocabularyTerms(String vocabularyArtifactId) throws IOException {
    final List<VocabularyArtifactStore.TermRow> rows =
        new ArrayList<>(vocabularies.readVocabularyTermRows(vocabularyArtifactId));
    rows.sort(Comparator.comparingLong(VocabularyArtifactStore.TermRow::count).reversed()
        .thenComparing(VocabularyArtifactStore.TermRow::term));
    final List<String> sorted = new ArrayList<>(rows.size());
    for (VocabularyArtifactStore.TermRow row : rows) {
      sorted.add(row.term());
    }
    return sorted;
  }

  /** Validates the training request and resolves its teacher. */
  private TeacherConfiguration validateRequest(TrainStaticModelRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("training request must not be null");
    }
    final TeacherConfiguration teacher = validateTrainingControls(
        request.getTeacherId(), request.getDisplayName(), request.getPcaDims(),
        request.getProvenanceSummary());
    vocabularies.requireVocabulary(request.getVocabularyArtifactId());
    return teacher;
  }

  /** Validates controls shared by unary and bidirectional training. */
  private TeacherConfiguration validateTrainingControls(
      String teacherId, String displayName, int pcaDims, String provenanceSummary) {
    requireTrimmed(displayName, "model display_name");
    requireTrimmed(provenanceSummary, "model provenance_summary");
    final TeacherConfiguration teacher = teachers.get(teacherId);
    if (teacher == null) {
      throw new IllegalArgumentException("Unknown teacher '" + teacherId + "'");
    }
    if (pcaDims != 0 && (pcaDims < 1 || pcaDims > maxPcaDims)) {
      throw new IllegalArgumentException("pca_dims must be 0 for the default or between 1 and "
          + maxPcaDims + ", was " + pcaDims);
    }
    return teacher;
  }

  /** Rejects model operations when no artifact root was configured. */
  private void requireEnabled() {
    if (!writesEnabled()) {
      throw new IllegalStateException(ARTIFACT_ROOT_KEY
          + " is not configured; model training is disabled");
    }
  }

  /** Validates a generated model artifact id. */
  private void requireArtifactId(String id) {
    requireTrimmed(id, "model artifact_id");
    if (!id.startsWith(ARTIFACT_ID_PREFIX)
        || id.length() != ARTIFACT_ID_PREFIX.length() + 36) {
      throw new IllegalArgumentException("Invalid model artifact_id '" + id + "'");
    }
    try {
      UUID.fromString(id.substring(ARTIFACT_ID_PREFIX.length()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid model artifact_id '" + id + "'", e);
    }
  }

  /** Parses the configured teacher allowlist, rejecting unknown teacher keys loudly. */
  private static Map<String, TeacherConfiguration> configuredTeachers(
      Map<String, String> configuration) {
    final Map<String, String> references = new TreeMap<>();
    final Map<String, String> displayNames = new TreeMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(TEACHER_KEY_PREFIX)) {
        continue;
      }
      if (key.endsWith(TEACHER_REF_SUFFIX)) {
        references.put(teacherId(key, TEACHER_REF_SUFFIX), entry.getValue());
      } else if (key.endsWith(TEACHER_DISPLAY_NAME_SUFFIX)) {
        displayNames.put(teacherId(key, TEACHER_DISPLAY_NAME_SUFFIX), entry.getValue());
      } else {
        throw new IllegalArgumentException("Unknown teacher configuration key '" + key + "'");
      }
    }
    for (String id : displayNames.keySet()) {
      if (!references.containsKey(id)) {
        throw new IllegalArgumentException("Teacher '" + id
            + "' declares a display name but no '" + TEACHER_KEY_PREFIX + id
            + TEACHER_REF_SUFFIX + "'");
      }
    }
    final Map<String, TeacherConfiguration> teachers = new TreeMap<>();
    for (Map.Entry<String, String> entry : references.entrySet()) {
      final String id = entry.getKey();
      final String reference = entry.getValue();
      if (reference == null || reference.isBlank()) {
        throw new IllegalArgumentException("Teacher '" + id + "' reference must not be blank");
      }
      teachers.put(id, new TeacherConfiguration(
          id, displayNames.getOrDefault(id, id), reference.trim()));
    }
    return teachers;
  }

  /** Extracts and validates the teacher id between the key prefix and one suffix. */
  private static String teacherId(String key, String suffix) {
    final String id =
        key.substring(TEACHER_KEY_PREFIX.length(), key.length() - suffix.length());
    if (id.isBlank() || id.contains(".")) {
      throw new IllegalArgumentException("Invalid teacher configuration key '" + key + "'");
    }
    return id;
  }

  /** Resolves the configured or default local model cache root. */
  private static Path cacheRoot(Map<String, String> configuration) throws IOException {
    final String configured = configuration.get(MODEL_CACHE_DIR_KEY);
    if (configured == null || configured.isBlank()) {
      return Files.createTempDirectory("opennlp-trained-models-");
    }
    return Files.createDirectories(Path.of(configured.trim()));
  }

  /** Reads one positive configured limit bounded by a fixed ceiling. */
  private static int configuredPositiveInt(
      Map<String, String> configuration, String key, int defaultValue, int ceiling) {
    final String value = configuration.get(key);
    final int parsed;
    try {
      parsed = value == null ? defaultValue : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " must be a positive integer", e);
    }
    if (parsed < 1 || parsed > ceiling) {
      throw new IllegalArgumentException(key + " must be between 1 and " + ceiling
          + ", was " + parsed);
    }
    return parsed;
  }

  /** Requires a trimmed, nonblank string. */
  private static void requireTrimmed(String value, String name) {
    if (value == null || value.isBlank() || !value.equals(value.trim())) {
      throw new IllegalArgumentException(name + " must be trimmed and nonblank");
    }
  }

  /** Deletes one directory tree, deepest entries first; absent trees are ignored. */
  private static void deleteTree(Path tree) throws IOException {
    if (tree == null || !Files.exists(tree, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    final List<Path> paths = new ArrayList<>();
    try (var stream = Files.walk(tree)) {
      stream.forEach(paths::add);
    }
    paths.sort(Comparator.<Path>comparingInt(Path::getNameCount).reversed());
    for (Path path : paths) {
      Files.delete(path);
    }
  }

  /** Returns the current wall-clock instant as a protobuf timestamp. */
  private static Timestamp now() {
    final Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }

  /**
   * Reports what is known about a registered teacher's origin.
   *
   * @param teacherId The teacher identifier.
   * @return Its provenance.
   * @throws IllegalArgumentException If no teacher has that identifier.
   */
  TeacherProvenance teacherProvenance(String teacherId) {
    final TeacherConfiguration teacher = teachers.get(teacherId);
    if (teacher == null) {
      throw new IllegalArgumentException("Unknown teacher '" + teacherId + "'");
    }
    return teacher.provenance();
  }

  /** One configured teacher and what is known about its origin. */
  private record TeacherConfiguration(
      String teacherId, String displayName, String reference, TeacherProvenance provenance) {

    /** An operator-configured teacher: its path is all that is known. */
    TeacherConfiguration(String teacherId, String displayName, String reference) {
      this(teacherId, displayName, reference, TeacherProvenance.unknown(reference));
    }
  }

  /** One parsed manifest line: file name, byte size, and SHA-256. */
  private record ManifestEntry(String name, long size, String hexDigest) {

    /** Parses one {@code name\tsize\tsha256} manifest line. */
    static ManifestEntry parse(String artifactId, String line) throws IOException {
      final int firstTab = line.indexOf('\t');
      final int secondTab = firstTab < 0 ? -1 : line.indexOf('\t', firstTab + 1);
      if (firstTab <= 0 || secondTab < 0 || line.indexOf('\t', secondTab + 1) >= 0) {
        throw new IOException("Model artifact '" + artifactId + "' has a corrupt manifest");
      }
      final long size;
      try {
        size = Long.parseLong(line.substring(firstTab + 1, secondTab));
      } catch (NumberFormatException e) {
        throw new IOException("Model artifact '" + artifactId + "' has a corrupt manifest", e);
      }
      return new ManifestEntry(line.substring(0, firstTab), size, line.substring(secondTab + 1));
    }
  }
}
