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
package org.apache.opennlp.grpc.profile;

import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.Normalizer;
import org.apache.opennlp.grpc.v1.NormalizationSpec;
import org.apache.opennlp.grpc.v1.RelationPatternSpec;
import org.apache.opennlp.grpc.v1.SentenceDetectorSelector;
import org.apache.opennlp.grpc.v1.StandardSentenceDetectorEngine;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.apache.opennlp.grpc.v1.TermLayerSpec;
import org.apache.opennlp.grpc.v1.TokenizerSelector;
import org.apache.opennlp.grpc.v1.TermVectorMode;
import org.apache.opennlp.grpc.v1.TermVectorSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProfileMergerTest {

  @Test
  void typedTokenizerOverrideReplacesLegacyBaseSelector() {
    final AnalysisProfile base = AnalysisProfile.newBuilder()
        .setTokenizerEngine("model")
        .build();
    final AnalysisProfile override = AnalysisProfile.newBuilder()
        .setTokenizer(TokenizerSelector.newBuilder()
            .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE))
        .build();

    final AnalysisProfile merged = ProfileMerger.merge(base, override);

    assertFalse(merged.hasTokenizerEngine());
    assertEquals(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE,
        merged.getTokenizer().getStandard());
  }

  @Test
  void legacyTokenizerOverrideReplacesTypedBaseSelector() {
    final AnalysisProfile base = AnalysisProfile.newBuilder()
        .setTokenizer(TokenizerSelector.newBuilder()
            .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_MODEL))
        .build();
    final AnalysisProfile override = AnalysisProfile.newBuilder()
        .setTokenizerEngine("simple")
        .build();

    final AnalysisProfile merged = ProfileMerger.merge(base, override);

    assertEquals("simple", merged.getTokenizerEngine());
    assertFalse(merged.hasTokenizer());
  }

  @Test
  void extendedFieldsOverrideNamedProfileValues() {
    final AnalysisProfile base = AnalysisProfile.newBuilder()
        .setNormalization(NormalizationSpec.newBuilder()
            .addNormalizers(Normalizer.NORMALIZER_WHITESPACE))
        .setTokenizerEngine("model")
        .addTermDimensions("NFC")
        .setTermProfile("en")
        .setStopwordLanguage("en")
        .setSubwordModelId("base-subwords")
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER))
        .setWordnetLexiconId("base-wordnet")
        .setLatticeDictionaryId("base-lattice")
        .setTokenizer(TokenizerSelector.newBuilder()
            .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_MODEL))
        .setSentenceDetector(SentenceDetectorSelector.newBuilder()
            .setStandard(StandardSentenceDetectorEngine
                .STANDARD_SENTENCE_DETECTOR_ENGINE_MODEL))
        .setTermVector(TermVectorSpec.newBuilder()
            .setSourceLayer(LayerIdentity.newBuilder()
                .setStandard(StandardLayer.STANDARD_LAYER_TOKENS)))
        .addTermLayers(TermLayerSpec.newBuilder()
            .setQualifier("base")
            .addNormalizers(Normalizer.NORMALIZER_CASE_FOLD))
        .setDependencyParserId("base-dependency")
        .addRelationPatterns(RelationPatternSpec.newBuilder()
            .setType("base")
            .setPath("<nsubj >obj"))
        .build();
    final AnalysisProfile override = AnalysisProfile.newBuilder()
        .setNormalization(NormalizationSpec.newBuilder()
            .addNormalizers(Normalizer.NORMALIZER_QUOTES))
        .setTokenizerEngine("uax29")
        .addTermDimensions("CASE_FOLD")
        .setTermProfile("de")
        .setStopwordLanguage("de")
        .setSubwordModelId("override-subwords")
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL))
        .setWordnetLexiconId("override-wordnet")
        .setLatticeDictionaryId("override-lattice")
        .setTokenizer(TokenizerSelector.newBuilder()
            .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_SIMPLE))
        .setSentenceDetector(SentenceDetectorSelector.newBuilder()
            .setStandard(StandardSentenceDetectorEngine
                .STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE))
        .setTermVector(TermVectorSpec.newBuilder()
            .setMode(TermVectorMode.TERM_VECTOR_MODE_SCORING_ONLY)
            .setSourceLayer(LayerIdentity.newBuilder()
                .setStandard(StandardLayer.STANDARD_LAYER_STEMS)))
        .addTermLayers(TermLayerSpec.newBuilder()
            .setQualifier("override")
            .addNormalizers(Normalizer.NORMALIZER_FULL_CASE_FOLD))
        .setDependencyParserId("override-dependency")
        .addRelationPatterns(RelationPatternSpec.newBuilder()
            .setType("override")
            .setPath("<nsubj >obl"))
        .build();

    final AnalysisProfile merged = ProfileMerger.merge(base, override);

    assertEquals(Normalizer.NORMALIZER_QUOTES,
        merged.getNormalization().getNormalizers(0));
    assertEquals("uax29", merged.getTokenizerEngine());
    assertEquals("CASE_FOLD", merged.getTermDimensions(0));
    assertEquals("de", merged.getTermProfile());
    assertEquals("de", merged.getStopwordLanguage());
    assertEquals("override-subwords", merged.getSubwordModelId());
    assertEquals(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL,
        merged.getStemmer().getAlgorithm());
    assertEquals("override-wordnet", merged.getWordnetLexiconId());
    assertEquals("override-lattice", merged.getLatticeDictionaryId());
    assertEquals(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_SIMPLE,
        merged.getTokenizer().getStandard());
    assertEquals(StandardSentenceDetectorEngine.STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE,
        merged.getSentenceDetector().getStandard());
    assertEquals(TermVectorMode.TERM_VECTOR_MODE_SCORING_ONLY,
        merged.getTermVector().getMode());
    assertEquals(StandardLayer.STANDARD_LAYER_STEMS,
        merged.getTermVector().getSourceLayer().getStandard());
    assertEquals(1, merged.getTermLayersCount());
    assertEquals("override", merged.getTermLayers(0).getQualifier());
    assertEquals("override-dependency", merged.getDependencyParserId());
    assertEquals("override", merged.getRelationPatterns(0).getType());
  }
}
