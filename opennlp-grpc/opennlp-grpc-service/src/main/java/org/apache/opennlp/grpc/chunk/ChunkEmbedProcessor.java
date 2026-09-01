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
package org.apache.opennlp.grpc.chunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.embedding.EmbeddingBackendSelections;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkGroupStats;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.StandardChunkingStrategy;

/**
 * Builds {@link ChunkEmbeddingGroup} results from {@link ChunkEmbedConfigEntry} requests.
 */
public final class ChunkEmbedProcessor {

  private ChunkEmbedProcessor() {
  }

  /**
   * Validates a chunk+embed config entry against the server's capabilities before any
   * processing starts, so invalid requests fail without partial results.
   *
   * @param entry             The config entry to validate.
   * @param embeddingProvider The provider whose registered models are checked.
   *
   * @throws AnalysisException If the entry is incomplete, references unknown embedding
   *                           models, or requires features this server does not provide.
   */
  public static void validateEntry(ChunkEmbedConfigEntry entry, EmbeddingProvider embeddingProvider) {
    if (entry.getConfigId().isBlank()) {
      throw AnalysisException.invalidArgument("chunk_embed_configs.config_id is required");
    }
    if (entry.hasProfile()) {
      validateEntryProfile(entry.getProfile(), entry.getConfigId());
    }
    if (!entry.hasChunking()) {
      throw AnalysisException.invalidArgument(
          "chunk_embed_configs.chunking is required for config '" + entry.getConfigId() + "'");
    }
    final ChunkingSpec chunking = entry.getChunking();
    if (isSemantic(chunking)) {
      validateSemanticChunking(entry);
      if (!embeddingProvider.isAvailable()) {
        throw AnalysisException.notFound(
            "semantic chunking for config '" + entry.getConfigId()
                + "' requires configured embedding models on this server");
      }
    }
    final List<EmbeddingSelector> selectors = embeddingSelectors(entry);
    if (!selectors.isEmpty() && !embeddingProvider.isAvailable()) {
      throw AnalysisException.notFound(
          "embedding models requested for config '" + entry.getConfigId()
              + "' but no embedding models are configured on this server");
    }
    for (EmbeddingSelector selector : selectors) {
      validateSelector(selector, embeddingProvider);
    }
  }

  /**
   * Chunks the document according to the entry's chunking spec and embeds every chunk
   * with each requested embedding model.
   *
   * @param rawText           The document text the annotation offsets refer to.
   * @param document          The analyzed document backbone.
   * @param entry             A previously validated config entry.
   * @param embeddingProvider The provider used for chunk embeddings and semantic chunking.
   *
   * @return The resulting chunk group including per-group statistics.
   */
  public static ChunkEmbeddingGroup buildGroup(
      String rawText,
      OpenNlpDocument document,
      ChunkEmbedConfigEntry entry,
      EmbeddingProvider embeddingProvider) {
    final long started = System.currentTimeMillis();
    final List<EmbeddingSelector> selectors = embeddingSelectors(entry);
    final EmbeddingSelector semanticFallback = selectors.size() == 1 ? selectors.getFirst() : null;
    final List<SegmentationChunker.ChunkSegment> segments = SegmentationChunker.segment(
        rawText, document, entry.getChunking(), embeddingProvider, semanticFallback);

    final ChunkEmbeddingGroup.Builder group = ChunkEmbeddingGroup.newBuilder()
        .setGroupId(entry.getConfigId())
        .setChunkConfigId(entry.getConfigId())
        .addAllEmbeddingModelIds(selectors.stream().map(EmbeddingSelector::getModelId).toList())
        .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
        .setStrategy(ChunkingStrategies.selectedStrategy(entry.getChunking()));
    if (entry.hasResultSetName()) {
      group.setResultSetName(entry.getResultSetName());
    }

    final List<String> chunkTexts = new ArrayList<>(segments.size());
    final ChunkingSpec chunkingSpec = entry.getChunking();
    for (SegmentationChunker.ChunkSegment segment : segments) {
      chunkTexts.add(ChunkTextPreprocessor.chunkText(
          rawText, segment.start(), segment.end(), chunkingSpec));
    }

    // One batched inference per model across all chunks of this group.
    final List<EmbeddingBatchResult> batches = new ArrayList<>(selectors.size());
    if (!segments.isEmpty()) {
      for (EmbeddingSelector selector : selectors) {
        final EmbeddingBatchResult batch = embeddingProvider.embedBatchResolved(
            selector.getModelId(), selectedBackend(selector), chunkTexts);
        requireFullBatch(selector.getModelId(), batch, chunkTexts.size(), "chunk");
        batches.add(batch);
      }
    }

    // Token-window chunks overlap, so a token can appear in several chunks; count each token
    // once for the group total by deduplicating on its (unique) character start offset.
    final Set<Integer> distinctTokenStarts = new HashSet<>();
    for (int i = 0; i < segments.size(); i++) {
      final SegmentationChunker.ChunkSegment segment = segments.get(i);
      final Chunk.Builder chunk = Chunk.newBuilder()
          .setAnnotationSpan(toSpan(segment.start(), segment.end()))
          .setTextContent(chunkTexts.get(i))
          .addAllContainedSentenceIndices(segment.sentenceIndices());
      collectTokenStarts(document, segment, distinctTokenStarts);
      for (int selected = 0; selected < selectors.size(); selected++) {
        final EmbeddingSelector selector = selectors.get(selected);
        final EmbeddingBatchResult batch = batches.get(selected);
        chunk.addEmbeddings(EmbeddingResult.newBuilder()
            .setModelId(selector.getModelId())
            .addAllVector(toFloatList(batch.vectors().get(i)))
            .setSourceSpan(toSpan(segment.start(), segment.end()))
            .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
            .setRoute(batch.route())
            .build());
      }
      group.addChunks(chunk.build());
    }

    // One centroid per model: the mean of this group's chunk vectors, spanning all its chunks.
    if (!segments.isEmpty()) {
      final AnnotationSpan groupSpan = groupSpan(segments);
      for (int selected = 0; selected < selectors.size(); selected++) {
        final EmbeddingSelector selector = selectors.get(selected);
        final EmbeddingBatchResult batch = batches.get(selected);
        final EmbeddingResult centroid = Centroids.centroid(
            selector.getModelId(), batch.vectors(), groupSpan,
            EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID, batch.route());
        if (centroid != null) {
          group.addCentroids(centroid);
        }
      }
    }

    group.setStats(ChunkGroupStats.newBuilder()
        .setChunkCount(segments.size())
        .setTotalTokens(distinctTokenStarts.size())
        .setProcessingTimeMs(System.currentTimeMillis() - started)
        .build());
    return group.build();
  }

  /**
   * Validates a category-chunk config entry: a non-blank id, at least one supported embedding
   * model, and no blank category labels.
   *
   * @param entry The entry to validate.
   * @param embeddingProvider The provider, checked for the requested models.
   *
   * @throws AnalysisException If the entry is malformed or names an unavailable model.
   */
  public static void validateCategoryEntry(
      CategoryChunkConfigEntry entry, EmbeddingProvider embeddingProvider) {
    if (entry.getConfigId().isBlank()) {
      throw AnalysisException.invalidArgument("category_chunk_configs.config_id is required");
    }
    final List<EmbeddingSelector> selectors = embeddingSelectors(entry);
    if (selectors.isEmpty()) {
      throw AnalysisException.invalidArgument("category_chunk_configs entry '" + entry.getConfigId()
          + "' requires at least one embedding selector");
    }
    if (!embeddingProvider.isAvailable()) {
      throw AnalysisException.notFound("embedding models requested for category config '"
          + entry.getConfigId() + "' but no embedding models are configured on this server");
    }
    for (EmbeddingSelector selector : selectors) {
      validateSelector(selector, embeddingProvider);
    }
    for (String category : entry.getCategoriesList()) {
      if (category == null || category.isBlank()) {
        throw AnalysisException.invalidArgument(
            "category_chunk_configs.categories must not contain blank values");
      }
    }
  }

  /**
   * Groups the document's sentences by their per-sentence category (the sentiment label),
   * concatenates each category's sentences into one chunk, embeds it with each requested model, and
   * returns a {@link ChunkEmbeddingGroup} with one chunk per category plus a per-model centroid.
   * Sentences without a category label are ignored.
   *
   * @param rawText The document text the offsets refer to.
   * @param document The analyzed document, whose sentences carry sentiment labels.
   * @param entry A previously validated category-chunk config entry.
   * @param embeddingProvider The provider used to embed the category texts.
   *
   * @return The resulting group; its chunks are the category groups, {@code chunk_tag} the label.
   */
  public static ChunkEmbeddingGroup buildCategoryGroup(
      String rawText, OpenNlpDocument document, CategoryChunkConfigEntry entry,
      EmbeddingProvider embeddingProvider) {
    final long started = System.currentTimeMillis();
    final List<EmbeddingSelector> selectors = embeddingSelectors(entry);

    // Bucket sentence text by normalized category label, preserving first-appearance order.
    // Category chunks never overlap, so a token's (unique) character start offset identifies
    // it for the group total, matching buildGroup's dedup semantics.
    final Map<String, CategoryBucket> buckets = new LinkedHashMap<>();
    final Set<Integer> distinctTokenStarts = new HashSet<>();
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final String label = sentence.getSentimentLabel();
      if (label == null || label.isBlank()) {
        continue;
      }
      buckets.computeIfAbsent(normalizeCategory(label), k -> new CategoryBucket(label))
          .add(i, sentence, rawText);
      for (var token : sentence.getTokensList()) {
        distinctTokenStarts.add(token.getAnnotationSpan().getStart());
      }
    }

    // Ordered categories: the allowlist (normalized, deduplicated) if given, else first-appearance.
    final List<String> order = new ArrayList<>();
    if (entry.getCategoriesCount() > 0) {
      for (String category : entry.getCategoriesList()) {
        final String key = normalizeCategory(category);
        if (buckets.containsKey(key) && !order.contains(key)) {
          order.add(key);
        }
      }
    } else {
      order.addAll(buckets.keySet());
    }

    final List<String> categoryTexts = new ArrayList<>(order.size());
    for (String key : order) {
      categoryTexts.add(buckets.get(key).text());
    }

    final List<EmbeddingBatchResult> batches = new ArrayList<>(selectors.size());
    if (!order.isEmpty()) {
      for (EmbeddingSelector selector : selectors) {
        final EmbeddingBatchResult batch = embeddingProvider.embedBatchResolved(
            selector.getModelId(), selectedBackend(selector), categoryTexts);
        requireFullBatch(selector.getModelId(), batch, categoryTexts.size(), "category chunk");
        batches.add(batch);
      }
    }

    final ChunkEmbeddingGroup.Builder group = ChunkEmbeddingGroup.newBuilder()
        .setGroupId(entry.getConfigId())
        .setChunkConfigId(entry.getConfigId())
        .addAllEmbeddingModelIds(selectors.stream().map(EmbeddingSelector::getModelId).toList())
        .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
        .setStrategy(ChunkingStrategies.standard(
            StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_CATEGORY));
    if (entry.hasResultSetName()) {
      group.setResultSetName(entry.getResultSetName());
    }

    int spanStart = Integer.MAX_VALUE;
    int spanEnd = Integer.MIN_VALUE;
    for (int g = 0; g < order.size(); g++) {
      final CategoryBucket bucket = buckets.get(order.get(g));
      spanStart = Math.min(spanStart, bucket.start);
      spanEnd = Math.max(spanEnd, bucket.end);
      final Chunk.Builder chunk = Chunk.newBuilder()
          .setAnnotationSpan(toSpan(bucket.start, bucket.end))
          .setChunkTag(bucket.label)
          .setTextContent(categoryTexts.get(g))
          .addAllContainedSentenceIndices(bucket.indices);
      for (int selected = 0; selected < selectors.size(); selected++) {
        final EmbeddingSelector selector = selectors.get(selected);
        final EmbeddingBatchResult batch = batches.get(selected);
        chunk.addEmbeddings(EmbeddingResult.newBuilder()
            .setModelId(selector.getModelId())
            .addAllVector(toFloatList(batch.vectors().get(g)))
            .setSourceSpan(toSpan(bucket.start, bucket.end))
            .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
            .setRoute(batch.route())
            .build());
      }
      group.addChunks(chunk.build());
    }

    if (!order.isEmpty()) {
      final AnnotationSpan groupSpan = toSpan(spanStart, spanEnd);
      for (int selected = 0; selected < selectors.size(); selected++) {
        final EmbeddingSelector selector = selectors.get(selected);
        final EmbeddingBatchResult batch = batches.get(selected);
        final EmbeddingResult centroid = Centroids.centroid(
            selector.getModelId(), batch.vectors(), groupSpan,
            EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID, batch.route());
        if (centroid != null) {
          group.addCentroids(centroid);
        }
      }
    }

    group.setStats(ChunkGroupStats.newBuilder()
        .setChunkCount(order.size())
        .setTotalTokens(distinctTokenStarts.size())
        .setProcessingTimeMs(System.currentTimeMillis() - started)
        .build());
    return group.build();
  }

  /** Normalizes category. */
  private static String normalizeCategory(String value) {
    return StringUtil.toLowerCase(value.trim());
  }

  /** Accumulates one category's sentences: their texts (for concat) and their bounding span. */
  private static final class CategoryBucket {
    private final String label;
    private final List<Integer> indices = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private int start = Integer.MAX_VALUE;
    private int end = Integer.MIN_VALUE;

    CategoryBucket(String label) {
      this.label = label;
    }

    void add(int sentenceIndex, AnnotatedSentence sentence, String rawText) {
      final AnnotationSpan span = sentence.getSentenceSpan();
      indices.add(sentenceIndex);
      texts.add(rawText.substring(span.getStart(), span.getEnd()));
      start = Math.min(start, span.getStart());
      end = Math.max(end, span.getEnd());
    }

    String text() {
      return String.join(" ", texts);
    }
  }

  /** The span covering every chunk in a group (from the earliest start to the latest end). */
  private static AnnotationSpan groupSpan(List<SegmentationChunker.ChunkSegment> segments) {
    int start = Integer.MAX_VALUE;
    int end = Integer.MIN_VALUE;
    for (SegmentationChunker.ChunkSegment segment : segments) {
      start = Math.min(start, segment.start());
      end = Math.max(end, segment.end());
    }
    return toSpan(start, end);
  }

  /**
   * Builds a sentence-per-chunk group without embeddings, used when the {@code CHUNK}
   * pipeline step runs without chunk+embed configs.
   *
   * @param rawText  The document text the annotation offsets refer to.
   * @param document The analyzed document backbone.
   * @param groupId  The id assigned to the resulting group.
   *
   * @return The resulting chunk group.
   */
  public static ChunkEmbeddingGroup buildSentenceGroup(
      String rawText, OpenNlpDocument document, String groupId) {
    final ChunkingSpec spec = ChunkingSpec.newBuilder()
        .setStrategy(ChunkingStrategies.standard(
            StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE))
        .build();
    final ChunkEmbedConfigEntry entry = ChunkEmbedConfigEntry.newBuilder()
        .setConfigId(groupId)
        .setChunking(spec)
        .build();
    return buildGroup(rawText, document, entry, new NoOpEmbeddingProvider());
  }

  /**
   * Builds an INFO diagnostic recording the chunk count for a successfully processed config.
   *
   * @param configId   The config id the diagnostic refers to.
   * @param chunkCount The number of chunks produced for the config.
   *
   * @return An INFO diagnostic for a successfully processed chunk config.
   */
  public static ProcessingDiagnostic successDiagnostic(String configId, int chunkCount) {
    return ProcessingDiagnostic.newBuilder()
        .setStep(PipelineStep.PIPELINE_STEP_CHUNK)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage("Produced " + chunkCount + " chunk(s) for config '" + configId + "'")
        .build();
  }

  /**
   * Validates that every step listed on a chunk config entry profile is implemented.
   *
   * @param profile  The per-entry profile to validate. Must not be {@code null}.
   * @param configId The parent config entry id, used in error messages.
   *
   * @throws AnalysisException If the profile requests an unimplemented step.
   */
  private static void validateEntryProfile(AnalysisProfile profile, String configId) {
    for (PipelineStep step : profile.getStepsList()) {
      if (step == PipelineStep.PIPELINE_STEP_UNSPECIFIED || step == PipelineStep.UNRECOGNIZED) {
        continue;
      }
      if (!PipelineStepPolicy.isImplemented(step)) {
        throw AnalysisException.unimplemented(
            "chunk_embed_configs profile for config '" + configId + "' requests unimplemented step "
                + step);
      }
    }
  }

  /** Validates semantic chunking. */
  private static void validateSemanticChunking(ChunkEmbedConfigEntry entry) {
    final var semantic = entry.getChunking().getSemanticConfig();
    if (semantic.hasSemanticEmbeddingModelId() && semantic.hasSemanticEmbeddingSelector()) {
      throw AnalysisException.invalidArgument("semantic_embedding_model_id and "
          + "semantic_embedding_selector are mutually exclusive");
    }
    if (semantic.hasSemanticEmbeddingSelector()) {
      if (semantic.getSemanticEmbeddingSelector().getModelId().isBlank()) {
        throw AnalysisException.invalidArgument(
            "semantic_embedding_selector.model_id must not be blank");
      }
      return;
    }
    if (semantic.hasSemanticEmbeddingModelId() && !semantic.getSemanticEmbeddingModelId().isBlank()) {
      return;
    }
    if (embeddingSelectors(entry).size() == 1) {
      return;
    }
    throw AnalysisException.invalidArgument(
        "semantic chunking requires a semantic embedding selector or exactly one chunk embedding selector");
  }

  /** Resolves typed selectors and compatibility model ids. */
  private static List<EmbeddingSelector> embeddingSelectors(ChunkEmbedConfigEntry entry) {
    if (entry.getEmbeddingModelIdsCount() > 0 && entry.getEmbeddingSelectorsCount() > 0) {
      throw AnalysisException.invalidArgument(
          "embedding_model_ids and embedding_selectors are mutually exclusive");
    }
    if (entry.getEmbeddingSelectorsCount() > 0) {
      return entry.getEmbeddingSelectorsList();
    }
    return entry.getEmbeddingModelIdsList().stream()
        .map(modelId -> EmbeddingSelector.newBuilder().setModelId(modelId).build())
        .toList();
  }

  /** Resolves typed selectors and compatibility model ids. */
  private static List<EmbeddingSelector> embeddingSelectors(CategoryChunkConfigEntry entry) {
    if (entry.getEmbeddingModelIdsCount() > 0 && entry.getEmbeddingSelectorsCount() > 0) {
      throw AnalysisException.invalidArgument(
          "embedding_model_ids and embedding_selectors are mutually exclusive");
    }
    if (entry.getEmbeddingSelectorsCount() > 0) {
      return entry.getEmbeddingSelectorsList();
    }
    return entry.getEmbeddingModelIdsList().stream()
        .map(modelId -> EmbeddingSelector.newBuilder().setModelId(modelId).build())
        .toList();
  }

  /** Validates selector. */
  private static void validateSelector(
      EmbeddingSelector selector, EmbeddingProvider embeddingProvider) {
    final String modelId = selector.getModelId().trim();
    if (modelId.isEmpty()) {
      throw AnalysisException.invalidArgument("embedding selector model_id must not be blank");
    }
    final String backendId = selectedBackend(selector);
    if (backendId == null) {
      if (!embeddingProvider.supportsModel(modelId)) {
        throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
      }
    } else if (!embeddingProvider.supportsModel(modelId, backendId)) {
      throw AnalysisException.notFound(
          "Engine '" + backendId + "' does not serve embedding model '" + modelId + "'");
    }
  }

  /** Returns the selected backend id. */
  private static String selectedBackend(EmbeddingSelector selector) {
    return EmbeddingBackendSelections.selectedId(selector);
  }

  /** Returns whether semantic chunking is selected. */
  private static boolean isSemantic(ChunkingSpec chunking) {
    return ChunkingStrategies.isSemantic(chunking);
  }

  /** Collects token starts. */
  private static void collectTokenStarts(OpenNlpDocument document,
      SegmentationChunker.ChunkSegment segment, Set<Integer> tokenStarts) {
    for (int sentenceIndex : segment.sentenceIndices()) {
      final AnnotatedSentence sentence = document.getSentences(sentenceIndex);
      for (var token : sentence.getTokensList()) {
        final AnnotationSpan span = token.getAnnotationSpan();
        if (span.getStart() < segment.end() && span.getEnd() > segment.start()) {
          tokenStarts.add(span.getStart());
        }
      }
    }
  }

  /** Converts offsets to an annotation span. */
  private static AnnotationSpan toSpan(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  /** Verifies the provider returned exactly one vector per input text. */
  private static void requireFullBatch(String modelId, EmbeddingBatchResult batch, int expected,
      String unit) {
    if (batch.vectors().size() != expected) {
      throw AnalysisException.internal("Embedding model '" + modelId + "' returned "
          + batch.vectors().size() + " vector(s) for " + expected + " " + unit + " text(s)", null);
    }
  }

  /** Copies a primitive vector into its protobuf representation. */
  private static List<Float> toFloatList(float[] vector) {    final List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }

  /** Embedding provider that rejects embed calls; used for chunk-only groups. */
  private static final class NoOpEmbeddingProvider implements EmbeddingProvider {
    /** {@inheritDoc} */
    @Override
    public String backendId() {
      return "none";
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAvailable() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> registeredModelIds() {
      return Set.of();
    }

    /** {@inheritDoc} */
    @Override
    public boolean supportsModel(String modelId) {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public int embeddingDimension(String modelId) {
      return 0;
    }

    /** {@inheritDoc} */
    @Override
    public float[] embed(String modelId, String text) {
      throw AnalysisException.failedPrecondition("embeddings were not requested for this group");
    }
  }
}
