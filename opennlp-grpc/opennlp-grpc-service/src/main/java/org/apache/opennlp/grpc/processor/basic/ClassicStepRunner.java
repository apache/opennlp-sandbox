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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.model.ChunkerRegistry;
import org.apache.opennlp.grpc.model.DocCategorizerModel;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.ParserRegistry;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DocumentClassification;
import org.apache.opennlp.grpc.v1.EnginePolicy;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseFormat;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
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

  ClassicStepRunner(ModelBundleCache modelBundleCache) {
    this.modelBundleCache = Objects.requireNonNull(modelBundleCache, "modelBundleCache");
  }

  /**
   * Predicts the dominant document language and records it as an ISO 639-3 code
   * (e.g. {@code "eng"}) together with the model confidence.
   */
  void detectLanguage(
      String rawText,
      OpenNlpDocument.Builder document,
      List<ProcessingDiagnostic> diagnostics) {
    final LanguageDetectorME detector = modelBundleCache.getLanguageDetector();
    final Language language = detector.predictLanguage(rawText);
    document.setDetectedLanguage(language.getLang());
    document.setLanguageConfidence((float) language.getConfidence());
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
        "Detected language '" + language.getLang() + "' (confidence "
            + language.getConfidence() + ")"));
  }

  void detectSentences(
      String rawText,
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final SentenceDetectorME detector = modelBundleCache.getSentenceDetector();
    final Span[] spans = detector.sentPosDetect(rawText);
    final double[] probabilities = includeProbabilities ? detector.probs() : null;
    for (int i = 0; i < spans.length; i++) {
      final AnnotationSpan.Builder span = AnnotationSpan.newBuilder()
          .setStart(spans[i].getStart())
          .setEnd(spans[i].getEnd())
          .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT);
      if (probabilities != null && i < probabilities.length) {
        span.setProbability(probabilities[i]);
      }
      document.addSentences(AnnotatedSentence.newBuilder().setSentenceSpan(span).build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        "Detected " + spans.length + " sentence(s)"));
  }

  void tokenize(
      String rawText,
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final TokenizerME tokenizer = modelBundleCache.getTokenizer();
    int tokenCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotationSpan sentenceSpan = sentence.getSentenceSpan();
      final String sentenceText = rawText.substring(sentenceSpan.getStart(), sentenceSpan.getEnd());
      final Span[] tokenSpans = tokenizer.tokenizePos(sentenceText);
      final double[] probabilities = includeProbabilities ? tokenizer.probs() : null;
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < tokenSpans.length; t++) {
        final Span tokenSpan = tokenSpans[t];
        final AnnotationSpan.Builder span = AnnotationSpan.newBuilder()
            .setStart(sentenceSpan.getStart() + tokenSpan.getStart())
            .setEnd(sentenceSpan.getStart() + tokenSpan.getEnd())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT);
        if (probabilities != null && t < probabilities.length) {
          span.setProbability(probabilities[t]);
        }
        sentenceBuilder.addTokens(Token.newBuilder()
            .setText(sentenceText.substring(tokenSpan.getStart(), tokenSpan.getEnd()))
            .setAnnotationSpan(span)
            .build());
        tokenCount++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TOKENIZE,
        "Tokenized " + tokenCount + " token(s)"));
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
    final List<String> engines = new ArrayList<>(enginePolicy.getEnginesCount());
    for (String engine : enginePolicy.getEnginesList()) {
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
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final POSTaggerME posTagger = modelBundleCache.getPosTagger();
    int taggedTokens = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final String[] tokens = tokenTexts(sentence);
      final String[] tags = posTagger.tag(tokens);
      final double[] probabilities = includeProbabilities ? posTagger.probs() : null;
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < tags.length; t++) {
        final Token.Builder token = sentenceBuilder.getTokens(t).toBuilder().setPosTag(tags[t]);
        if (probabilities != null && t < probabilities.length) {
          token.setPosProbability((float) probabilities[t]);
        }
        sentenceBuilder.setTokens(t, token.build());
      }
      document.setSentences(i, sentenceBuilder.build());
      taggedTokens += tags.length;
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_POS_TAG,
        "POS-tagged " + taggedTokens + " token(s)"));
  }

  /**
   * Assigns a lemma to every token using the statistical lemmatizer, which requires the
   * POS tags produced by {@link PipelineStep#PIPELINE_STEP_POS_TAG}.
   */
  void lemmatize(OpenNlpDocument.Builder document, List<ProcessingDiagnostic> diagnostics) {
    final LemmatizerME lemmatizer = modelBundleCache.getLemmatizer();
    int lemmatizedTokens = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final String[] tokens = tokenTexts(sentence);
      final String[] tags = new String[sentence.getTokensCount()];
      for (int t = 0; t < tags.length; t++) {
        tags[t] = sentence.getTokens(t).getPosTag();
      }
      final String[] lemmas = lemmatizer.lemmatize(tokens, tags);
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < lemmas.length; t++) {
        sentenceBuilder.setTokens(t,
            sentenceBuilder.getTokens(t).toBuilder().setLemma(lemmas[t]).build());
      }
      document.setSentences(i, sentenceBuilder.build());
      lemmatizedTokens += lemmas.length;
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_LEMMATIZE,
        "Lemmatized " + lemmatizedTokens + " token(s)"));
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
    int classifiedSentences = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotationSpan span = sentence.getSentenceSpan();
      final String sentenceText = rawText.substring(span.getStart(), span.getEnd());
      final DocumentClassification classification =
          model.classify(sentenceText, tokenTexts(sentence));
      final String label = classification.getBestCategory();
      document.setSentences(i, sentence.toBuilder()
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
   * the primary parse on the sentence's {@code parse_tree}, and — when a union across engines
   * produced more than one — the full list on {@code parse_trees}, each tagged with its producer. A
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
    final List<String> engines = new ArrayList<>(enginePolicy.getEnginesCount());
    for (String engine : enginePolicy.getEnginesList()) {
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
    final List<String> engines = new ArrayList<>(enginePolicy.getEnginesCount());
    for (String engine : enginePolicy.getEnginesList()) {
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
