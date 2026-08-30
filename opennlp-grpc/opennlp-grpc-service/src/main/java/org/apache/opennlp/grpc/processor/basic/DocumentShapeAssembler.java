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
package org.apache.opennlp.grpc.processor.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnalyticsAnnotationList;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryAnnotation;
import org.apache.opennlp.grpc.v1.CategoryAnnotationList;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.ChunkGroupAnnotationList;
import org.apache.opennlp.grpc.v1.DocumentWordType;
import org.apache.opennlp.grpc.v1.DocumentLayers;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotation;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotationList;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EntityAnnotationList;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.LanguageScore;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.NormalizationAnnotationList;
import org.apache.opennlp.grpc.v1.StringAnnotation;
import org.apache.opennlp.grpc.v1.StringAnnotationList;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.Token;
import org.apache.opennlp.grpc.v1.TreeAnnotation;
import org.apache.opennlp.grpc.v1.TreeAnnotationList;
import org.apache.opennlp.grpc.v1.SyntacticChunkAnnotationList;
import org.apache.opennlp.grpc.v1.WordTypeAnnotation;
import org.apache.opennlp.grpc.v1.WordTypeAnnotationList;

/**
 * Renders a fully analyzed document as typed, namespaced annotation layers: the
 * annotation layers under {@link OpenNlpDocument#getLayers()}.
 *
 * <p>The string-valued layers are routed through the library's
 * {@link opennlp.tools.document.Document} container itself, under the toolkit's own
 * {@link Layers layer keys}, so the container's invariants (span bounds, positional
 * versus document scope, once-only layers) validate everything before it reaches the
 * wire. The category, embedding, and parse-tree layers render directly, because their
 * value types are the service's typed messages; their ids follow the same
 * {@code opennlp:} naming.</p>
 *
 * <p>Runs before {@link DocumentOffsetEncoder}, so every span here is in Java UTF-16
 * indices; the encoder remaps layer spans together with all other spans.</p>
 */
final class DocumentShapeAssembler {

  /** Lemmas per token, span-aligned with {@link Layers#TOKENS}. */
  static final LayerKey<String> LEMMAS = Layers.key("lemmas", String.class);

  /** Phrase tags of syntactic chunks. */
  static final LayerKey<String> CHUNKS = Layers.key("chunks", String.class);

  /** UAX #29 word classes per token, present only for the uax29 tokenizer engine. */
  static final LayerKey<String> WORD_TYPES = Layers.key("word-types", String.class);

  /** The covered text of each token the stopword list matched. */
  static final LayerKey<String> STOPWORDS = Layers.key("stopwords", String.class);

  /** The detected language code, one whole-document value. */
  static final LayerKey<String> LANGUAGE = Layers.documentKey("language", String.class);

  /** The id prefix of the per-dimension term layers, followed by the dimension name. */
  static final String TERMS_ID_PREFIX = "opennlp:terms:";

  /** The id of the embedding layer. */
  static final String EMBEDDINGS_ID = "opennlp:embeddings";

  /** The id of the parse layer. */
  static final String PARSES_ID = "opennlp:parses";

  /** The id of the sentiment layer. */
  static final String SENTIMENT_ID = "opennlp:sentiment";

  /** The id of the document-classification layer. */
  static final String CATEGORIES_ID = "opennlp:categories";

  /** The id of the subword layer. */
  static final String SUBWORDS_ID = "opennlp:subwords";

  /** The id of the stem layer. */
  static final String STEMS_ID = "opennlp:stems";

  /** The id of the lexical-expansion layer. */
  static final String EXPANSIONS_ID = "opennlp:expansions";

  /** The id of the geocoding layer. */
  static final String GEO_ID = "opennlp:geo";

  /** The id of the document analytics layer. */
  static final String ANALYTICS_ID = "opennlp:analytics";

  /** The id of the normalization layer. */
  static final String NORMALIZATION_ID = "opennlp:normalization";

  /** The id of the strategy chunk group layer. */
  static final String CHUNK_GROUPS_ID = "opennlp:chunk-groups";
  static final String TERM_VECTORS_ID = "opennlp:term-vectors";

  private DocumentShapeAssembler() {
    // This class is a stateless rendering pass and is never instantiated.
  }

  /**
   * Builds the document-shape layers from the analyzed state alone, without
   * step-emitted layers.
   *
   * @param document The fully analyzed document, spans still in UTF-16 indices.
   * @param rawText The analyzed text every span indexes into.
   */
  static void apply(OpenNlpDocument.Builder document, String rawText) {
    apply(document, rawText, List.of());
  }

  /**
   * Builds the document-shape layers from the analyzed state and sets them on the
   * document. A layer whose producing step did not run is absent; when no step
   * produced anything, the layers field stays unset.
   *
   * @param document The fully analyzed document, spans still in UTF-16 indices.
   * @param rawText The analyzed text every span indexes into.
   * @param extraLayers Layers emitted directly by steps whose results live only in
   *                    the document shape; appended after the built-in layers.
   */
  static void apply(
      OpenNlpDocument.Builder document, String rawText, List<AnnotationLayer> extraLayers) {
    try {
      final DocumentLayers.Builder layers = DocumentLayers.newBuilder();
      Document container = Document.of(rawText);
      container = stringLayers(document, rawText, container, layers);
      container = wordTypeLayer(document, container, layers);
      container = entityLayer(document, container, layers);
      container = syntacticChunkLayer(document, container, layers);
      sentimentLayer(document, layers);
      languageLayer(document, container, layers);
      categoriesLayer(document, layers);
      parsesLayer(document, layers);
      embeddingsLayer(document, layers);
      analyticsLayer(document, layers);
      normalizationLayer(document, layers);
      chunkGroupsLayer(document, layers);
      for (AnnotationLayer extra : extraLayers) {
        layers.addLayers(extra);
      }
      if (layers.getLayersCount() > 0) {
        document.setLayers(layers.build());
      }
    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
      // The container (or the covered-text slice) rejected a span or scope the
      // pipeline produced; a server-side bug.
      throw AnalysisException.internal("Document shape assembly failed", e);
    }
  }

  /**
   * Extracts, validates through the container, and renders every string-valued layer,
   * in pipeline order: sentences, tokens, pos, lemmas, word types, stopwords, term
   * dimensions, entities, chunks.
   *
   * @return The container with every present string layer added.
   */
  private static Document stringLayers(
      OpenNlpDocument.Builder document,
      String rawText,
      Document container,
      DocumentLayers.Builder layers) {
    final List<Annotation<String>> sentences = new ArrayList<>();
    final List<Double> sentenceProbs = new ArrayList<>();
    final List<Annotation<String>> tokens = new ArrayList<>();
    final List<Double> tokenProbs = new ArrayList<>();
    final List<Annotation<String>> pos = new ArrayList<>();
    final List<Double> posProbs = new ArrayList<>();
    final List<Annotation<String>> lemmas = new ArrayList<>();
    final List<Annotation<String>> stopwords = new ArrayList<>();
    final Map<String, List<Annotation<String>>> terms = new TreeMap<>();

    for (AnnotatedSentence sentence : document.getSentencesList()) {
      final AnnotationSpan sentenceSpan = sentence.getSentenceSpan();
      sentences.add(annotation(sentenceSpan, covered(rawText, sentenceSpan)));
      sentenceProbs.add(sentenceSpan.hasProbability() ? sentenceSpan.getProbability() : null);
      for (Token token : sentence.getTokensList()) {
        final AnnotationSpan span = token.getAnnotationSpan();
        tokens.add(annotation(span, token.getText()));
        tokenProbs.add(span.hasProbability() ? span.getProbability() : null);
        if (token.hasPosTag()) {
          pos.add(annotation(span, token.getPosTag()));
          posProbs.add(token.hasPosProbability() ? (double) token.getPosProbability() : null);
        }
        if (token.hasLemma()) {
          lemmas.add(annotation(span, token.getLemma()));
        }
        if (token.getIsStopword()) {
          stopwords.add(annotation(span, token.getText()));
        }
        for (String dimension : new TreeSet<>(token.getTermLayersMap().keySet())) {
          terms.computeIfAbsent(dimension, unused -> new ArrayList<>())
              .add(annotation(span, token.getTermLayersOrThrow(dimension)));
        }
      }
    }

    container = addStringLayer(container, layers, Layers.SENTENCES, sentences, sentenceProbs);
    container = addStringLayer(container, layers, Layers.TOKENS, tokens, tokenProbs);
    container = addStringLayer(container, layers, Layers.POS_TAGS, pos, posProbs);
    container = addStringLayer(container, layers, LEMMAS, lemmas, null);
    container = addStringLayer(container, layers, STOPWORDS, stopwords, null);
    for (Map.Entry<String, List<Annotation<String>>> term : terms.entrySet()) {
      container = addStringLayer(container, layers,
          LayerKey.of(TERMS_ID_PREFIX + term.getKey(), String.class), term.getValue(), null);
    }
    return container;
  }

  /** Builds the typed word-classification layer. */
  private static Document wordTypeLayer(
      OpenNlpDocument.Builder document, Document container, DocumentLayers.Builder layers) {
    final List<Annotation<DocumentWordType>> annotations = new ArrayList<>();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        if (token.hasWordType()) {
          annotations.add(new Annotation<>(spanValue(token.getAnnotationSpan()),
              documentWordType(token.getWordType())));
        }
      }
    }
    if (annotations.isEmpty()) {
      return container;
    }
    final LayerKey<DocumentWordType> key =
        Layers.key("word-types", DocumentWordType.class);
    final Document validated = container.with(key, annotations);
    final WordTypeAnnotationList.Builder values = WordTypeAnnotationList.newBuilder();
    for (Annotation<DocumentWordType> annotation : validated.get(key)) {
      values.addAnnotations(WordTypeAnnotation.newBuilder()
          .setSpan(span(annotation.span()))
          .setType(annotation.value()));
    }
    layers.addLayers(layer(key.id())
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setWordTypeValues(values));
    return validated;
  }

  /** Builds the named-entity layer. */
  private static Document entityLayer(
      OpenNlpDocument.Builder document, Document container, DocumentLayers.Builder layers) {
    final List<Annotation<org.apache.opennlp.grpc.v1.NamedEntity>> annotations = new ArrayList<>();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      for (var entity : sentence.getEntitiesList()) {
        annotations.add(new Annotation<>(spanValue(entity.getAnnotationSpan()), entity));
      }
    }
    if (annotations.isEmpty()) {
      return container;
    }
    final LayerKey<org.apache.opennlp.grpc.v1.NamedEntity> key = LayerKey.of(
        "opennlp:entities", org.apache.opennlp.grpc.v1.NamedEntity.class);
    final Document validated = container.with(key, annotations);
    final EntityAnnotationList.Builder values = EntityAnnotationList.newBuilder();
    for (Annotation<org.apache.opennlp.grpc.v1.NamedEntity> annotation : validated.get(key)) {
      values.addAnnotations(annotation.value());
    }
    layers.addLayers(layer(key.id())
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setEntityValues(values));
    return validated;
  }

  /** Builds the syntactic-chunk layer. */
  private static Document syntacticChunkLayer(
      OpenNlpDocument.Builder document, Document container, DocumentLayers.Builder layers) {
    final List<Annotation<ChunkSpan>> annotations = new ArrayList<>();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      if (sentence.hasSyntacticChunks()) {
        for (ChunkSpan chunk : sentence.getSyntacticChunks().getChunksList()) {
          annotations.add(new Annotation<>(spanValue(chunk.getAnnotationSpan()), chunk));
        }
      }
    }
    if (annotations.isEmpty()) {
      return container;
    }
    final LayerKey<ChunkSpan> key = LayerKey.of("opennlp:chunks", ChunkSpan.class);
    final Document validated = container.with(key, annotations);
    final SyntacticChunkAnnotationList.Builder values =
        SyntacticChunkAnnotationList.newBuilder();
    for (Annotation<ChunkSpan> annotation : validated.get(key)) {
      values.addAnnotations(annotation.value());
    }
    layers.addLayers(layer(key.id())
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setSyntacticChunkValues(values));
    return validated;
  }

  /**
   * Adds one string layer to the container (validating it) and renders it, or does
   * nothing when the layer has no annotations.
   *
   * @param probabilities Per-annotation confidences aligned with {@code annotations},
   *                      {@code null} entries (or a {@code null} list) meaning absent.
   * @return The container with the layer added, or unchanged when it was empty.
   */
  private static Document addStringLayer(
      Document container,
      DocumentLayers.Builder layers,
      LayerKey<String> key,
      List<Annotation<String>> annotations,
      List<Double> probabilities) {
    if (annotations.isEmpty()) {
      return container;
    }
    final Document validated = container.with(key, annotations);
    final StringAnnotationList.Builder list = StringAnnotationList.newBuilder();
    final List<Annotation<String>> layer = validated.get(key);
    for (int i = 0; i < layer.size(); i++) {
      final Annotation<String> annotation = layer.get(i);
      final StringAnnotation.Builder rendered = StringAnnotation.newBuilder()
          .setValue(annotation.value());
      if (annotation.span() != null) {
        rendered.setSpan(span(annotation.span()));
      }
      if (probabilities != null && probabilities.get(i) != null) {
        rendered.setProbability(probabilities.get(i));
      }
      list.addAnnotations(rendered.build());
    }
    layers.addLayers(layer(key.id())
        .setScope(key.scope() == LayerKey.Scope.DOCUMENT
            ? LayerScope.LAYER_SCOPE_DOCUMENT
            : LayerScope.LAYER_SCOPE_POSITIONAL)
        .setStringValues(list.build())
        .build());
    return validated;
  }

  /** Renders per-sentence sentiment as scored labels on the sentences' spans. */
  private static void sentimentLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    final CategoryAnnotationList.Builder list = CategoryAnnotationList.newBuilder();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      if (sentence.hasSentimentLabel()) {
        list.addAnnotations(CategoryAnnotation.newBuilder()
            .setSpan(sentence.getSentenceSpan())
            .setLabel(sentence.getSentimentLabel())
            .setScore(sentence.getSentimentConfidence())
            .build());
      }
    }
    if (list.getAnnotationsCount() > 0) {
      layers.addLayers(layer(SENTIMENT_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setCategoryValues(list.build())
          .build());
    }
  }

  /**
   * Renders the detected language as one document-scoped scored label, also adding the
   * code to the container under {@link #LANGUAGE} so the scope rule is enforced.
   */
  private static void languageLayer(
      OpenNlpDocument.Builder document, Document container, DocumentLayers.Builder layers) {
    if (!document.hasDetectedLanguage()) {
      return;
    }
    // The ranked predictions, when requested, carry the detected language as their first
    // entry; without them the layer keeps its single best-prediction annotation.
    final CategoryAnnotationList.Builder categories = CategoryAnnotationList.newBuilder();
    final List<Annotation<String>> labels = new ArrayList<>();
    if (document.getRankedLanguagesCount() > 0) {
      for (LanguageScore score : document.getRankedLanguagesList()) {
        categories.addAnnotations(CategoryAnnotation.newBuilder()
            .setLabel(score.getLanguage())
            .setScore(score.getConfidence())
            .build());
        labels.add(Annotation.of(score.getLanguage()));
      }
    } else {
      categories.addAnnotations(CategoryAnnotation.newBuilder()
          .setLabel(document.getDetectedLanguage())
          .setScore(document.getLanguageConfidence())
          .build());
      labels.add(Annotation.of(document.getDetectedLanguage()));
    }
    container.with(LANGUAGE, labels);
    layers.addLayers(layer(LANGUAGE.id())
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setCategoryValues(categories.build())
        .build());
  }

  /** Renders the whole classification distribution, best category first. */
  private static void categoriesLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    if (!document.hasClassification()) {
      return;
    }
    final var classification = document.getClassification();
    final CategoryAnnotationList.Builder list = CategoryAnnotationList.newBuilder();
    classification.getCategoryScoresMap().entrySet().stream()
        .sorted((a, b) -> {
          if (a.getKey().equals(classification.getBestCategory())) {
            return -1;
          }
          if (b.getKey().equals(classification.getBestCategory())) {
            return 1;
          }
          final int byScore = Double.compare(b.getValue(), a.getValue());
          return byScore != 0 ? byScore : a.getKey().compareTo(b.getKey());
        })
        .forEach(entry -> list.addAnnotations(CategoryAnnotation.newBuilder()
            .setLabel(entry.getKey())
            .setScore(entry.getValue())
            .build()));
    layers.addLayers(layer(CATEGORIES_ID)
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setCategoryValues(list.build())
        .build());
  }

  /** Renders each parsed sentence's primary parse on the sentence's span. */
  private static void parsesLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    final TreeAnnotationList.Builder list = TreeAnnotationList.newBuilder();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      if (sentence.hasParseTree()) {
        final TreeAnnotation.Builder annotation = TreeAnnotation.newBuilder()
            .setSpan(sentence.getSentenceSpan())
            .setTree(sentence.getParseTree());
        if (sentence.getParseTreesCount() > 0) {
          annotation.addAllAlternatives(sentence.getParseTreesList());
        }
        list.addAnnotations(annotation);
      }
    }
    if (list.getAnnotationsCount() > 0) {
      layers.addLayers(layer(PARSES_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setTreeValues(list.build())
          .build());
    }
  }

  /** Renders sentence vectors and document centroids as one embedding layer. */
  private static void embeddingsLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    final EmbeddingAnnotationList.Builder list = EmbeddingAnnotationList.newBuilder();
    for (EmbeddingResult embedding : document.getEmbeddingsList()) {
      list.addAnnotations(embeddingAnnotation(embedding));
    }
    for (EmbeddingResult centroid : document.getDocumentCentroidsList()) {
      list.addAnnotations(embeddingAnnotation(centroid));
    }
    if (list.getAnnotationsCount() > 0) {
      layers.addLayers(layer(EMBEDDINGS_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setEmbeddingValues(list.build())
          .build());
    }
  }

  /** Builds one typed embedding annotation. */
  private static EmbeddingAnnotation embeddingAnnotation(EmbeddingResult embedding) {
    final EmbeddingAnnotation.Builder annotation = EmbeddingAnnotation.newBuilder()
        .setModelId(embedding.getModelId())
        .addAllVector(embedding.getVectorList())
        .setGranularity(embedding.getGranularity())
        .setVectorNormalization(embedding.getVectorNormalization());
    if (embedding.hasSourceSpan()) {
      annotation.setSpan(embedding.getSourceSpan());
    }
    if (embedding.hasRoute()) {
      annotation.setRoute(embedding.getRoute());
    }
    return annotation.build();
  }

  /** Adds document analytics to the layer set. */
  private static void analyticsLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    if (!document.hasAnalytics()) {
      return;
    }
    layers.addLayers(layer(ANALYTICS_ID)
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setAnalyticsValues(AnalyticsAnnotationList.newBuilder()
            .addAnnotations(document.getAnalytics())));
  }

  /** Adds normalization output to the layer set. */
  private static void normalizationLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    if (!document.hasNormalization()) {
      return;
    }
    layers.addLayers(layer(NORMALIZATION_ID)
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setNormalizationValues(NormalizationAnnotationList.newBuilder()
            .addAnnotations(document.getNormalization())));
  }

  /** Adds chunk groups to the layer set. */
  private static void chunkGroupsLayer(
      OpenNlpDocument.Builder document, DocumentLayers.Builder layers) {
    if (document.getChunkEmbeddingGroupsCount() == 0) {
      return;
    }
    layers.addLayers(layer(CHUNK_GROUPS_ID)
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setChunkGroupValues(ChunkGroupAnnotationList.newBuilder()
            .addAllAnnotations(document.getChunkEmbeddingGroupsList())));
  }

  /**
   * Starts a layer with both its compatibility id and its strongly typed identity.
   * Unknown ids remain first-class extension layers through the custom case.
   */
  static AnnotationLayer.Builder layer(String id) {
    final LayerIdentity.Builder identity = LayerIdentity.newBuilder();
    if (id.startsWith(TERMS_ID_PREFIX)) {
      identity.setStandard(StandardLayer.STANDARD_LAYER_TERMS)
          .setQualifier(id.substring(TERMS_ID_PREFIX.length()));
    } else {
      final StandardLayer standard = switch (id) {
        case "opennlp:sentences" -> StandardLayer.STANDARD_LAYER_SENTENCES;
        case "opennlp:tokens" -> StandardLayer.STANDARD_LAYER_TOKENS;
        case "opennlp:pos" -> StandardLayer.STANDARD_LAYER_POS_TAGS;
        case "opennlp:lemmas" -> StandardLayer.STANDARD_LAYER_LEMMAS;
        case "opennlp:entities" -> StandardLayer.STANDARD_LAYER_ENTITIES;
        case "opennlp:chunks" -> StandardLayer.STANDARD_LAYER_SYNTACTIC_CHUNKS;
        case PARSES_ID -> StandardLayer.STANDARD_LAYER_PARSES;
        case SENTIMENT_ID -> StandardLayer.STANDARD_LAYER_SENTIMENT;
        case "opennlp:language" -> StandardLayer.STANDARD_LAYER_LANGUAGE;
        case CATEGORIES_ID -> StandardLayer.STANDARD_LAYER_CATEGORIES;
        case EMBEDDINGS_ID -> StandardLayer.STANDARD_LAYER_EMBEDDINGS;
        case "opennlp:word-types" -> StandardLayer.STANDARD_LAYER_WORD_TYPES;
        case "opennlp:stopwords" -> StandardLayer.STANDARD_LAYER_STOPWORDS;
        case SUBWORDS_ID -> StandardLayer.STANDARD_LAYER_SUBWORDS;
        case STEMS_ID -> StandardLayer.STANDARD_LAYER_STEMS;
        case EXPANSIONS_ID -> StandardLayer.STANDARD_LAYER_EXPANSIONS;
        case GEO_ID -> StandardLayer.STANDARD_LAYER_GEO;
        case NORMALIZATION_ID -> StandardLayer.STANDARD_LAYER_NORMALIZATION;
        case ANALYTICS_ID -> StandardLayer.STANDARD_LAYER_ANALYTICS;
        case CHUNK_GROUPS_ID -> StandardLayer.STANDARD_LAYER_CHUNK_GROUPS;
        case TERM_VECTORS_ID -> StandardLayer.STANDARD_LAYER_TERM_VECTORS;
        default -> StandardLayer.STANDARD_LAYER_UNSPECIFIED;
      };
      if (standard == StandardLayer.STANDARD_LAYER_UNSPECIFIED) {
        identity.setCustom(id);
      } else {
        identity.setStandard(standard);
      }
    }
    return AnnotationLayer.newBuilder().setId(id).setIdentity(identity);
  }

  /** Maps a wire word type to the document-container value. */
  private static DocumentWordType documentWordType(String value) {
    try {
      return DocumentWordType.valueOf("DOCUMENT_WORD_TYPE_" + value);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.internal("Unknown UAX 29 word type '" + value + "'", e);
    }
  }

  /** Converts a wire span to the document-container value. */
  private static Span spanValue(AnnotationSpan span) {
    return new Span(span.getStart(), span.getEnd());
  }

  /** Builds a document-container annotation. */
  private static Annotation<String> annotation(AnnotationSpan span, String value) {
    return new Annotation<>(spanValue(span), value);
  }

  /** Returns the text covered by a span. */
  private static String covered(String rawText, AnnotationSpan span) {
    return rawText.substring(span.getStart(), span.getEnd());
  }

  /** Converts a document-container span to the wire value. */
  private static AnnotationSpan span(Span span) {
    return AnnotationSpan.newBuilder()
        .setStart(span.getStart())
        .setEnd(span.getEnd())
        .setSpace(org.apache.opennlp.grpc.v1.CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }
}
