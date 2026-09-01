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
package org.apache.opennlp.grpc.spi.model;

import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;

/**
 * Span arithmetic shared by the token-based NER models: OpenNLP name finders return
 * token-index spans, which the document shape carries as document character offsets.
 */
public final class NerSpans {

  private NerSpans() {
  }

  /**
   * Returns the sentence's token text in order.
   *
   * @param sentence The sentence whose tokens to copy.
   *
   * @return One entry per token. Never {@code null}.
   */
  public static String[] tokenTexts(AnnotatedSentence sentence) {
    final String[] tokens = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
    }
    return tokens;
  }

  /**
   * Converts a token-index span to document character offsets using the tokens' own spans.
   *
   * @param sentence The sentence the span indexes into.
   * @param tokenSpan The token-index span an OpenNLP name finder returned.
   *
   * @return The document-relative character span.
   * @throws IllegalStateException If the span lies outside the sentence's tokens.
   */
  public static AnnotationSpan tokenSpanToDocumentSpan(AnnotatedSentence sentence, Span tokenSpan) {
    final int startToken = tokenSpan.getStart();
    final int endToken = tokenSpan.getEnd();
    if (startToken < 0 || endToken <= startToken || endToken > sentence.getTokensCount()) {
      throw new IllegalStateException("Name finder span is out of token bounds: " + tokenSpan);
    }
    return AnnotationSpan.newBuilder()
        .setStart(sentence.getTokens(startToken).getAnnotationSpan().getStart())
        .setEnd(sentence.getTokens(endToken - 1).getAnnotationSpan().getEnd())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  /**
   * Returns the authoritative entity type for a found span: the type the finder emitted on
   * the span when present (set by multi-class models), otherwise the type the finder was
   * registered under.
   *
   * @param configuredType The entity type of the finder's registration.
   * @param span The span the finder returned.
   *
   * @return The entity type to report. Never {@code null}.
   */
  public static String resolveEntityType(String configuredType, Span span) {
    final String spanType = span.getType();
    if (spanType != null && !spanType.isBlank()) {
      return spanType;
    }
    return configuredType;
  }
}
