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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.search.DynamicSearchIndexRegistry;
import org.apache.opennlp.grpc.search.IndexAliasRegistry;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.LearnVocabularyStart;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexDurability;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingModelPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingStart;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;

/** Production orchestration for one bounded streaming training transaction. */
public final class DefaultStreamingTrainingPipeline implements StreamingTrainingPipeline {

  private final DocumentAnalyzer analyzer;
  private final VocabularyArtifactStore vocabularies;
  private final StaticModelArtifactStore models;
  private final DynamicSearchIndexRegistry indexes;
  private final IndexAliasRegistry aliases;
  private final Limits limits;

  /**
   * Creates the production pipeline over the server's shared stores and registries.
   *
   * @param analyzer Document-shape analyzer.
   * @param vocabularies Vocabulary artifact store.
   * @param models Static-model artifact store.
   * @param indexes Dynamic search index registry.
   * @param aliases Search index alias registry.
   */
  public DefaultStreamingTrainingPipeline(
      DocumentAnalyzer analyzer,
      VocabularyArtifactStore vocabularies,
      StaticModelArtifactStore models,
      DynamicSearchIndexRegistry indexes,
      IndexAliasRegistry aliases) {
    if (analyzer == null || vocabularies == null || models == null
        || indexes == null || aliases == null) {
      throw new IllegalArgumentException("streaming training dependencies must not be null");
    }
    this.analyzer = analyzer;
    this.vocabularies = vocabularies;
    this.models = models;
    this.indexes = indexes;
    this.aliases = aliases;
    this.limits = new Limits(
        Math.min(vocabularies.maxCorpusDocuments(), indexes.maxDocumentsPerIndex()),
        Math.min(vocabularies.maxCorpusBytes(), indexes.maxSourceDocumentBytesPerIndex()),
        models.writesEnabled(), indexes.isEnabled());
  }

  @Override
  public Limits limits() {
    return limits;
  }

  @Override
  public void validateStart(StreamingTrainingStart start) {
    if (!vocabularies.writesEnabled()) {
      throw new IllegalStateException(
          "vocabulary.artifact_root is not configured; streaming training is disabled");
    }
    vocabularies.requireDictionary(start.getVocabulary().getDictionaryArtifactId());
    if (start.hasModel()) {
      models.validateTrainingPlan(start.getModel());
    }
    if (start.hasIndex()) {
      indexes.validateProvider(start.getIndex().hasProvider()
          ? start.getIndex().getProvider() : null);
    }
  }

  @Override
  public DocumentAnalysisSession openAnalysis(AnalyzeStreamConfiguration configuration) {
    return analyzer.openSession(configuration);
  }

  @Override
  public VocabularyArtifactDescriptor learnVocabulary(
      LearnVocabularyStart start, List<OpenNlpDocument> documents) throws IOException {
    return vocabularies.learnVocabulary(start, documents);
  }

  @Override
  public StaticModelDescriptor trainModel(
      StreamingTrainingModelPlan plan,
      String vocabularyArtifactId,
      Consumer<String> progress,
      BooleanSupplier cancelled) throws IOException {
    final TrainStaticModelRequest request = TrainStaticModelRequest.newBuilder()
        .setVocabularyArtifactId(vocabularyArtifactId)
        .setTeacherId(plan.getTeacherId())
        .setDisplayName(plan.getDisplayName())
        .setPcaDims(plan.getPcaDims())
        .setProvenanceSummary(plan.getProvenanceSummary())
        .build();
    return models.trainStaticModel(request, progress::accept, cancelled);
  }

  @Override
  public IndexPublication createIndex(
      StreamingTrainingStart start,
      StaticModelDescriptor model,
      List<OpenNlpDocument> documents,
      BooleanSupplier cancelled) throws IOException {
    requireActive(cancelled);
    final StreamingTrainingIndexPlan plan = start.getIndex();
    final AnalyzeStreamConfiguration configuration = indexAnalysis(start, model.getArtifactId());
    final DocumentAnalysisSession session = analyzer.openSession(configuration);
    final List<OpenNlpDocument> batch = new ArrayList<>(indexes.maxDocumentsPerRequest());
    IndexDocumentsResponse latest = null;
    String indexId = null;
    try {
      for (OpenNlpDocument document : documents) {
        requireActive(cancelled);
        final AnalyzeDocumentResponse analyzed = session.analyze(document);
        batch.add(analyzed.getDocument());
        if (batch.size() == indexes.maxDocumentsPerRequest()) {
          latest = publishBatch(plan, model.getArtifactId(), indexId, batch);
          indexId = latest.getIndex().getIndexId();
          batch.clear();
        }
      }
      if (!batch.isEmpty()) {
        latest = publishBatch(plan, model.getArtifactId(), indexId, batch);
        indexId = latest.getIndex().getIndexId();
      }
      requireActive(cancelled);
      if (latest == null || indexId == null) {
        throw new IllegalArgumentException("StreamingTraining index corpus must not be empty");
      }
      final SearchIndexDescriptor descriptor = switch (plan.getDurability()) {
        case STREAMING_TRAINING_INDEX_DURABILITY_PROCESS_LOCAL -> latest.getIndex();
        case STREAMING_TRAINING_INDEX_DURABILITY_PERSISTED -> indexes.persist(indexId);
        case STREAMING_TRAINING_INDEX_DURABILITY_SEALED -> indexes.seal(indexId);
        case STREAMING_TRAINING_INDEX_DURABILITY_UNSPECIFIED, UNRECOGNIZED ->
            throw new IllegalArgumentException(
                "StreamingTraining index durability must be specified");
      };
      latest = latest.toBuilder().setIndex(descriptor).build();
      final AliasState alias = publishAlias(plan, indexId);
      final String publishedId = indexId;
      final AliasState publishedAlias = alias;
      return new IndexPublication(latest,
          () -> rollbackIndex(publishedId, publishedAlias));
    } catch (AnalysisException | UncheckedIOException | IllegalArgumentException
        | IllegalStateException e) {
      cleanupIndex(indexId, e);
      throw e;
    } catch (RuntimeException indexProviderFailure) {
      final StreamingTrainingPublicationException failure =
          new StreamingTrainingPublicationException(
              "Unexpected dynamic index publication failure", indexProviderFailure);
      cleanupIndex(indexId, failure);
      throw failure;
    }
  }

  /** Removes a partially published index while preserving the stage failure. */
  private void cleanupIndex(String indexId, RuntimeException failure) {
    if (indexId == null) {
      return;
    }
    try {
      indexes.delete(indexId);
    } catch (AnalysisException | UncheckedIOException | IllegalArgumentException
        | IllegalStateException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    } catch (RuntimeException indexProviderCleanupFailure) {
      failure.addSuppressed(new StreamingTrainingPublicationException(
          "Unexpected dynamic index cleanup failure", indexProviderCleanupFailure));
    }
  }

  /** Builds the final analysis configuration with the new model injected. */
  static AnalyzeStreamConfiguration indexAnalysis(
      StreamingTrainingStart start, String modelId) {
    final EmbeddingSelector selector = EmbeddingSelector.newBuilder().setModelId(modelId).build();
    final AnalyzeStreamConfiguration.Builder configuration = start.getAnalysis().toBuilder()
        .clearChunkEmbedConfigs()
        .clearCategoryChunkConfigs();
    for (ChunkEmbedConfigEntry entry : start.getIndex().getChunkEmbedConfigsList()) {
      configuration.addChunkEmbedConfigs(entry.toBuilder().addEmbeddingSelectors(selector));
    }
    for (CategoryChunkConfigEntry entry : start.getIndex().getCategoryChunkConfigsList()) {
      configuration.addCategoryChunkConfigs(entry.toBuilder().addEmbeddingSelectors(selector));
    }
    return configuration.build();
  }

  /** Publishes one bounded index batch, creating or extending the same index. */
  private IndexDocumentsResponse publishBatch(
      StreamingTrainingIndexPlan plan,
      String modelId,
      String indexId,
      List<OpenNlpDocument> batch) {
    final IndexDocumentsRequest.Builder request = IndexDocumentsRequest.newBuilder()
        .setDisplayName(plan.getDisplayName())
        .setEmbedding(EmbeddingSelector.newBuilder().setModelId(modelId))
        .addAllDocuments(batch);
    if (indexId != null) {
      request.setIndexId(indexId);
    }
    if (plan.hasProvider()) {
      request.setProvider(plan.getProvider());
    }
    return indexes.index(request.build());
  }

  /** Publishes an optional alias and captures its previous mapping for rollback. */
  private AliasState publishAlias(StreamingTrainingIndexPlan plan, String indexId) {
    if (!plan.hasAlias()) {
      return null;
    }
    final String alias = plan.getAlias();
    synchronized (aliases) {
      final String previous = aliases.isAlias(alias) ? aliases.resolve(alias) : null;
      aliases.set(alias, indexId);
      return new AliasState(alias, indexId, previous);
    }
  }

  /** Restores an alias only when it still names this publication, then removes the index. */
  private void rollbackIndex(String indexId, AliasState alias) throws IOException {
    RuntimeException aliasFailure = null;
    if (alias != null) {
      synchronized (aliases) {
        if (aliases.isAlias(alias.alias())
            && alias.indexId().equals(aliases.resolve(alias.alias()))) {
          try {
            if (alias.previousIndexId() == null) {
              aliases.delete(alias.alias());
            } else {
              aliases.set(alias.alias(), alias.previousIndexId());
            }
          } catch (UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            aliasFailure = e;
          } catch (RuntimeException aliasStoreFailure) {
            aliasFailure = new StreamingTrainingPublicationException(
                "Unexpected index alias rollback failure", aliasStoreFailure);
          }
        }
      }
    }
    try {
      indexes.delete(indexId);
    } catch (AnalysisException | UncheckedIOException | IllegalArgumentException
        | IllegalStateException e) {
      if (aliasFailure != null) {
        e.addSuppressed(aliasFailure);
      }
      throw e;
    } catch (RuntimeException indexProviderFailure) {
      final StreamingTrainingPublicationException failure =
          new StreamingTrainingPublicationException(
              "Unexpected dynamic index rollback failure", indexProviderFailure);
      if (aliasFailure != null) {
        failure.addSuppressed(aliasFailure);
      }
      throw failure;
    }
    if (aliasFailure != null) {
      throw aliasFailure;
    }
  }

  @Override
  public void deleteModel(String artifactId) throws IOException {
    models.deleteModel(artifactId);
  }

  @Override
  public void deleteVocabulary(String artifactId) throws IOException {
    vocabularies.deleteVocabulary(artifactId);
  }

  /** Throws at each expensive or durable boundary after client cancellation. */
  private static void requireActive(BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("StreamingTraining call is cancelled");
    }
  }

  /** Prior alias state captured by a publication. */
  private record AliasState(String alias, String indexId, String previousIndexId) {
  }
}
