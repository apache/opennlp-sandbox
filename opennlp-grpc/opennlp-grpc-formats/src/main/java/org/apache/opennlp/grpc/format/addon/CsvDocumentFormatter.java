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
 * Renders the analyzed document as one token-per-row RFC 4180 CSV table with a header
 * row: sentence index, token index, document span, token text, POS tag, and lemma.
 * Fields holding a comma, quote, or line break are quoted with doubled quotes, and
 * rows end in CRLF as the RFC specifies.
 */
public final class CsvDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public CsvDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "csv";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "Token CSV";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "text/csv";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "csv";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    final StringBuilder table = new StringBuilder(
        "sentence,token,start,end,text,pos,lemma\r\n");
    final List<AnnotatedSentence> sentences = reply.getSentencesList();
    for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
      final List<Token> tokens = sentences.get(sentenceIndex).getTokensList();
      for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
        final Token token = tokens.get(tokenIndex);
        table.append(sentenceIndex).append(',')
            .append(tokenIndex).append(',')
            .append(token.getAnnotationSpan().getStart()).append(',')
            .append(token.getAnnotationSpan().getEnd()).append(',')
            .append(field(token.getText())).append(',')
            .append(token.hasPosTag() ? field(token.getPosTag()) : "").append(',')
            .append(token.hasLemma() ? field(token.getLemma()) : "").append("\r\n");
      }
    }
    output.write(table.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** Quotes one field when it holds a separator, quote, or line break. */
  private static String field(String value) {
    boolean quote = false;
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      if (character == ',' || character == '"' || character == '\n' || character == '\r') {
        quote = true;
        break;
      }
    }
    if (!quote) {
      return value;
    }
    final StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      if (character == '"') {
        quoted.append('"');
      }
      quoted.append(character);
    }
    return quoted.append('"').toString();
  }
}
