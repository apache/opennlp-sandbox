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
import java.util.Map;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.NormalizationRung;
import org.apache.opennlp.grpc.v1.NormalizationSpec;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for every {@link AnalysisRequestValidator} guard added with the library-parity
 * surfaces: the NORMALIZE spec/step pairing, rung consistency, alignment requirements,
 * tokenizer_engine values, term_dimensions prerequisites and name checks, and the
 * term_profile exclusivity and registry lookup. The guards are exercised through
 * {@link BasicDocumentAnalyzer#analyze}, the public entry that runs the validator, so
 * each test also proves the failure reaches the caller with its precise failure type.
 */
class AnalysisRequestValidatorTest {

  private static final String TEXT = "The driver was injured. He was taken to the hospital!";

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

  private AnalyzeDocumentResponse analyze(AnalysisProfile.Builder profile) {
    return analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile.setProfileId("inline-test"))
        .build());
  }

  private AnalysisException assertRejected(
      AnalysisProfile.Builder profile,
      AnalysisException.FailureType expectedType,
      String expectedSnippet) {
    final AnalysisException error =
        assertThrows(AnalysisException.class, () -> analyze(profile));
    assertEquals(expectedType, error.getFailureType());
    assertTrue(error.getMessage().contains(expectedSnippet),
        "Expected message to contain '" + expectedSnippet + "' but was: " + error.getMessage());
    return error;
  }

  // ---------- NORMALIZE spec/step pairing ----------

  @Test
  void rejectsNormalizationSpecWithoutNormalizeStep() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_WHITESPACE)),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "requires PIPELINE_STEP_NORMALIZE");
  }

  @Test
  void rejectsNormalizeStepWithoutSpec() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "at least one rung");
  }

  @Test
  void rejectsNormalizeStepWithEmptySpec() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "at least one rung");
  }

  @Test
  void rejectsSpecWhoseOnlyRungIsUnspecified() {
    // The rung list is non-empty, so the presence check passes; the canonical-order pass
    // must still catch that nothing recognizable is left.
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_UNSPECIFIED)),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "no recognized rung");
  }

  // ---------- Rung consistency ----------

  @Test
  void rejectsWhitespaceAndPreserveLineBreaksTogether() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_WHITESPACE)
                .addRungs(NormalizationRung
                    .NORMALIZATION_RUNG_WHITESPACE_PRESERVE_LINE_BREAKS)),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "mutually exclusive rungs");
  }

  // ---------- Alignment requirement vs offset-opaque rungs ----------

  @Test
  void rejectsOffsetOpaqueRungsWhenAlignmentRequiredByDefault() {
    // require_alignment is unset: the validator must treat that as true.
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_NFC)),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "require_alignment");
  }

  @Test
  void rejectsOffsetOpaqueRungsWhenAlignmentRequiredExplicitly() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_CONFUSABLE_FOLD)
                .setRequireAlignment(true)),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "require_alignment");
  }

  @Test
  void acceptsOffsetOpaqueRungsWhenAlignmentWaived() {
    final AnalyzeDocumentResponse response = analyze(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_NFC)
                .setRequireAlignment(false)));
    assertFalse(response.getDocument().getNormalization().getNormalizedText().isEmpty());
    assertEquals(0, response.getDocument().getNormalization().getAlignmentCount());
  }

  @Test
  void acceptsOffsetAwareRungsWithAlignmentRequired() {
    final AnalyzeDocumentResponse response = analyze(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_NORMALIZE)
            .setNormalization(NormalizationSpec.newBuilder()
                .addRungs(NormalizationRung.NORMALIZATION_RUNG_WHITESPACE)
                .setRequireAlignment(true)));
    assertTrue(response.getDocument().getNormalization().getAlignmentCount() > 0);
  }

  // ---------- tokenizer_engine values ----------

  @Test
  void rejectsUnknownTokenizerEngine() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .setTokenizerEngine("bpe"),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "Unknown tokenizer_engine");
  }

  @Test
  void acceptsModelTokenizerEngine() {
    final AnalyzeDocumentResponse response = analyze(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .setTokenizerEngine("model"));
    assertFalse(response.getDocument().getSentences(0).getTokensList().isEmpty());
    // The statistical tokenizer classifies nothing, so word_type stays unset.
    assertFalse(response.getDocument().getSentences(0).getTokens(0).hasWordType());
  }

  @Test
  void acceptsUax29TokenizerEngine() {
    final AnalyzeDocumentResponse response = analyze(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .setTokenizerEngine("uax29"));
    assertFalse(response.getDocument().getSentences(0).getTokensList().isEmpty());
    assertEquals("ALPHANUMERIC",
        response.getDocument().getSentences(0).getTokens(0).getWordType());
  }

  // ---------- term_dimensions prerequisites and names ----------

  @Test
  void rejectsTermDimensionsWithoutTokenizeStep() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addTermDimensions("NFC"),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "term_dimensions requires PIPELINE_STEP_TOKENIZE");
  }

  @Test
  void rejectsUnknownTermDimension() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addTermDimensions("SPARKLE"),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "Unknown term dimension 'SPARKLE'");
  }

  @Test
  void rejectsTokenIdentityAndLemmaDimensions() {
    // ORIGINAL is the identity layer and STEM/LEMMA belong to PIPELINE_STEP_LEMMATIZE;
    // none of them is a character-level dimension a client may request here.
    for (final String dimension : List.of("ORIGINAL", "STEM", "LEMMA")) {
      assertRejected(
          AnalysisProfile.newBuilder()
              .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
              .addTermDimensions(dimension),
          AnalysisException.FailureType.INVALID_ARGUMENT,
          "not a character-level dimension");
    }
  }

  // ---------- term_profile exclusivity and registry lookup ----------

  @Test
  void rejectsTermProfileCombinedWithTermDimensions() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addTermDimensions("NFC")
            .setTermProfile("en"),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "mutually exclusive");
  }

  @Test
  void rejectsTermProfileWithoutTokenizeStep() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .setTermProfile("en"),
        AnalysisException.FailureType.INVALID_ARGUMENT,
        "term_profile requires PIPELINE_STEP_TOKENIZE");
  }

  @Test
  void rejectsUnknownTermProfileLanguage() {
    assertRejected(
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .setTermProfile("zz"),
        AnalysisException.FailureType.NOT_FOUND,
        "No normalization profile registered for language 'zz'");
  }
}
