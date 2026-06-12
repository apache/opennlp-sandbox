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

import java.util.List;
import java.util.Objects;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.Token;

/**
 * Executes the classic OpenNLP annotation steps (language detection, sentence
 * detection, tokenization, POS tagging, lemmatization) against the shared models in
 * the {@link ModelBundleCache}, writing results into the document builder.
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

  private static String[] tokenTexts(AnnotatedSentence sentence) {
    final String[] tokens = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
    }
    return tokens;
  }
}
