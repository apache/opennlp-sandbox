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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-level tests pinning the OPENNLP-205 sentence span mapping through the wire:
 * {@code SentenceDetectorME} now trims the full Unicode White_Space set (so exotic
 * separators such as NBSP and NEL never lead or trail a sentence span), while the
 * U+001C..U+001F information separator controls are no longer treated as whitespace
 * and stay span content. The assertions slice {@code raw_text} with the returned
 * spans in the response's own offset encoding, so they also prove the UTF-8 byte
 * rescale (the default) and the UTF-16 pass-through are applied to the new spans.
 */
class BasicDocumentAnalyzerSentenceSpanTest {

  private static final String FIRST = "This is a test.";
  private static final String SECOND = "There are many tests, this is the second.";
  private static final String THIRD = "And here is one more!";

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private AnalyzeDocumentResponse detectSentences(String rawText, OffsetEncoding encoding) {
    final AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(rawText).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("sentences-only")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .build());
    if (encoding != null) {
      request.setOptions(AnalysisOptions.newBuilder().setOffsetEncoding(encoding).build());
    }
    return analyzer.analyze(request.build());
  }

  private static String sliceUtf8(byte[] rawBytes, AnnotationSpan span) {
    return new String(rawBytes, span.getStart(), span.getEnd() - span.getStart(),
        StandardCharsets.UTF_8);
  }

  private static void assertNoExoticEdges(String sliced) {
    assertFalse(sliced.isEmpty(), "Sliced sentence must not be empty");
    assertFalse(Character.isWhitespace(sliced.charAt(0))
            || Character.getType(sliced.charAt(0)) == Character.SPACE_SEPARATOR,
        "Sliced sentence must not start with whitespace: " + sliced);
    final char last = sliced.charAt(sliced.length() - 1);
    assertFalse(Character.isWhitespace(last)
            || Character.getType(last) == Character.SPACE_SEPARATOR,
        "Sliced sentence must not end with whitespace: " + sliced);
  }

  @Test
  void nbspAndNelBetweenSentencesAreTrimmedFromUtf8ByteSpans() {
    // NBSP (U+00A0) and NEL (U+0085) are Unicode White_Space; with the OPENNLP-205 mapping
    // they are trimmed from span edges. Both are 1 UTF-16 unit but 2 UTF-8 bytes, so slicing
    // the raw bytes with the returned offsets also proves the UTF-8 rescale of the new spans.
    final String rawText = FIRST + cp(0x00A0) + SECOND + " " + cp(0x0085) + " " + THIRD;
    final AnalyzeDocumentResponse response = detectSentences(rawText, null);

    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE,
        response.getDocument().getOffsetEncoding());
    final List<AnnotatedSentence> sentences = response.getDocument().getSentencesList();
    assertEquals(3, sentences.size());

    final byte[] rawBytes = rawText.getBytes(StandardCharsets.UTF_8);
    final List<String> expected = List.of(FIRST, SECOND, THIRD);
    for (int i = 0; i < expected.size(); i++) {
      final String sliced = sliceUtf8(rawBytes, sentences.get(i).getSentenceSpan());
      assertEquals(expected.get(i), sliced, "sentence " + i);
      assertNoExoticEdges(sliced);
    }
  }

  @Test
  void nbspAndNelBetweenSentencesAreTrimmedFromUtf16Spans() {
    final String rawText = FIRST + cp(0x00A0) + SECOND + " " + cp(0x0085) + " " + THIRD;
    final AnalyzeDocumentResponse response =
        detectSentences(rawText, OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT);

    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT,
        response.getDocument().getOffsetEncoding());
    final List<AnnotatedSentence> sentences = response.getDocument().getSentencesList();
    assertEquals(3, sentences.size());

    final List<String> expected = List.of(FIRST, SECOND, THIRD);
    for (int i = 0; i < expected.size(); i++) {
      final AnnotationSpan span = sentences.get(i).getSentenceSpan();
      final String sliced = rawText.substring(span.getStart(), span.getEnd());
      assertEquals(expected.get(i), sliced, "sentence " + i);
      assertNoExoticEdges(sliced);
    }
  }

  @Test
  void informationSeparatorFourStaysSpanContent() {
    // Deliberate library delta pinned through the wire: U+001C (INFORMATION SEPARATOR FOUR)
    // is not Unicode White_Space, so unlike the old StringUtil-based mapping it is no longer
    // trimmed from span edges and arrives as sentence content.
    final String rawText = FIRST + " " + cp(0x001C) + SECOND;
    final AnalyzeDocumentResponse response =
        detectSentences(rawText, OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT);

    final List<AnnotatedSentence> sentences = response.getDocument().getSentencesList();
    assertEquals(2, sentences.size());
    final AnnotationSpan first = sentences.get(0).getSentenceSpan();
    assertEquals(FIRST, rawText.substring(first.getStart(), first.getEnd()));

    final AnnotationSpan second = sentences.get(1).getSentenceSpan();
    final String sliced = rawText.substring(second.getStart(), second.getEnd());
    assertTrue(sliced.startsWith(cp(0x001C)),
        "U+001C must be span content, not trimmed whitespace: " + sliced);
    assertEquals(cp(0x001C) + SECOND, sliced);
  }
}
