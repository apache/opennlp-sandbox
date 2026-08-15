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
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryAnnotation;
import org.apache.opennlp.grpc.v1.CategoryAnnotationList;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.DocumentLayers;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotation;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotationList;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StringAnnotation;
import org.apache.opennlp.grpc.v1.StringAnnotationList;
import org.apache.opennlp.grpc.v1.Token;
import org.apache.opennlp.grpc.v1.TreeAnnotation;
import org.apache.opennlp.grpc.v1.TreeAnnotationList;

/**
 * Renders a fully analyzed document as the document shape of OPENNLP-1888: the typed
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
      sentimentLayer(document, layers);
      languageLayer(document, container, layers);
      categoriesLayer(document, layers);
      parsesLayer(document, layers);
      embeddingsLayer(document, layers);
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
    final List<Annotation<String>> wordTypes = new ArrayList<>();
    final List<Annotation<String>> stopwords = new ArrayList<>();
    final Map<String, List<Annotation<String>>> terms = new TreeMap<>();
    final List<Annotation<String>> entities = new ArrayList<>();
    final List<Double> entityProbs = new ArrayList<>();
    final List<Annotation<String>> chunks = new ArrayList<>();

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
        if (token.hasWordType()) {
          wordTypes.add(annotation(span, token.getWordType()));
        }
        if (token.getIsStopword()) {
          stopwords.add(annotation(span, token.getText()));
        }
        for (String dimension : new TreeSet<>(token.getTermLayersMap().keySet())) {
          terms.computeIfAbsent(dimension, unused -> new ArrayList<>())
              .add(annotation(span, token.getTermLayersOrThrow(dimension)));
        }
      }
      for (org.apache.opennlp.grpc.v1.NamedEntity entity : sentence.getEntitiesList()) {
        entities.add(annotation(entity.getAnnotationSpan(), entity.getEntityType()));
        entityProbs.add(entity.hasProbability() ? entity.getProbability() : null);
      }
      if (sentence.hasSyntacticChunks()) {
        for (ChunkSpan chunk : sentence.getSyntacticChunks().getChunksList()) {
          chunks.add(annotation(chunk.getAnnotationSpan(), chunk.getChunkTag()));
        }
      }
    }

    container = addStringLayer(container, layers, Layers.SENTENCES, sentences, sentenceProbs);
    container = addStringLayer(container, layers, Layers.TOKENS, tokens, tokenProbs);
    container = addStringLayer(container, layers, Layers.POS_TAGS, pos, posProbs);
    container = addStringLayer(container, layers, LEMMAS, lemmas, null);
    container = addStringLayer(container, layers, WORD_TYPES, wordTypes, null);
    container = addStringLayer(container, layers, STOPWORDS, stopwords, null);
    for (Map.Entry<String, List<Annotation<String>>> term : terms.entrySet()) {
      container = addStringLayer(container, layers,
          LayerKey.of(TERMS_ID_PREFIX + term.getKey(), String.class), term.getValue(), null);
    }
    container = addStringLayer(container, layers, Layers.ENTITIES, entities, entityProbs);
    container = addStringLayer(container, layers, CHUNKS, chunks, null);
    return container;
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
    layers.addLayers(AnnotationLayer.newBuilder()
        .setId(key.id())
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
      layers.addLayers(AnnotationLayer.newBuilder()
          .setId(SENTIMENT_ID)
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
    container.with(LANGUAGE, List.of(Annotation.of(document.getDetectedLanguage())));
    layers.addLayers(AnnotationLayer.newBuilder()
        .setId(LANGUAGE.id())
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setCategoryValues(CategoryAnnotationList.newBuilder()
            .addAnnotations(CategoryAnnotation.newBuilder()
                .setLabel(document.getDetectedLanguage())
                .setScore(document.getLanguageConfidence())
                .build())
            .build())
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
    layers.addLayers(AnnotationLayer.newBuilder()
        .setId(CATEGORIES_ID)
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
        list.addAnnotations(TreeAnnotation.newBuilder()
            .setSpan(sentence.getSentenceSpan())
            .setTree(sentence.getParseTree())
            .build());
      }
    }
    if (list.getAnnotationsCount() > 0) {
      layers.addLayers(AnnotationLayer.newBuilder()
          .setId(PARSES_ID)
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
      layers.addLayers(AnnotationLayer.newBuilder()
          .setId(EMBEDDINGS_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setEmbeddingValues(list.build())
          .build());
    }
  }

  private static EmbeddingAnnotation embeddingAnnotation(EmbeddingResult embedding) {
    final EmbeddingAnnotation.Builder annotation = EmbeddingAnnotation.newBuilder()
        .setModelId(embedding.getModelId())
        .addAllVector(embedding.getVectorList())
        .setGranularity(embedding.getGranularity());
    if (embedding.hasSourceSpan()) {
      annotation.setSpan(embedding.getSourceSpan());
    }
    return annotation.build();
  }

  private static Annotation<String> annotation(AnnotationSpan span, String value) {
    return new Annotation<>(new Span(span.getStart(), span.getEnd()), value);
  }

  private static String covered(String rawText, AnnotationSpan span) {
    return rawText.substring(span.getStart(), span.getEnd());
  }

  private static AnnotationSpan span(Span span) {
    return AnnotationSpan.newBuilder()
        .setStart(span.getStart())
        .setEnd(span.getEnd())
        .setSpace(org.apache.opennlp.grpc.v1.CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }
}
