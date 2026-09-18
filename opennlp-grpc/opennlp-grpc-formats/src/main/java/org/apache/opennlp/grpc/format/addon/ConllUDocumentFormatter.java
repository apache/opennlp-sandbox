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
package org.apache.opennlp.grpc.format.addon;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;

/**
 * Renders the analyzed document as CoNLL-U: per sentence a {@code sent_id} and
 * {@code text} comment followed by one ten-column line per token. The lemma and POS tag
 * fill LEMMA and XPOS when present; UPOS, FEATS, and the dependency columns stay
 * {@code _}; MISC carries {@code SpaceAfter=No} when the next token starts at this
 * token's end offset.
 */
public final class ConllUDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  private static final String ABSENT = "_";

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public ConllUDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "conllu";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "CoNLL-U";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "text/plain";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "conllu";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    final StringBuilder rendered = new StringBuilder();
    final String documentId = reply.getDocId().isBlank() ? "doc" : reply.getDocId();
    final List<AnnotatedSentence> sentences = reply.getSentencesList();
    for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
      final AnnotatedSentence sentence = sentences.get(sentenceIndex);
      rendered.append("# sent_id = ").append(documentId)
          .append('-').append(sentenceIndex + 1).append('\n');
      rendered.append("# text = ")
          .append(sanitize(sentenceText(reply, sentence))).append('\n');
      final List<Token> tokens = sentence.getTokensList();
      for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
        final Token token = tokens.get(tokenIndex);
        rendered.append(tokenIndex + 1).append('\t')
            .append(sanitize(token.getText())).append('\t')
            .append(token.hasLemma() ? sanitize(token.getLemma()) : ABSENT).append('\t')
            .append(ABSENT).append('\t')
            .append(token.hasPosTag() ? sanitize(token.getPosTag()) : ABSENT).append('\t')
            .append(ABSENT).append('\t')
            .append(ABSENT).append('\t')
            .append(ABSENT).append('\t')
            .append(ABSENT).append('\t')
            .append(spaceAfter(tokens, tokenIndex)).append('\n');
      }
      rendered.append('\n');
    }
    output.write(rendered.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** Returns the sentence's raw text through its document span, or empty when unset. */
  private static String sentenceText(OpenNlpDocument document, AnnotatedSentence sentence) {
    final String raw = document.getRawText();
    final int start = sentence.getSentenceSpan().getStart();
    final int end = sentence.getSentenceSpan().getEnd();
    if (start < 0 || end < start || end > raw.length()) {
      return "";
    }
    return raw.substring(start, end);
  }

  /** Marks a token followed with no gap, per the CoNLL-U SpaceAfter convention. */
  private static String spaceAfter(List<Token> tokens, int tokenIndex) {
    if (tokenIndex + 1 < tokens.size()
        && tokens.get(tokenIndex + 1).getAnnotationSpan().getStart()
            == tokens.get(tokenIndex).getAnnotationSpan().getEnd()) {
      return "SpaceAfter=No";
    }
    return ABSENT;
  }

  /** Replaces column and line separators inside one value with spaces. */
  private static String sanitize(String value) {
    final StringBuilder cell = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      cell.append(character == '\t' || character == '\n' || character == '\r'
          ? ' ' : character);
    }
    return cell.toString();
  }
}
