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

import java.util.List;
import java.util.Locale;

import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import org.apache.opennlp.grpc.v1.CatalogFileDescriptor;
import org.apache.opennlp.grpc.v1.ModelArtifactFormat;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.apache.opennlp.grpc.v1.PipelineStep;

/**
 * What each catalog role means to the running server: whether it serves only after a
 * restart, which startup key its installed file is published under, which pipeline steps
 * it unlocks, and how a catalog entry's file names map to an artifact format. The store
 * and the bootstrap share this so a listing and a restart never disagree.
 */
final class CatalogRoles {

  private CatalogRoles() {
  }

  /**
   * Reports whether a role serves only after a server restart publishes its path.
   *
   * @param role The catalog role.
   * @return {@code true} for every role that is loaded from startup configuration.
   */
  static boolean requiresRestart(ModelArtifactRole role) {
    return switch (role) {
      case MODEL_ARTIFACT_ROLE_PARSER,
           MODEL_ARTIFACT_ROLE_CHUNKER,
           MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR,
           MODEL_ARTIFACT_ROLE_TOKENIZER,
           MODEL_ARTIFACT_ROLE_POS_TAGGER,
           MODEL_ARTIFACT_ROLE_LEMMATIZER,
           MODEL_ARTIFACT_ROLE_NAME_FINDER,
           MODEL_ARTIFACT_ROLE_SUBWORD_MODEL,
           MODEL_ARTIFACT_ROLE_WORDNET_LEXICON,
           MODEL_ARTIFACT_ROLE_DOC_CATEGORIZER -> true;
      default -> false;
    };
  }

  /**
   * Returns the startup configuration key one restart-only role publishes to, or
   * {@code null} for roles that serve without a restart. Parser, chunker, name finder,
   * subword, WordNet and document categorizer keys are per model id (a name finder's
   * model id is its entity type); the sentence detector, tokenizer, POS tagger, and
   * lemmatizer publish into the {@code model.pipeline.<lang>} set of the pack's declared
   * language, so packs for different languages coexist and route by language at request
   * time.
   *
   * @param descriptor The catalog descriptor.
   * @return The configuration key, or {@code null}.
   * @throws IllegalArgumentException If a pipeline role does not declare exactly one language.
   */
  static String restartConfigurationKey(ModelCatalogDescriptor descriptor) {
    if (descriptor == null) {
      throw new IllegalArgumentException("descriptor must not be null");
    }
    final String modelId = descriptor.getModelId();
    return switch (descriptor.getRole()) {
      case MODEL_ARTIFACT_ROLE_PARSER -> "model.parser." + modelId + ".path";
      case MODEL_ARTIFACT_ROLE_CHUNKER -> "model.chunker." + modelId + ".path";
      case MODEL_ARTIFACT_ROLE_NAME_FINDER -> "model.name_finder." + modelId + ".path";
      case MODEL_ARTIFACT_ROLE_SUBWORD_MODEL -> "model.subword." + modelId + ".path";
      case MODEL_ARTIFACT_ROLE_WORDNET_LEXICON -> "model.wordnet." + modelId + ".path";
      case MODEL_ARTIFACT_ROLE_DOC_CATEGORIZER -> "model.doccat." + modelId + ".path";
      case MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR -> pipelineKey(descriptor, "sentence_detector");
      case MODEL_ARTIFACT_ROLE_TOKENIZER -> pipelineKey(descriptor, "tokenizer");
      case MODEL_ARTIFACT_ROLE_POS_TAGGER -> pipelineKey(descriptor, "pos_tagger");
      case MODEL_ARTIFACT_ROLE_LEMMATIZER -> pipelineKey(descriptor, "lemmatizer");
      default -> null;
    };
  }

  /**
   * Lists the pipeline steps a role makes available once its model is active. A teacher
   * unlocks distillation rather than a step, so its list is empty.
   *
   * @param role The catalog role.
   * @return The unlocked steps in pipeline order; empty when the role unlocks none.
   */
  static List<PipelineStep> unlocks(ModelArtifactRole role) {
    return switch (role) {
      case MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING ->
          List.of(PipelineStep.PIPELINE_STEP_EMBED, PipelineStep.PIPELINE_STEP_CHUNK);
      case MODEL_ARTIFACT_ROLE_PARSER -> List.of(PipelineStep.PIPELINE_STEP_PARSE);
      case MODEL_ARTIFACT_ROLE_CHUNKER -> List.of(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK);
      case MODEL_ARTIFACT_ROLE_NAME_FINDER ->
          List.of(PipelineStep.PIPELINE_STEP_NER, PipelineStep.PIPELINE_STEP_GEOCODE);
      case MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR ->
          List.of(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
      case MODEL_ARTIFACT_ROLE_TOKENIZER -> List.of(PipelineStep.PIPELINE_STEP_TOKENIZE);
      case MODEL_ARTIFACT_ROLE_POS_TAGGER -> List.of(PipelineStep.PIPELINE_STEP_POS_TAG);
      case MODEL_ARTIFACT_ROLE_LEMMATIZER -> List.of(PipelineStep.PIPELINE_STEP_LEMMATIZE);
      case MODEL_ARTIFACT_ROLE_SUBWORD_MODEL ->
          List.of(PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE);
      case MODEL_ARTIFACT_ROLE_WORDNET_LEXICON -> List.of(PipelineStep.PIPELINE_STEP_EXPAND);
      case MODEL_ARTIFACT_ROLE_DOC_CATEGORIZER ->
          List.of(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE);
      default -> List.of();
    };
  }

  /**
   * Derives the artifact format from the pinned file names: the first recognised
   * extension wins, so a teacher directory with a tokenizer JSON beside an ONNX graph
   * reads as ONNX.
   *
   * @param files The pinned files.
   * @return The format, or {@code MODEL_ARTIFACT_FORMAT_UNSPECIFIED} when none is recognised.
   */
  static ModelArtifactFormat format(List<CatalogFile> files) {
    ModelArtifactFormat found = ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_UNSPECIFIED;
    for (CatalogFile file : files) {
      final ModelArtifactFormat candidate = formatOf(
          file.relativePath().getFileName().toString().toLowerCase(Locale.ROOT));
      if (candidate != ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_UNSPECIFIED) {
        return candidate;
      }
    }
    return found;
  }

  /**
   * Adds the role-derived and file-derived fields to a catalog descriptor.
   *
   * @param model The catalog entry.
   * @return The descriptor with format, unlocks, restart flag and files filled in.
   */
  static ModelCatalogDescriptor describe(CatalogModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    final ModelCatalogDescriptor.Builder builder = model.descriptor().toBuilder()
        .setFormat(format(model.files()))
        .clearUnlocks().addAllUnlocks(unlocks(model.descriptor().getRole()))
        .setRequiresRestart(requiresRestart(model.descriptor().getRole()))
        .clearFiles();
    for (CatalogFile file : model.files()) {
      builder.addFiles(CatalogFileDescriptor.newBuilder()
          .setRelativePath(portable(file))
          .setByteSize(file.byteSize())
          .setSha256Hex(file.sha256()));
    }
    return builder.build();
  }

  /** Renders a relative path with forward slashes regardless of host separator. */
  private static String portable(CatalogFile file) {
    final StringBuilder path = new StringBuilder();
    for (int i = 0; i < file.relativePath().getNameCount(); i++) {
      if (i > 0) {
        path.append('/');
      }
      path.append(file.relativePath().getName(i));
    }
    return path.toString();
  }

  /** Maps one lower-cased file name to a format by its extension. */
  private static ModelArtifactFormat formatOf(String fileName) {
    if (fileName.endsWith(".onnx")) {
      return ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_ONNX;
    }
    if (fileName.endsWith(".safetensors")) {
      return ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_SAFETENSORS;
    }
    if (fileName.endsWith(".bin")) {
      return ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_OPENNLP_BIN;
    }
    if (fileName.endsWith(".model") || fileName.endsWith(".spm")) {
      return ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_SENTENCEPIECE;
    }
    if (fileName.endsWith(".xml") || fileName.endsWith(".xml.gz")) {
      return ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_WN_LMF;
    }
    return ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_UNSPECIFIED;
  }

  /** Builds a {@code model.pipeline.<lang>.<slot>.path} key for a language pack member. */
  private static String pipelineKey(ModelCatalogDescriptor descriptor, String slot) {
    if (descriptor.getLanguagesCount() != 1 || descriptor.getLanguages(0).isBlank()) {
      throw new IllegalArgumentException("Catalog model '" + descriptor.getCatalogId()
          + "' must declare exactly one language for role " + descriptor.getRole());
    }
    return "model.pipeline." + descriptor.getLanguages(0) + "." + slot + ".path";
  }
}
