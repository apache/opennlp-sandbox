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

import opennlp.tools.util.normalizer.TextNormalizer;
import org.apache.opennlp.grpc.v1.AlignmentRun;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.NormalizationRung;
import org.apache.opennlp.grpc.v1.NormalizationSpec;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the model-free parity steps: UAX #29 tokenization with word types, the
 * offset-aware NORMALIZE step with wire alignment runs, per-token term layers, and
 * the offset-encoding rescale of alignment runs.
 */
class ParityStepsTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static OpenNlpDocument.Builder documentWithSentence(String rawText) {
    return OpenNlpDocument.newBuilder()
        .setRawText(rawText)
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(AnnotationSpan.newBuilder()
                .setStart(0).setEnd(rawText.length())
                .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)));
  }

  // ---------- UAX #29 tokenizer engine ----------

  @Test
  void uax29TokenizerEmitsTypedTokensInDocumentCoordinates() {
    final String rawText = "great " + cp(0x1F642) + " 42";
    final OpenNlpDocument.Builder document = documentWithSentence(rawText);
    ClassicStepRunner.tokenizeUax29(rawText, document, new ArrayList<>());
    final List<Token> tokens = document.getSentences(0).getTokensList();
    assertEquals(3, tokens.size());
    assertEquals("great", tokens.get(0).getText());
    assertEquals("ALPHANUMERIC", tokens.get(0).getWordType());
    assertEquals(cp(0x1F642), tokens.get(1).getText());
    assertEquals("EMOJI", tokens.get(1).getWordType());
    assertEquals("42", tokens.get(2).getText());
    assertEquals("NUMERIC", tokens.get(2).getWordType());
    // Spans are document-coordinate: slicing raw_text reproduces each token.
    for (final Token token : tokens) {
      assertEquals(token.getText(),
          rawText.substring(token.getAnnotationSpan().getStart(),
              token.getAnnotationSpan().getEnd()));
    }
  }

  // ---------- NORMALIZE step ----------

  @Test
  void normalizeProducesAlignedTextAndRuns() {
    // Whitespace collapse (contracting) plus emoticon-to-emoji (contracting): spans mapped
    // through the returned runs must reproduce the library's own alignment behavior.
    final String rawText = "ok   :-)";
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder().setRawText(rawText);
    final NormalizationSpec spec = NormalizationSpec.newBuilder()
        .addRungs(NormalizationRung.NORMALIZATION_RUNG_EMOTICON_TO_EMOJI)
        .addRungs(NormalizationRung.NORMALIZATION_RUNG_WHITESPACE)
        .build();
    final List<ProcessingDiagnostic> diagnostics = new ArrayList<>();
    ClassicStepRunner.normalize(rawText, spec, document, diagnostics);
    assertEquals("ok " + cp(0x1F642), document.getNormalization().getNormalizedText());
    // Canonical order is enum order (WHITESPACE before EMOTICON_TO_EMOJI), not request order.
    assertEquals(List.of("NORMALIZATION_RUNG_WHITESPACE",
            "NORMALIZATION_RUNG_EMOTICON_TO_EMOJI"),
        document.getNormalization().getAppliedRungsList());
    // Runs cover both texts exactly (UTF-16 units before the encoding pass).
    int original = 0;
    int normalized = 0;
    for (final AlignmentRun run : document.getNormalization().getAlignmentList()) {
      original += run.getOriginalUnits();
      normalized += run.getNormalizedUnits();
    }
    assertEquals(rawText.length(), original);
    assertEquals(document.getNormalization().getNormalizedText().length(), normalized);
  }

  @Test
  void normalizeWithoutAlignmentWhenOpaqueRungPermitted() {
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder().setRawText("Cafe" + cp(0x0301) + "");
    final NormalizationSpec spec = NormalizationSpec.newBuilder()
        .addRungs(NormalizationRung.NORMALIZATION_RUNG_NFC)
        .setRequireAlignment(false)
        .build();
    final List<ProcessingDiagnostic> diagnostics = new ArrayList<>();
    ClassicStepRunner.normalize("Cafe" + cp(0x0301) + "", spec, document, diagnostics);
    assertEquals(0, document.getNormalization().getAlignmentCount());
    assertFalse(document.getNormalization().getNormalizedText().isEmpty());
    assertTrue(diagnostics.stream().anyMatch(d -> d.getMessage().contains("no alignment")));
  }

  // ---------- AlignmentRuns reconstruction ----------

  @Test
  void alignmentRunsReconstructExpansionContractionAndTrim() {
    // "  A" + sharp s: whitespace collapse trims/contracts, full case fold expands.
    final var aligned = TextNormalizer.builder().whitespace().fullCaseFold().buildAligned()
        .normalizeAligned("  A" + cp(0x00DF));
    final List<AlignmentRun> runs = AlignmentRuns.from(aligned.alignment());
    int original = 0;
    int normalized = 0;
    for (final AlignmentRun run : runs) {
      original += run.getOriginalUnits();
      normalized += run.getNormalizedUnits();
      if (run.getEqual()) {
        assertEquals(run.getOriginalUnits(), run.getNormalizedUnits());
      }
    }
    assertEquals(4, original);
    assertEquals(aligned.normalizedString().length(), normalized);
  }

  // ---------- Term layers ----------

  @Test
  void termLayersCarryTheRequestedDimensions() {
    final String rawText = "Ma" + cp(0x00DF) + "e " + cp(0x1F600);
    final OpenNlpDocument.Builder document = documentWithSentence(rawText);
    ClassicStepRunner.tokenizeUax29(rawText, document, new ArrayList<>());
    ClassicStepRunner.computeTermLayers(document,
        List.of("FULL_CASE_FOLD", "EMOJI_FOLD"), new ArrayList<>());
    final List<Token> tokens = document.getSentences(0).getTokensList();
    assertEquals("masse", tokens.get(0).getTermLayersOrThrow("FULL_CASE_FOLD"));
    assertEquals(":D", tokens.get(1).getTermLayersOrThrow("EMOJI_FOLD"));
  }

  // ---------- Offset-encoding rescale of alignment runs ----------

  @Test
  void alignmentRunsRescaleToUtf8Bytes() {
    // The emoji is 2 UTF-16 units but 4 UTF-8 bytes; after the encoding pass the run units
    // must be bytes on both sides, still covering both texts exactly.
    final String rawText = "ok   :-)";
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder().setRawText(rawText);
    final NormalizationSpec spec = NormalizationSpec.newBuilder()
        .addRungs(NormalizationRung.NORMALIZATION_RUNG_EMOTICON_TO_EMOJI)
        .addRungs(NormalizationRung.NORMALIZATION_RUNG_WHITESPACE)
        .build();
    ClassicStepRunner.normalize(rawText, spec, document, new ArrayList<>());
    DocumentOffsetEncoder.apply(document, rawText, OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);
    int original = 0;
    int normalized = 0;
    for (final AlignmentRun run : document.getNormalization().getAlignmentList()) {
      original += run.getOriginalUnits();
      normalized += run.getNormalizedUnits();
    }
    assertEquals(rawText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, original);
    assertEquals(document.getNormalization().getNormalizedText()
        .getBytes(java.nio.charset.StandardCharsets.UTF_8).length, normalized);
  }
}
