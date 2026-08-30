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
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * Renders the analyzed document as a human-readable Markdown report: a document
 * heading with language and classification, then one section per sentence with the
 * sentence text as a blockquote, its entities as a bullet list, and its sentiment
 * when present. Markdown control characters inside document text are escaped.
 */
public final class MarkdownDocumentFormatter implements OutputFormatter<OpenNlpDocument> {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public MarkdownDocumentFormatter() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<OpenNlpDocument> inputType() {
    return OpenNlpDocument.class;
  }

  /** {@inheritDoc} */
  @Override
  public String formatId() {
    return "markdown";
  }

  /** {@inheritDoc} */
  @Override
  public String displayName() {
    return "Markdown report";
  }

  /** {@inheritDoc} */
  @Override
  public String mediaType() {
    return "text/markdown";
  }

  /** {@inheritDoc} */
  @Override
  public String fileExtension() {
    return "md";
  }

  /** {@inheritDoc} */
  @Override
  public void format(OpenNlpDocument reply, OutputStream output) throws IOException {
    if (reply == null) {
      throw new IllegalArgumentException("reply must not be null");
    }
    final StringBuilder report = new StringBuilder("# Document");
    if (!reply.getDocId().isBlank()) {
      report.append(' ').append(escape(reply.getDocId()));
    }
    report.append('\n');
    if (reply.hasDetectedLanguage()) {
      report.append("\nLanguage: ").append(escape(reply.getDetectedLanguage())).append('\n');
    }
    if (reply.hasClassification()) {
      report.append("\nClassification: ")
          .append(escape(reply.getClassification().getBestCategory())).append('\n');
    }
    final List<AnnotatedSentence> sentences = reply.getSentencesList();
    for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
      final AnnotatedSentence sentence = sentences.get(sentenceIndex);
      report.append("\n## Sentence ").append(sentenceIndex + 1).append("\n\n");
      report.append("> ").append(escape(sentenceText(reply, sentence))).append('\n');
      if (!sentence.getEntitiesList().isEmpty()) {
        report.append('\n');
        for (NamedEntity entity : sentence.getEntitiesList()) {
          report.append("- **").append(escape(entity.getEntityType())).append("**: ")
              .append(escape(entity.getText())).append('\n');
        }
      }
      if (sentence.hasSentimentLabel()) {
        report.append("\nSentiment: ").append(escape(sentence.getSentimentLabel()))
            .append('\n');
      }
    }
    output.write(report.toString().getBytes(StandardCharsets.UTF_8));
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

  /**
   * Escapes Markdown control characters and folds line breaks into spaces so document
   * text renders literally.
   */
  private static String escape(String value) {
    final StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      if (character == '\n' || character == '\r') {
        escaped.append(' ');
        continue;
      }
      if (character == '\\' || character == '`' || character == '*' || character == '_'
          || character == '[' || character == ']' || character == '<' || character == '>'
          || character == '#' || character == '|') {
        escaped.append('\\');
      }
      escaped.append(character);
    }
    return escaped.toString();
  }
}
