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
package org.apache.opennlp.grpc.format;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;

/**
 * Renders the analyzed document as one token-per-row TSV table with a header row:
 * sentence index, token index, document span, token text, POS tag, and lemma. Empty
 * cells mark absent annotations; tabs and line breaks inside token text are replaced
 * with spaces so every row stays one line.
 */
public final class TsvDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  private static final String HEADER = "sentence\ttoken\tstart\tend\ttext\tpos\tlemma\n";

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public TsvDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "tsv";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "Token TSV";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "text/tab-separated-values";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "tsv";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    final StringBuilder table = new StringBuilder(HEADER);
    final List<AnnotatedSentence> sentences = reply.getSentencesList();
    for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
      final List<Token> tokens = sentences.get(sentenceIndex).getTokensList();
      for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
        final Token token = tokens.get(tokenIndex);
        table.append(sentenceIndex).append('\t')
            .append(tokenIndex).append('\t')
            .append(token.getAnnotationSpan().getStart()).append('\t')
            .append(token.getAnnotationSpan().getEnd()).append('\t')
            .append(cell(token.getText())).append('\t')
            .append(token.hasPosTag() ? cell(token.getPosTag()) : "").append('\t')
            .append(token.hasLemma() ? cell(token.getLemma()) : "").append('\n');
      }
    }
    output.write(table.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** Replaces row and cell separators inside one cell value with spaces. */
  private static String cell(String value) {
    final StringBuilder cell = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      cell.append(character == '\t' || character == '\n' || character == '\r'
          ? ' ' : character);
    }
    return cell.toString();
  }
}
