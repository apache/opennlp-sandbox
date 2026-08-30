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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the "lattice" tokenizer engine: Viterbi segmentation over a
 * project-authored miniature MeCab-format dictionary written by the test, with the
 * segmented tokens flowing into the normal token results and the document shape.
 */
class BasicDocumentAnalyzerLatticeTest {

  /** Tokyo-Metropolis-ni-iku: segments as Tokyo | Metropolis | ni | iku. */
  private static final String TEXT = "東京都に行く";

  @TempDir
  static Path dictionaryDir;

  @BeforeAll
  static void writeMiniatureDictionary() throws IOException {
    Files.writeString(dictionaryDir.resolve("lexicon.csv"), String.join("\n",
        "東京,0,0,3000,noun,proper",
        "京都,0,0,3000,noun,proper",
        "東,0,0,6000,noun,common",
        "都,0,0,4000,noun,suffix",
        "に,0,0,1000,particle,case",
        "行く,0,0,3000,verb,base",
        ""));
    Files.writeString(dictionaryDir.resolve("matrix.def"), "1 1\n0 0 0\n");
    Files.writeString(dictionaryDir.resolve("char.def"), String.join("\n",
        "DEFAULT 0 1 0",
        "KANJI 0 0 2",
        "HIRAGANA 0 1 0",
        "",
        "0x3041..0x3096 HIRAGANA",
        "0x4E00..0x9FFF KANJI",
        ""));
    Files.writeString(dictionaryDir.resolve("unk.def"), String.join("\n",
        "DEFAULT,0,0,10000,symbol,unknown",
        "KANJI,0,0,8000,noun,unknown",
        "HIRAGANA,0,0,9000,particle,unknown",
        ""));
  }

  private static BasicDocumentAnalyzer analyzerWithDictionary() {
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), new ModelBundleCache(
        Map.of("model.lattice.mini.dir", dictionaryDir.toString())));
  }

  private static AnalyzeDocumentRequest request(String dictionaryId) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .setProfileId("lattice")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setTokenizerEngine("lattice");
    if (dictionaryId != null) {
      profile.setLatticeDictionaryId(dictionaryId);
    }
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile.build())
        // Pin UTF-16 offsets so the span assertions below are in Java char indices
        // (the wire default is UTF-8 bytes, where each kanji is three units).
        .setOptions(org.apache.opennlp.grpc.v1.AnalysisOptions.newBuilder()
            .setOffsetEncoding(org.apache.opennlp.grpc.v1.OffsetEncoding
                .OFFSET_ENCODING_UTF16_CODE_UNIT)
            .build())
        .build();
  }

  @Test
  void latticeEngineSegmentsAgainstTheDictionary() {
    final AnalyzeDocumentResponse response = analyzerWithDictionary().analyze(request(null));

    assertEquals(1, response.getDocument().getSentencesCount());
    final AnnotatedSentence sentence = response.getDocument().getSentences(0);
    assertEquals(4, sentence.getTokensCount());
    assertEquals(List.of("東京", "都", "に", "行く"),
        sentence.getTokensList().stream().map(t -> t.getText()).toList());
    assertEquals(0, sentence.getTokens(0).getAnnotationSpan().getStart());
    assertEquals(2, sentence.getTokens(0).getAnnotationSpan().getEnd());
    assertEquals(4, sentence.getTokens(3).getAnnotationSpan().getStart());
    assertEquals(6, sentence.getTokens(3).getAnnotationSpan().getEnd());

    // The segmented tokens are also the document-shape token layer.
    final var tokensLayer = response.getDocument().getLayers().getLayersList().stream()
        .filter(l -> "opennlp:tokens".equals(l.getId()))
        .findFirst().orElseThrow();
    assertEquals(4, tokensLayer.getStringValues().getAnnotationsCount());
    assertEquals("東京", tokensLayer.getStringValues().getAnnotations(0).getValue());
  }

  @Test
  void latticeWithoutConfiguredDictionaryFails() {
    final BasicDocumentAnalyzer bare = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()));
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> bare.analyze(request(null)));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void unknownLatticeDictionaryIdFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzerWithDictionary().analyze(request("missing")));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
