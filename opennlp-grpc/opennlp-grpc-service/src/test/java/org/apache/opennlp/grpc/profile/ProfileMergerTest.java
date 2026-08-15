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
import org.apache.opennlp.grpc.v1.NormalizationRung;
import org.apache.opennlp.grpc.v1.NormalizationSpec;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileMergerTest {

  @Test
  void extendedFieldsOverrideNamedProfileValues() {
    final AnalysisProfile base = AnalysisProfile.newBuilder()
        .setNormalization(NormalizationSpec.newBuilder()
            .addRungs(NormalizationRung.NORMALIZATION_RUNG_WHITESPACE))
        .setTokenizerEngine("model")
        .addTermDimensions("NFC")
        .setTermProfile("en")
        .setStopwordLanguage("en")
        .setSubwordModelId("base-subwords")
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER))
        .setWordnetLexiconId("base-wordnet")
        .setLatticeDictionaryId("base-lattice")
        .build();
    final AnalysisProfile override = AnalysisProfile.newBuilder()
        .setNormalization(NormalizationSpec.newBuilder()
            .addRungs(NormalizationRung.NORMALIZATION_RUNG_QUOTES))
        .setTokenizerEngine("uax29")
        .addTermDimensions("CASE_FOLD")
        .setTermProfile("de")
        .setStopwordLanguage("de")
        .setSubwordModelId("override-subwords")
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL))
        .setWordnetLexiconId("override-wordnet")
        .setLatticeDictionaryId("override-lattice")
        .build();

    final AnalysisProfile merged = ProfileMerger.merge(base, override);

    assertEquals(NormalizationRung.NORMALIZATION_RUNG_QUOTES,
        merged.getNormalization().getRungs(0));
    assertEquals("uax29", merged.getTokenizerEngine());
    assertEquals("CASE_FOLD", merged.getTermDimensions(0));
    assertEquals("de", merged.getTermProfile());
    assertEquals("de", merged.getStopwordLanguage());
    assertEquals("override-subwords", merged.getSubwordModelId());
    assertEquals(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL,
        merged.getStemmer().getAlgorithm());
    assertEquals("override-wordnet", merged.getWordnetLexiconId());
    assertEquals("override-lattice", merged.getLatticeDictionaryId());
  }
}
