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
import org.apache.opennlp.grpc.model.DocCategorizerModel;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.NerModel;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DocumentClassification;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
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
   * Runs every {@link NerModel} that can emit one of the requested entity types once per
   * sentence, keeps the entities whose type was requested, deduplicates identical spans,
   * and attaches them to the sentence. Running each model once (rather than once per type)
   * is what lets a single multi-type model serve several entity types.
   */
  void findNamedEntities(
      OpenNlpDocument.Builder document,
      List<String> entityTypes,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final NameFinderRegistry registry = modelBundleCache.getNameFinderRegistry();
    final Set<String> requested = new HashSet<>();
    for (String entityType : entityTypes) {
      requested.add(NameFinderRegistry.normalize(entityType));
    }
    final List<NerModel> models = registry.modelsForTypes(entityTypes);

    int entityCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      if (sentence.getTokensCount() == 0) {
        continue;
      }
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      final Set<String> seen = new HashSet<>();
      for (NerModel model : models) {
        for (NamedEntity entity : model.recognize(sentence, includeProbabilities)) {
          final String type = NameFinderRegistry.normalize(entity.getEntityType());
          if (!requested.contains(type)) {
            continue;
          }
          final AnnotationSpan span = entity.getAnnotationSpan();
          if (!seen.add(span.getStart() + ":" + span.getEnd() + ":" + type)) {
            continue;
          }
          sentenceBuilder.addEntities(entity);
          entityCount++;
        }
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_NER,
        "Detected " + entityCount + " entit"
            + (entityCount == 1 ? "y" : "ies")
            + " across " + models.size() + " model(s)"));
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
