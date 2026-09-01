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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import opennlp.geo.BundledGazetteer;
import opennlp.geo.SpatialCoherenceGeocoder;
import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoResolution;
import opennlp.tools.geo.Geocoder;
import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.lemmatizer.LemmatizerAnnotator;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerAnnotator;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorAnnotator;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.stemmer.StemmerAnnotator;
import opennlp.tools.tokenize.SubwordPiece;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerAnnotator;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.lattice.LatticeTokenizer;
import opennlp.tools.tokenize.uax29.WordToken;
import opennlp.tools.tokenize.uax29.WordTokenizer;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.Dimension;
import opennlp.tools.util.normalizer.NormalizationProfile;
import opennlp.tools.util.normalizer.NormalizationProfiles;
import opennlp.tools.util.normalizer.OffsetAwareNormalizer;
import opennlp.tools.util.normalizer.Term;
import opennlp.tools.util.normalizer.TermAnalyzer;
import opennlp.tools.util.normalizer.TextNormalizer;
import opennlp.wordnet.LexicalExpander;
import org.apache.opennlp.grpc.model.ChunkerRegistry;
import org.apache.opennlp.grpc.model.ClassicLanguagePipeline;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.ParserRegistry;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DocumentClassification;
import org.apache.opennlp.grpc.v1.EnginePolicy;
import org.apache.opennlp.grpc.v1.GeoAnnotation;
import org.apache.opennlp.grpc.v1.GeoAnnotationList;
import org.apache.opennlp.grpc.v1.GeoResolutionResult;
import org.apache.opennlp.grpc.v1.LanguageScore;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.LexicalExpansionAnnotation;
import org.apache.opennlp.grpc.v1.LexicalExpansionAnnotationList;
import org.apache.opennlp.grpc.v1.LexicalExpansionKind;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.NormalizationResult;
import org.apache.opennlp.grpc.v1.NormalizationSpec;
import org.apache.opennlp.grpc.v1.Normalizer;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.POSTagFormat;
import org.apache.opennlp.grpc.v1.ParseFormat;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.StemAnnotation;
import org.apache.opennlp.grpc.v1.StemAnnotationList;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.apache.opennlp.grpc.v1.SubwordAnnotation;
import org.apache.opennlp.grpc.v1.SubwordAnnotationList;
import org.apache.opennlp.grpc.v1.TermLayerSpec;
import org.apache.opennlp.grpc.v1.Token;

/**
 * Executes the classic OpenNLP annotation steps (language detection, sentence
 * detection, tokenization, named entity recognition, POS tagging, lemmatization)
 * against the shared models in the {@link ModelBundleCache}, writing results into
 * the document builder.
 *
 * <p>All spans are produced in Java UTF-16 indices; the final offset-encoding pass
 * converts them to the client-requested encoding.</p>
 */
final class ClassicStepRunner {

  private final ModelBundleCache modelBundleCache;
  private final ClassicDocumentMapper documentMapper;

  /**
   * Creates a runner over one loaded model bundle.
   *
   * @param modelBundleCache The models and registries used by classic pipeline steps.
   */
  ClassicStepRunner(ModelBundleCache modelBundleCache) {
    if (modelBundleCache == null) {
      throw new IllegalArgumentException("modelBundleCache must not be null");
    }
    this.modelBundleCache = modelBundleCache;
    this.documentMapper = new ClassicDocumentMapper();
  }

  /**
   * Predicts the dominant document language and records it as an ISO 639-3 code
   * (e.g. {@code "eng"}) together with the model confidence. A positive
   * {@code rankedLanguageCount} additionally records that many ranked predictions,
   * best first, as {@link OpenNlpDocument#getRankedLanguagesList()}.
   */
  void detectLanguage(
      String rawText,
      OpenNlpDocument.Builder document,
      int rankedLanguageCount,
      List<ProcessingDiagnostic> diagnostics) {
    final LanguageDetectorME detector = modelBundleCache.getLanguageDetector();
    final Language language;
    if (rankedLanguageCount > 0) {
      final Language[] ranked = detector.predictLanguages(rawText);
      final int limit = Math.min(rankedLanguageCount, ranked.length);
      for (int i = 0; i < limit; i++) {
        document.addRankedLanguages(LanguageScore.newBuilder()
            .setLanguage(ranked[i].getLang())
            .setConfidence((float) ranked[i].getConfidence())
            .build());
      }
      language = ranked.length > 0 ? ranked[0] : detector.predictLanguage(rawText);
    } else {
      language = detector.predictLanguage(rawText);
    }
    document.setDetectedLanguage(language.getLang());
    document.setLanguageConfidence((float) language.getConfidence());
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
        "Detected language '" + language.getLang() + "' (confidence "
            + language.getConfidence() + ")"));
  }

  /** Detects sentences with the routed pipeline's detector, appending document spans. */
  void detectSentences(
      ClassicLanguagePipeline pipeline,
      String rawText,
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final SentenceDetectorME detector = pipeline.sentenceDetector();
    final Document annotated = new SentenceDetectorAnnotator(detector)
        .annotate(Document.of(rawText));
    final List<Annotation<String>> sentences = annotated.get(Layers.SENTENCES);
    final double[] probabilities = includeProbabilities ? detector.probs() : null;
    for (int i = 0; i < sentences.size(); i++) {
      final Span sentence = sentences.get(i).span();
      final AnnotationSpan.Builder span = AnnotationSpan.newBuilder()
          .setStart(sentence.getStart())
          .setEnd(sentence.getEnd())
          .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT);
      if (probabilities != null && i < probabilities.length) {
        span.setProbability(probabilities[i]);
      }
      document.addSentences(AnnotatedSentence.newBuilder().setSentenceSpan(span).build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        "Detected " + sentences.size() + " sentence(s)"));
  }

  /** Detects sentence spans with a deterministic standard or custom engine. */
  void detectSentences(
      String rawText,
      OpenNlpDocument.Builder document,
      SentenceDetector detector,
      String engineId,
      List<ProcessingDiagnostic> diagnostics) {
    final Document annotated = new SentenceDetectorAnnotator(detector)
        .annotate(Document.of(rawText));
    final List<Annotation<String>> sentences = annotated.get(Layers.SENTENCES);
    for (Annotation<String> sentence : sentences) {
      final Span span = sentence.span();
      document.addSentences(AnnotatedSentence.newBuilder()
          .setSentenceSpan(AnnotationSpan.newBuilder()
              .setStart(span.getStart())
              .setEnd(span.getEnd())
              .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
          .build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        "Detected " + sentences.size() + " sentence(s) (" + engineId + ")"));
  }

  /** Tokenizes every sentence with the routed pipeline's tokenizer, keeping offsets. */
  void tokenize(
      ClassicLanguagePipeline pipeline,
      String rawText,
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final TokenizerME tokenizer = pipeline.tokenizer();
    final RecordingTokenizer recording = includeProbabilities
        ? new RecordingTokenizer(tokenizer) : null;
    final Tokenizer delegate = recording == null ? tokenizer : recording;
    final Document annotated = new TokenizerAnnotator(delegate)
        .annotate(documentMapper.withSentences(rawText, document));
    final int tokenCount = writeTokens(
        annotated, document, recording == null ? null : recording.probabilities());
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Tokenized " + tokenCount + " token(s)"));
  }

  /** Tokenizes each detected sentence with a deterministic standard or custom engine. */
  void tokenize(
      String rawText,
      OpenNlpDocument.Builder document,
      Tokenizer tokenizer,
      String engineId,
      List<ProcessingDiagnostic> diagnostics) {
    final Document annotated = new TokenizerAnnotator(tokenizer)
        .annotate(documentMapper.withSentences(rawText, document));
    final int tokenCount = writeTokens(annotated, document, null);
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Tokenized " + tokenCount + " token(s) (" + engineId + ")"));
  }

  /** Projects a flat token layer back into the wire document's sentences. */
  private int writeTokens(Document annotated, OpenNlpDocument.Builder wire,
      RecordedProbabilities probabilities) {
    final List<Annotation<String>> tokens = annotated.get(Layers.TOKENS);
    int tokenIndex = 0;
    for (int i = 0; i < wire.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = wire.getSentences(i);
      final Span sentenceSpan = new Span(
          sentence.getSentenceSpan().getStart(), sentence.getSentenceSpan().getEnd());
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      while (tokenIndex < tokens.size() && sentenceSpan.contains(tokens.get(tokenIndex).span())) {
        final Annotation<String> token = tokens.get(tokenIndex);
        final AnnotationSpan.Builder span = AnnotationSpan.newBuilder()
            .setStart(token.span().getStart())
            .setEnd(token.span().getEnd())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT);
        if (probabilities != null && probabilities.has(tokenIndex)) {
          span.setProbability(probabilities.get(tokenIndex));
        }
        sentenceBuilder.addTokens(Token.newBuilder()
            .setText(token.value())
            .setAnnotationSpan(span)
            .build());
        tokenIndex++;
      }
      wire.setSentences(i, sentenceBuilder.build());
    }
    return tokenIndex;
  }

  // The UAX #29 word tokenizer is stateless and thread-safe; one shared instance.
  private static final WordTokenizer UAX29_TOKENIZER = new WordTokenizer();

  /**
   * Tokenizes each sentence with the rule-based Unicode UAX&#160;#29 word tokenizer
   * (AnalysisProfile.tokenizer_engine = "uax29"). Needs no model, and additionally
   * classifies each token ({@code Token.word_type}). No probabilities: the
   * segmentation is deterministic, not statistical.
   */
  static void tokenizeUax29(
      String rawText,
      OpenNlpDocument.Builder document,
      List<ProcessingDiagnostic> diagnostics) {
    int tokenCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotationSpan sentenceSpan = sentence.getSentenceSpan();
      final String sentenceText = rawText.substring(sentenceSpan.getStart(), sentenceSpan.getEnd());
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (final WordToken word : UAX29_TOKENIZER.tokenizeTyped(sentenceText)) {
        final AnnotationSpan.Builder span = AnnotationSpan.newBuilder()
            .setStart(sentenceSpan.getStart() + word.span().getStart())
            .setEnd(sentenceSpan.getStart() + word.span().getEnd())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT);
        sentenceBuilder.addTokens(Token.newBuilder()
            .setText(sentenceText.substring(word.span().getStart(), word.span().getEnd()))
            .setAnnotationSpan(span)
            .setWordType(word.type().name())
            .build());
        tokenCount++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Tokenized " + tokenCount + " token(s) (uax29)"));
  }

  /**
   * Runs PIPELINE_STEP_STEM: stems every word token with the stemmer the spec selects
   * and emits the stems as the {@code opennlp:stems} document-shape layer,
   * span-aligned with the token layer. Annotation only: tokens and lemmas are
   * untouched. Runs after tokenization.
   */
  void stem(
      OpenNlpDocument.Builder document,
      StemmerSpec spec,
      List<AnnotationLayer> extraLayers,
      List<ProcessingDiagnostic> diagnostics) {
    final UnaryOperator<String> stem =
        StemmerSelector.newStemFunction(spec, modelBundleCache.getHunspellRegistry());
    final StemAnnotationList.Builder list = StemAnnotationList.newBuilder();
    final StemmerAlgorithm algorithm = spec.getAlgorithm()
        == StemmerAlgorithm.STEMMER_ALGORITHM_UNSPECIFIED
            ? StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL : spec.getAlgorithm();
    final Document annotated = new StemmerAnnotator(word -> stem.apply(word.toString()))
        .annotate(documentMapper.withTokens(document.getRawText(), document));
    for (Annotation<String> stemAnnotation : annotated.get(StemmerAnnotator.STEMS)) {
      final StemAnnotation.Builder annotation = StemAnnotation.newBuilder()
          .setSpan(AnnotationSpan.newBuilder()
              .setStart(stemAnnotation.span().getStart())
              .setEnd(stemAnnotation.span().getEnd())
              .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
          .setStem(stemAnnotation.value())
          .setAlgorithm(algorithm);
      if (spec.hasLanguage()) {
        annotation.setLanguage(spec.getLanguage());
      }
      if (spec.hasHunspellDictionaryId()) {
        annotation.setHunspellDictionaryId(spec.getHunspellDictionaryId());
      }
      list.addAnnotations(annotation);
    }
    if (list.getAnnotationsCount() > 0) {
      extraLayers.add(DocumentShapeAssembler.layer(DocumentShapeAssembler.STEMS_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setStemValues(list.build())
          .build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_STEM,
        "Stemmed " + list.getAnnotationsCount() + " token(s)"));
  }

  // The bundled Natural Earth gazetteer and the coherence geocoder over it are immutable
  // and thread-safe; one shared instance serves every request.
  private static final Geocoder GEOCODER =
      new SpatialCoherenceGeocoder(BundledGazetteer.getInstance());

  // Entity types the geocode step resolves; everything else (persons, organizations)
  // is left untouched.
  private static final Set<String> GEOCODABLE_ENTITY_TYPES =
      Set.of("location", "gpe", "place", "city", "country");

  /**
   * Runs PIPELINE_STEP_GEOCODE: resolves the location-typed entity mentions against
   * the bundled Natural Earth gazetteer, sets {@code NamedEntity.geo} on every
   * resolved entity, and emits the resolutions as the {@code opennlp:geo}
   * document-shape layer. An unresolvable mention stays unenriched. Runs after NER.
   */
  void geocode(
      OpenNlpDocument.Builder document,
      List<AnnotationLayer> extraLayers,
      List<ProcessingDiagnostic> diagnostics) {
    final List<Span> mentions = new ArrayList<>();
    final List<int[]> entityRefs = new ArrayList<>();
    for (int s = 0; s < document.getSentencesCount(); s++) {
      final AnnotatedSentence sentence = document.getSentences(s);
      for (int e = 0; e < sentence.getEntitiesCount(); e++) {
        final NamedEntity entity = sentence.getEntities(e);
        if (GEOCODABLE_ENTITY_TYPES.contains(
            StringUtil.toLowerCase(entity.getEntityType()))) {
          mentions.add(new Span(entity.getAnnotationSpan().getStart(),
              entity.getAnnotationSpan().getEnd()));
          entityRefs.add(new int[] {s, e});
        }
      }
    }
    int resolved = 0;
    if (!mentions.isEmpty()) {
      final List<GeoResolution> resolutions;
      try {
        resolutions = GEOCODER.resolve(document.getRawText(), mentions);
      } catch (java.io.IOException e) {
        // The bundled in-memory gazetteer never throws; reaching this is a server bug.
        throw AnalysisException.internal("Geocoding failed", e);
      }
      final GeoAnnotationList.Builder layer = GeoAnnotationList.newBuilder();
      int m = 0;
      for (GeoResolution resolution : resolutions) {
        while (m < mentions.size()
            && (mentions.get(m).getStart() != resolution.mention().getStart()
                || mentions.get(m).getEnd() != resolution.mention().getEnd())) {
          m++;
        }
        if (m >= mentions.size()) {
          break;
        }
        final int[] ref = entityRefs.get(m);
        final AnnotatedSentence.Builder sentence = document.getSentences(ref[0]).toBuilder();
        final NamedEntity entity = sentence.getEntities(ref[1]);
        final GeoResolutionResult result = toResult(resolution);
        sentence.setEntities(ref[1], entity.toBuilder().setGeo(result).build());
        document.setSentences(ref[0], sentence.build());
        layer.addAnnotations(GeoAnnotation.newBuilder()
            .setSpan(entity.getAnnotationSpan())
            .setResolution(result)
            .build());
        resolved++;
        m++;
      }
      if (layer.getAnnotationsCount() > 0) {
        extraLayers.add(DocumentShapeAssembler.layer(DocumentShapeAssembler.GEO_ID)
            .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
            .setGeoValues(layer.build())
            .build());
      }
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_GEOCODE,
        "Geocoded " + resolved + " of " + mentions.size() + " location mention(s)"));
  }

  /** Renders one library resolution as the typed wire result. */
  private static GeoResolutionResult toResult(GeoResolution resolution) {
    final GazetteerEntry entry = resolution.entry();
    final GeoResolutionResult.Builder result = GeoResolutionResult.newBuilder()
        .setSource(entry.source())
        .setRecordId(entry.recordId())
        .setName(entry.name())
        .setLatitude(entry.location().latitude())
        .setLongitude(entry.location().longitude())
        .setPopulation(entry.population())
        .setConfidence(resolution.confidence());
    if (entry.countryCode() != null) {
      result.setCountryCode(entry.countryCode());
    }
    if (entry.containment() != null) {
      result.addAllContainment(entry.containment());
    }
    if (entry.featureClass() != null) {
      result.setFeatureClass(entry.featureClass());
    }
    return result.build();
  }

  /**
   * Runs PIPELINE_STEP_EXPAND: expands every word token over the selected WordNet
   * knowledge base and emits the expansions as the {@code opennlp:expansions}
   * document-shape layer, one scored label per expansion anchored on the expanded
   * token's span. A token's lemma is expanded when lemmatization ran, otherwise its
   * surface form. Runs after tokenization.
   */
  void expand(
      OpenNlpDocument.Builder document,
      String lexiconId,
      List<AnnotationLayer> extraLayers,
      List<ProcessingDiagnostic> diagnostics) {
    final LexicalExpander expander = modelBundleCache.getWordNetRegistry().get(lexiconId);
    final LexicalExpansionAnnotationList.Builder list =
        LexicalExpansionAnnotationList.newBuilder();
    int expandedTokens = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      for (Token token : document.getSentences(i).getTokensList()) {
        final String term = token.hasLemma() ? token.getLemma() : token.getText();
        final List<LexicalExpander.Expansion> expansions = expander.expand(term);
        if (expansions.isEmpty()) {
          continue;
        }
        expandedTokens++;
        for (LexicalExpander.Expansion expansion : expansions) {
          list.addAnnotations(LexicalExpansionAnnotation.newBuilder()
              .setSpan(token.getAnnotationSpan())
              .setTerm(expansion.term())
              .setKind(
                  switch (expansion.kind()) {
                    case SYNONYM -> LexicalExpansionKind.LEXICAL_EXPANSION_KIND_SYNONYM;
                    case HYPERNYM -> LexicalExpansionKind.LEXICAL_EXPANSION_KIND_HYPERNYM;
                    case HYPONYM -> LexicalExpansionKind.LEXICAL_EXPANSION_KIND_HYPONYM;
                  })
              .setDepth(expansion.depth())
              .setSenseRank(expansion.senseRank())
              .setWeight(expansion.weight())
              .setLexiconId(lexiconId)
              .build());
        }
      }
    }
    if (list.getAnnotationsCount() > 0) {
      extraLayers.add(DocumentShapeAssembler.layer(DocumentShapeAssembler.EXPANSIONS_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setLexicalExpansionValues(list.build())
          .build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_EXPAND,
        "Expanded " + expandedTokens + " token(s) into " + list.getAnnotationsCount()
            + " expansion(s) with lexicon '" + lexiconId + "'"));
  }

  /**
   * Runs PIPELINE_STEP_SUBWORD_TOKENIZE: encodes the whole document text into subword
   * pieces with the selected SentencePiece model and emits them as the
   * {@code opennlp:subwords} document-shape layer. Independent of sentence detection
   * and word tokenization; the word-token results are untouched.
   */
  void subwordTokenize(
      String rawText,
      String modelId,
      List<AnnotationLayer> extraLayers,
      List<ProcessingDiagnostic> diagnostics) {
    final SentencePieceTokenizer tokenizer = modelBundleCache.getSubwordRegistry().get(modelId);
    final SubwordAnnotationList.Builder list = SubwordAnnotationList.newBuilder();
    for (SubwordPiece piece : tokenizer.encode(rawText)) {
      list.addAnnotations(SubwordAnnotation.newBuilder()
          .setSpan(AnnotationSpan.newBuilder()
              .setStart(piece.start())
              .setEnd(piece.end())
              .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
          .setPiece(piece.piece())
          .setVocabularyId(piece.id())
          .build());
    }
    if (list.getAnnotationsCount() > 0) {
      extraLayers.add(DocumentShapeAssembler.layer(DocumentShapeAssembler.SUBWORDS_ID)
          .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
          .setSubwordValues(list.build())
          .build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE,
        "Encoded " + list.getAnnotationsCount() + " subword piece(s) with model '"
            + modelId + "'"));
  }

  /**
   * Tokenizes each sentence by Viterbi lattice segmentation over the selected
   * MeCab-format dictionary (AnalysisProfile.tokenizer_engine = "lattice"). No
   * probabilities: the segmentation is cost-driven, not statistical.
   */
  void tokenizeLattice(
      String rawText,
      OpenNlpDocument.Builder document,
      String dictionaryId,
      List<ProcessingDiagnostic> diagnostics) {
    final LatticeTokenizer tokenizer = modelBundleCache.getLatticeRegistry().get(dictionaryId);
    tokenize(rawText, document, tokenizer,
        "lattice, dictionary '" + dictionaryId + "'", diagnostics);
  }

  /**
   * Runs PIPELINE_STEP_NORMALIZE: applies the requested normalizers in the library's canonical
   * order and records the normalized text in {@code OpenNlpDocument.normalization}. When
   * every normalizer is offset-aware the result carries the full alignment (in UTF-16 units at
   * this stage; the offset-encoding pass rescales); otherwise, which the validator only
   * permits with {@code require_alignment = false}, the alignment is omitted and a
   * diagnostic says so.
   */
  static void normalize(
      String rawText,
      NormalizationSpec spec,
      OpenNlpDocument.Builder document,
      List<ProcessingDiagnostic> diagnostics) {
    final List<Normalizer> ordered = Normalizers.canonicalOrder(spec.getNormalizersList());
    final TextNormalizer.Builder builder = TextNormalizer.builder();
    for (final Normalizer normalizer : ordered) {
      Normalizers.apply(builder, normalizer);
    }
    final NormalizationResult.Builder result = NormalizationResult.newBuilder();
    for (final Normalizer normalizer : ordered) {
      result.addAppliedNormalizers(normalizer.name());
    }
    if (Normalizers.allOffsetAware(ordered)) {
      final OffsetAwareNormalizer aligned = builder.buildAligned();
      final AlignedText alignedText = aligned.normalizeAligned(rawText);
      result.setNormalizedText(alignedText.normalizedString());
      result.addAllAlignment(AlignmentRuns.from(alignedText));
    } else {
      result.setNormalizedText(builder.build().normalize(rawText).toString());
      diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_NORMALIZE,
          "Offset-opaque normalizer(s) requested; normalized_text carries no alignment"));
    }
    document.setNormalization(result.build());
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_NORMALIZE,
        "Applied " + ordered.size() + " normalizer(s)"));
  }

  /**
   * Computes per-token normalization layers ({@code Token.term_layers}) for the
   * character-level {@link Dimension}s named in AnalysisProfile.term_dimensions,
   * using the library's cumulative term-layer semantics. The validator has already
   * checked the names.
   */
  static void computeTermLayers(
      OpenNlpDocument.Builder document,
      List<String> dimensionNames,
      List<ProcessingDiagnostic> diagnostics) {
    final List<Dimension> dimensions = new ArrayList<>(dimensionNames.size());
    for (final String name : dimensionNames) {
      dimensions.add(Dimension.valueOf(name));
    }
    final TermAnalyzer.Builder builder = TermAnalyzer.builder();
    for (final Dimension dimension : dimensions) {
      builder.transform(dimension, dimension.defaultNormalizer());
    }
    final TermAnalyzer analyzer = builder.build();
    int tokenCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final String[] tokens = new String[sentence.getTokensCount()];
      for (int t = 0; t < tokens.length; t++) {
        tokens[t] = sentence.getTokens(t).getText();
      }
      final List<Term> terms = analyzer.analyze(tokens, new String[tokens.length]);
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < tokens.length; t++) {
        final Token.Builder token = sentenceBuilder.getTokens(t).toBuilder();
        for (final Dimension dimension : dimensions) {
          putTermLayer(token, dimension.name(), terms.get(t).at(dimension));
        }
        sentenceBuilder.setTokens(t, token.build());
        tokenCount++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Computed " + dimensionNames.size() + " term layer(s) for " + tokenCount + " token(s)"));
  }

  /**
   * Marks Token.is_stopword against the bundled stopword list for the requested
   * language (AnalysisProfile.stopword_language). Annotation only: no token is
   * removed. The validator has already confirmed the language has a bundled list.
   */
  static void markStopwords(
      OpenNlpDocument.Builder document,
      String language,
      List<ProcessingDiagnostic> diagnostics) {
    final opennlp.tools.stopword.StopwordFilter filter =
        opennlp.tools.stopword.StopwordLists.forLanguage(language);
    int marked = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence.Builder sentenceBuilder = document.getSentences(i).toBuilder();
      for (int t = 0; t < sentenceBuilder.getTokensCount(); t++) {
        if (filter.isStopword(sentenceBuilder.getTokens(t).getText())) {
          sentenceBuilder.setTokens(t, sentenceBuilder.getTokens(t).toBuilder()
              .setIsStopword(true).build());
          marked++;
        }
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Marked " + marked + " stopword token(s) for language '" + language + "'"));
  }

  /**
   * Computes Token.term_layers with a per-language {@link NormalizationProfile} matching
   * analyzer (AnalysisProfile.term_profile). The analyzer is built per request: the
   * profile's stemmer layer is stateful (Snowball), so instances must not be shared
   * across threads. The validator has already confirmed the profile exists.
   */
  static void computeProfileTermLayers(
      OpenNlpDocument.Builder document,
      String language,
      List<ProcessingDiagnostic> diagnostics) {
    final NormalizationProfile profile = NormalizationProfiles.forLanguage(language)
        .orElseThrow(() -> AnalysisException.notFound(
            "No normalization profile registered for language '" + language + "'"));
    final TermAnalyzer analyzer = profile.matchingAnalyzer();
    final List<Dimension> dimensions = analyzer.dimensions();
    int tokenCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final String[] tokens = new String[sentence.getTokensCount()];
      for (int t = 0; t < tokens.length; t++) {
        tokens[t] = sentence.getTokens(t).getText();
      }
      final List<Term> terms = analyzer.analyze(tokens, new String[tokens.length]);
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < tokens.length; t++) {
        final Token.Builder token = sentenceBuilder.getTokens(t).toBuilder();
        for (final Dimension dimension : dimensions) {
          putTermLayer(token, dimension.name(), terms.get(t).at(dimension));
        }
        sentenceBuilder.setTokens(t, token.build());
        tokenCount++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Computed profile '" + language + "' term layers (" + dimensions.size()
            + " dimension(s)) for " + tokenCount + " token(s)"));
  }

  /**
   * Produces caller-qualified term layers from explicit typed normalization and
   * stemming pipelines. Each layer is computed per token, so its value retains the
   * token's original document span even when normalization expands or removes code
   * points. A token whose final value is empty is omitted from that term layer.
   */
  void computeConfiguredTermLayers(
      OpenNlpDocument.Builder document,
      List<TermLayerSpec> specs,
      List<ProcessingDiagnostic> diagnostics) {
    for (final TermLayerSpec spec : specs) {
      final TextNormalizer.Builder normalizerBuilder = TextNormalizer.builder();
      for (final Normalizer normalizer
          : Normalizers.canonicalOrder(spec.getNormalizersList())) {
        Normalizers.apply(normalizerBuilder, normalizer);
      }
      final var normalizer = normalizerBuilder.build();
      final UnaryOperator<String> stem = spec.hasStemmer()
          ? StemmerSelector.newRawStemFunction(
              spec.getStemmer(), modelBundleCache.getHunspellRegistry())
          : UnaryOperator.identity();
      int tokenCount = 0;
      for (int i = 0; i < document.getSentencesCount(); i++) {
        final AnnotatedSentence.Builder sentence = document.getSentences(i).toBuilder();
        for (int t = 0; t < sentence.getTokensCount(); t++) {
          final Token.Builder token = sentence.getTokens(t).toBuilder();
          final String normalized = normalizer.normalize(token.getText()).toString();
          putTermLayer(token, spec.getQualifier(), stem.apply(normalized));
          sentence.setTokens(t, token.build());
          tokenCount++;
        }
        document.setSentences(i, sentence.build());
      }
      diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
          "Computed configured term layer '" + spec.getQualifier() + "' for "
              + tokenCount + " token(s)"));
    }
  }

  /** Adds term layer. */
  private static void putTermLayer(Token.Builder token, String qualifier, String value) {
    if (!value.isEmpty()) {
      token.putTermLayers(qualifier, value);
    }
  }

  /**
   * Recognizes named entities per the request's engine policy, attaches each entity's provenance
   * ({@code sources}) and matched text, and writes them onto their sentences. A recognizer served
   * by several engines is resolved by priority (with fallback), pinned to one engine, or unioned
   * across engines depending on how many engines the policy lists; see {@link NerEntityResolver}.
   */
  void findNamedEntities(
      OpenNlpDocument.Builder document,
      List<String> entityTypes,
      EnginePolicy enginePolicy,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final NameFinderRegistry registry = modelBundleCache.getNameFinderRegistry();
    final Set<String> requested = new HashSet<>();
    for (String entityType : entityTypes) {
      requested.add(NameFinderRegistry.normalize(entityType));
    }
    final List<String> recognizerIds = registry.recognizerIdsForTypes(entityTypes);
    final List<String> selections = EngineSelections.ids(enginePolicy);
    final List<String> engines = new ArrayList<>(selections.size());
    for (String engine : selections) {
      engines.add(NameFinderRegistry.normalize(engine));
    }
    final NerEntityResolver resolver = new NerEntityResolver(
        registry.recognizers(), recognizerIds, engines, enginePolicy.getMerge(),
        requested, document.getRawText(), includeProbabilities);

    int entityCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final List<NamedEntity> entities = resolver.resolve(sentence);
      if (entities.isEmpty()) {
        continue;
      }
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (NamedEntity entity : entities) {
        sentenceBuilder.addEntities(entity);
      }
      document.setSentences(i, sentenceBuilder.build());
      entityCount += entities.size();
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_NER,
        "Detected " + entityCount + " entit"
            + (entityCount == 1 ? "y" : "ies")
            + " across " + recognizerIds.size() + " recognizer(s)"));
  }

  /**
   * Tags every token of every sentence with its part of speech. Sentences are tagged
   * as a whole so the tagger sees full sentential context.
   */
  void tagPartsOfSpeech(
      ClassicLanguagePipeline pipeline,
      OpenNlpDocument.Builder document,
      POSTagFormat posTagFormat,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final POSTaggerME posTagger = pipeline.createPosTagger(posTagFormat);
    final RecordingPOSTagger recording = includeProbabilities
        ? new RecordingPOSTagger(posTagger) : null;
    final POSTagger delegate = recording == null ? posTagger : recording;
    final Document annotated = new POSTaggerAnnotator(delegate)
        .annotate(documentMapper.withTokens(document.getRawText(), document));
    final List<Annotation<String>> tags = annotated.get(Layers.POS_TAGS);
    final RecordedProbabilities probabilities = recording == null
        ? null : recording.probabilities();
    int tagIndex = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < sentence.getTokensCount(); t++) {
        final Token.Builder token = sentenceBuilder.getTokens(t).toBuilder()
            .setPosTag(tags.get(tagIndex).value());
        if (probabilities != null && probabilities.has(tagIndex)) {
          token.setPosProbability((float) probabilities.get(tagIndex));
        }
        sentenceBuilder.setTokens(t, token.build());
        tagIndex++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_POS_TAG,
        "POS-tagged " + tagIndex + " token(s)"));
  }

  /**
   * Assigns a lemma to every token using the configured lemmatizer (statistical or
   * dictionary-backed), which requires the POS tags produced by
   * {@link PipelineStep#PIPELINE_STEP_POS_TAG}. When that step converted tags to a requested
   * output tagset, the lemmatizer (keyed on the tagger's native tagset) is fed native tags
   * re-derived from the same model instead of the converted token tags.
   */
  void lemmatize(ClassicLanguagePipeline pipeline, OpenNlpDocument.Builder document,
      POSTagFormat posTagFormat, List<ProcessingDiagnostic> diagnostics) {
    final Lemmatizer lemmatizer = pipeline.lemmatizer();
    final POSTaggerME nativeTagger = pipeline.convertsPosTagFormat(posTagFormat)
        ? pipeline.createPosTagger(POSTagFormat.POS_TAG_FORMAT_UNSPECIFIED)
        : null;
    Document annotated = nativeTagger == null
        ? documentMapper.withPosTags(document.getRawText(), document)
        : new POSTaggerAnnotator(nativeTagger)
            .annotate(documentMapper.withTokens(document.getRawText(), document));
    annotated = new LemmatizerAnnotator(lemmatizer).annotate(annotated);
    final List<Annotation<String>> lemmas = annotated.get(LemmatizerAnnotator.LEMMAS);
    int lemmaIndex = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < sentence.getTokensCount(); t++) {
        sentenceBuilder.setTokens(t,
            sentenceBuilder.getTokens(t).toBuilder()
                .setLemma(lemmas.get(lemmaIndex).value()).build());
        lemmaIndex++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_LEMMATIZE,
        "Lemmatized " + lemmaIndex + " token(s)"));
  }

  /**
   * Classifies the whole document with the selected {@link DocCategorizerModel} and records the
   * result as {@link OpenNlpDocument#getClassification()}. The model receives both the raw text
   * and the document's tokens so classic (token-based) and transformer (text-based) categorizers
   * are both served from the one call.
   */
  void categorizeDocument(
      String rawText,
      OpenNlpDocument.Builder document,
      String modelId,
      List<ProcessingDiagnostic> diagnostics) {
    final DocCategorizerRegistry registry = modelBundleCache.getDocCategorizerRegistry();
    final DocCategorizerModel model = registry.get(modelId);
    if (model == null) {
      // The validator resolves and checks the id up front, so a null here is a server-side bug.
      throw AnalysisException.internal(
          "Document categorizer '" + modelId + "' is not registered", null);
    }
    final String[] tokens = documentTokens(document);
    final DocumentClassification classification = model.classify(rawText, tokens);
    document.setClassification(classification);
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE,
        "Classified document as '" + classification.getBestCategory() + "' using model '"
            + modelId + "' (" + classification.getCategoryScoresCount() + " categor"
            + (classification.getCategoryScoresCount() == 1 ? "y" : "ies") + ")"));
  }

  /**
   * Classifies every sentence with the selected sentiment model and records the winning label and
   * its score as {@link AnnotatedSentence#getSentimentLabel()} /
   * {@link AnnotatedSentence#getSentimentConfidence()}. Sentiment is document categorization
   * applied per sentence, so each sentence is handed both its own text and its tokens, letting
   * classic (token-based) and transformer (text-based) models be served from the one call.
   */
  void analyzeSentiment(
      String rawText,
      OpenNlpDocument.Builder document,
      String modelId,
      List<ProcessingDiagnostic> diagnostics) {
    final SentimentRegistry registry = modelBundleCache.getSentimentRegistry();
    final DocCategorizerModel model = registry.get(modelId);
    if (model == null) {
      // The validator resolves and checks the id up front, so a null here is a server-side bug.
      throw AnalysisException.internal("Sentiment model '" + modelId + "' is not registered", null);
    }
    // One batched call for the whole document: a transformer backend scores thousands of
    // sentences in a few inference calls instead of one call per sentence.
    final int sentenceCount = document.getSentencesCount();
    final List<String> sentenceTexts = new ArrayList<>(sentenceCount);
    final List<String[]> sentenceTokens = new ArrayList<>(sentenceCount);
    for (int i = 0; i < sentenceCount; i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotationSpan span = sentence.getSentenceSpan();
      sentenceTexts.add(rawText.substring(span.getStart(), span.getEnd()));
      sentenceTokens.add(tokenTexts(sentence));
    }
    final List<DocumentClassification> classifications =
        model.classifyBatch(sentenceTexts, sentenceTokens);
    if (classifications == null || classifications.size() != sentenceCount) {
      throw AnalysisException.internal("Sentiment model '" + modelId + "' returned "
          + (classifications == null ? "no" : classifications.size())
          + " classification(s) for " + sentenceCount + " sentence(s)", null);
    }
    int classifiedSentences = 0;
    for (int i = 0; i < sentenceCount; i++) {
      final DocumentClassification classification = classifications.get(i);
      final String label = classification.getBestCategory();
      document.setSentences(i, document.getSentences(i).toBuilder()
          .setSentimentLabel(label)
          .setSentimentConfidence(
              (float) classification.getCategoryScoresOrDefault(label, 0.0d))
          .build());
      classifiedSentences++;
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SENTIMENT,
        "Scored sentiment for " + classifiedSentences + " sentence(s) using model '"
            + modelId + "'"));
  }

  /**
   * Builds constituency parses per the request's engine policy and stores them on each sentence:
   * the primary parse on the sentence's {@code parse_tree}, and, when a union across engines
   * produced more than one, the full list on {@code parse_trees}, each tagged with its producer. A
   * parser served by several engines is resolved by priority (with fallback), pinned, or unioned
   * depending on how many engines the policy lists; see {@link ParseResolver}.
   */
  void parse(
      OpenNlpDocument.Builder document,
      Set<ParseFormat> formats,
      EnginePolicy enginePolicy,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final ParserRegistry registry = modelBundleCache.getParserRegistry();
    final List<String> parserIds = registry.parserIds();
    final List<String> selections = EngineSelections.ids(enginePolicy);
    final List<String> engines = new ArrayList<>(selections.size());
    for (String engine : selections) {
      engines.add(ParserRegistry.normalize(engine));
    }
    final ParseResolver resolver = new ParseResolver(registry.parsers(), parserIds, engines,
        formats.contains(ParseFormat.PARSE_FORMAT_STRUCTURED),
        formats.contains(ParseFormat.PARSE_FORMAT_BRACKETED), includeProbabilities);

    int parsedSentences = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final List<ParseTree> trees = resolver.resolve(sentence);
      if (trees.isEmpty()) {
        continue;
      }
      // The first tree is the primary; expose the full list only when a union produced several.
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder().setParseTree(trees.get(0));
      if (trees.size() > 1) {
        sentenceBuilder.addAllParseTrees(trees);
      }
      document.setSentences(i, sentenceBuilder.build());
      parsedSentences++;
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_PARSE,
        "Parsed " + parsedSentences + " sentence(s) across " + parserIds.size() + " parser(s)"));
  }

  /**
   * Groups each sentence's tokens into base phrases (NP, VP, ...) per the request's engine policy
   * and stores them in {@link AnnotatedSentence#getSyntacticChunks()}, attaching each chunk's
   * provenance and matched text. A chunker served by several engines is resolved by priority (with
   * fallback), pinned, or unioned depending on how many engines the policy lists; see
   * {@link ChunkResolver}. Runs after {@link PipelineStep#PIPELINE_STEP_POS_TAG}.
   */
  void chunkSyntactic(OpenNlpDocument.Builder document, EnginePolicy enginePolicy,
      List<ProcessingDiagnostic> diagnostics) {
    final ChunkerRegistry registry = modelBundleCache.getChunkerRegistry();
    final List<String> chunkerIds = registry.chunkerIds();
    final List<String> selections = EngineSelections.ids(enginePolicy);
    final List<String> engines = new ArrayList<>(selections.size());
    for (String engine : selections) {
      engines.add(ChunkerRegistry.normalize(engine));
    }
    final ChunkResolver resolver = new ChunkResolver(
        registry.chunkers(), chunkerIds, engines, enginePolicy.getMerge(), document.getRawText());

    int chunkCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final ChunkResult result = resolver.resolve(sentence);
      chunkCount += result.getChunksCount();
      document.setSentences(i, sentence.toBuilder().setSyntacticChunks(result).build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK,
        "Found " + chunkCount + " syntactic chunk(s) across " + chunkerIds.size() + " chunker(s)"));
  }

  /** Records the probabilities produced by each tokenizer call. */
  private final class RecordingTokenizer implements Tokenizer {

    private final TokenizerME delegate;
    private final RecordedProbabilities probabilities = new RecordedProbabilities();

    /** Creates a recording wrapper over a model-backed tokenizer. */
    private RecordingTokenizer(TokenizerME delegate) {
      this.delegate = delegate;
    }

    /** {@inheritDoc} */
    @Override
    public String[] tokenize(String text) {
      return delegate.tokenize(text);
    }

    /** {@inheritDoc} */
    @Override
    public Span[] tokenizePos(String text) {
      final Span[] spans = delegate.tokenizePos(text);
      probabilities.append(delegate.probs(), spans.length);
      return spans;
    }

    /** Returns the probabilities in flat token order. */
    private RecordedProbabilities probabilities() {
      return probabilities;
    }
  }

  /** Records the probabilities produced by each part-of-speech tagging call. */
  private final class RecordingPOSTagger implements POSTagger {

    private final POSTaggerME delegate;
    private final RecordedProbabilities probabilities = new RecordedProbabilities();

    /** Creates a recording wrapper over a model-backed tagger. */
    private RecordingPOSTagger(POSTaggerME delegate) {
      this.delegate = delegate;
    }

    /** {@inheritDoc} */
    @Override
    public String[] tag(String[] sentence) {
      final String[] tags = delegate.tag(sentence);
      probabilities.append(delegate.probs(), tags.length);
      return tags;
    }

    /** {@inheritDoc} */
    @Override
    public String[] tag(String[] sentence, Object[] additionalContext) {
      final String[] tags = delegate.tag(sentence, additionalContext);
      probabilities.append(delegate.probs(), tags.length);
      return tags;
    }

    /** {@inheritDoc} */
    @Override
    public Sequence[] topKSequences(String[] sentence) {
      return delegate.topKSequences(sentence);
    }

    /** {@inheritDoc} */
    @Override
    public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
      return delegate.topKSequences(sentence, additionalContext);
    }

    /** Returns the probabilities in flat token order. */
    private RecordedProbabilities probabilities() {
      return probabilities;
    }
  }

  /** Stores probability values without boxing them on the annotation hot path. */
  private final class RecordedProbabilities {

    private double[] values = new double[16];
    private int size;

    /** Appends one slot for each produced annotation. */
    private void append(double[] produced, int expectedSize) {
      ensureCapacity(size + expectedSize);
      Arrays.fill(values, size, size + expectedSize, Double.NaN);
      if (produced != null) {
        System.arraycopy(produced, 0, values, size, Math.min(produced.length, expectedSize));
      }
      size += expectedSize;
    }

    /** Returns whether the requested slot carries a probability. */
    private boolean has(int index) {
      return index < size && !Double.isNaN(values[index]);
    }

    /** Returns one recorded probability. */
    private double get(int index) {
      return values[index];
    }

    /** Grows the primitive backing array when required. */
    private void ensureCapacity(int required) {
      if (required > values.length) {
        values = Arrays.copyOf(values, Math.max(required, values.length * 2));
      }
    }
  }

  /** Returns sentence token text in order. */
  private static String[] tokenTexts(AnnotatedSentence sentence) {
    final String[] tokens = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
    }
    return tokens;
  }

  /** Flattens every sentence's tokens into one document-order array for whole-document tasks. */
  private static String[] documentTokens(OpenNlpDocument.Builder document) {
    final List<String> tokens = new ArrayList<>();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        tokens.add(token.getText());
      }
    }
    return tokens.toArray(new String[0]);
  }
}
