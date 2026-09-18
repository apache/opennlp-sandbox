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
package org.apache.opennlp.grpc.model;

import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormat;
import opennlp.tools.postag.POSTagFormatMapper;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.TokenizerME;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * One language's classic pipeline models: sentence detector, tokenizer, POS tagger, and
 * lemmatizer, loaded once and shared across requests. The default pipeline serves every
 * request that no configured {@code model.pipeline.<lang>} set matches.
 *
 * <p>The {@code *ME} decoders keep caller-specific result state internally and release
 * it through {@link #clearThreadLocalState()} after each document.</p>
 */
public final class ClassicLanguagePipeline {

  private final String language;
  private final SentenceDetectorME sentenceDetector;
  private final TokenizerME tokenizer;
  private final POSModel posModel;
  private final POSTaggerME posTagger;
  private final Lemmatizer lemmatizer;
  // Set only when the lemmatizer is the statistical decoder; a dictionary
  // lemmatizer holds no caller-specific state.
  private final LemmatizerME statisticalLemmatizer;

  /**
   * Creates one loaded pipeline.
   *
   * @param language The configured pipeline language, or an empty string for the default
   *     pipeline. Must not be {@code null}.
   * @param sentenceDetector The shared sentence detector. Must not be {@code null}.
   * @param tokenizer The shared tokenizer. Must not be {@code null}.
   * @param posModel The POS model backing per-format tagger creation. Must not be
   *     {@code null}.
   * @param posTagger The shared native-format POS tagger. Must not be {@code null}.
   * @param lemmatizer The lemmatizer to serve. Must not be {@code null}.
   * @param statisticalLemmatizer The statistical decoder when it backs
   *     {@code lemmatizer}, or {@code null} for a dictionary lemmatizer.
   *
   * @throws IllegalArgumentException If a required component is {@code null}.
   */
  ClassicLanguagePipeline(String language, SentenceDetectorME sentenceDetector,
      TokenizerME tokenizer, POSModel posModel, POSTaggerME posTagger,
      Lemmatizer lemmatizer, LemmatizerME statisticalLemmatizer) {
    if (language == null) {
      throw new IllegalArgumentException("language must not be null");
    }
    if (sentenceDetector == null) {
      throw new IllegalArgumentException("sentenceDetector must not be null");
    }
    if (tokenizer == null) {
      throw new IllegalArgumentException("tokenizer must not be null");
    }
    if (posModel == null) {
      throw new IllegalArgumentException("posModel must not be null");
    }
    if (posTagger == null) {
      throw new IllegalArgumentException("posTagger must not be null");
    }
    if (lemmatizer == null) {
      throw new IllegalArgumentException("lemmatizer must not be null");
    }
    this.language = language;
    this.sentenceDetector = sentenceDetector;
    this.tokenizer = tokenizer;
    this.posModel = posModel;
    this.posTagger = posTagger;
    this.lemmatizer = lemmatizer;
    this.statisticalLemmatizer = statisticalLemmatizer;
  }

  /**
   * Returns the configured pipeline language.
   *
   * @return The language code, or an empty string for the default pipeline. Never
   *     {@code null}.
   */
  public String language() {
    return language;
  }

  /**
   * Returns the shared sentence detector.
   *
   * @return The sentence detector. Never {@code null}.
   */
  public SentenceDetectorME sentenceDetector() {
    return sentenceDetector;
  }

  /**
   * Returns the shared tokenizer.
   *
   * @return The tokenizer. Never {@code null}.
   */
  public TokenizerME tokenizer() {
    return tokenizer;
  }

  /**
   * Returns the shared POS tagger in the model's native tag format.
   *
   * @return The POS tagger. Never {@code null}.
   */
  public POSTaggerME posTagger() {
    return posTagger;
  }

  /**
   * Returns the shared lemmatizer.
   *
   * @return The lemmatizer. Never {@code null}.
   */
  public Lemmatizer lemmatizer() {
    return lemmatizer;
  }

  /**
   * Creates a tagger over this pipeline's POS model for the requested output format.
   *
   * @param requestedFormat The client-requested tagset.
   *
   * @return A tagger configured for {@code requestedFormat}. Never {@code null}.
   *
   * @throws AnalysisException If {@code requestedFormat} is {@code CUSTOM}.
   */
  public POSTaggerME createPosTagger(org.apache.opennlp.grpc.v1.POSTagFormat requestedFormat) {
    if (requestedFormat == org.apache.opennlp.grpc.v1.POSTagFormat.POS_TAG_FORMAT_CUSTOM) {
      throw AnalysisException.unimplemented(
          "pos_tag_format CUSTOM requires a client-supplied tag mapping; not supported");
    }
    final POSTagFormat outputFormat = switch (requestedFormat) {
      case POS_TAG_FORMAT_UD -> POSTagFormat.UD;
      case POS_TAG_FORMAT_PENN -> POSTagFormat.PENN;
      default -> POSTagFormatMapper.guessFormat(posModel);
    };
    return new POSTaggerME(posModel, outputFormat);
  }

  /**
   * Reports whether tagging with the requested output format rewrites the model's native
   * tags. When it does, consumers keyed on the native tagset (the lemmatizer) must not be
   * fed the converted {@code Token.pos_tag} values.
   *
   * @param requestedFormat The client-requested tagset.
   *
   * @return {@code true} when {@link #createPosTagger} with {@code requestedFormat}
   *     converts tags.
   */
  public boolean convertsPosTagFormat(org.apache.opennlp.grpc.v1.POSTagFormat requestedFormat) {
    final POSTagFormat nativeFormat = POSTagFormatMapper.guessFormat(posModel);
    return switch (requestedFormat) {
      case POS_TAG_FORMAT_UD -> nativeFormat != POSTagFormat.UD;
      case POS_TAG_FORMAT_PENN -> nativeFormat != POSTagFormat.PENN;
      default -> false;
    };
  }

  /** Releases caller-specific decoder state after one document finishes on this thread. */
  public void clearThreadLocalState() {
    sentenceDetector.clearThreadLocalState();
    tokenizer.clearThreadLocalState();
    posTagger.clearThreadLocalState();
    if (statisticalLemmatizer != null) {
      statisticalLemmatizer.clearThreadLocalState();
    }
  }
}
