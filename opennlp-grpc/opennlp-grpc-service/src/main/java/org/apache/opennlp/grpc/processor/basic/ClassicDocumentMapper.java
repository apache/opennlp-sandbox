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

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;

/** Maps nested gRPC annotations to the flat layers used by OpenNLP annotators. */
final class ClassicDocumentMapper {

  /**
   * Maps the wire sentence spans onto a new document.
   *
   * @param rawText The original document text.
   * @param wire The wire document containing sentence spans.
   * @return A document with its sentence layer populated.
   */
  Document withSentences(String rawText, OpenNlpDocument.Builder wire) {
    final List<Annotation<String>> sentences = new ArrayList<>(wire.getSentencesCount());
    for (AnnotatedSentence sentence : wire.getSentencesList()) {
      final Span span = span(sentence.getSentenceSpan());
      sentences.add(new Annotation<>(span, span.getCoveredText(rawText).toString()));
    }
    return Document.of(rawText).with(Layers.SENTENCES, sentences);
  }

  /**
   * Maps the wire sentence and token spans onto a new document.
   *
   * @param rawText The original document text.
   * @param wire The wire document containing sentences and tokens.
   * @return A document with its sentence and token layers populated.
   */
  Document withTokens(String rawText, OpenNlpDocument.Builder wire) {
    Document document = withSentences(rawText, wire);
    final List<Annotation<String>> tokens = new ArrayList<>();
    for (AnnotatedSentence sentence : wire.getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        tokens.add(new Annotation<>(span(token.getAnnotationSpan()), token.getText()));
      }
    }
    return document.with(Layers.TOKENS, tokens);
  }

  /**
   * Maps the wire sentence, token, and part-of-speech annotations onto a new document.
   *
   * @param rawText The original document text.
   * @param wire The wire document containing sentences, tokens, and tags.
   * @return A document with its sentence, token, and tag layers populated.
   */
  Document withPosTags(String rawText, OpenNlpDocument.Builder wire) {
    Document document = withTokens(rawText, wire);
    final List<Annotation<String>> tags = new ArrayList<>();
    for (AnnotatedSentence sentence : wire.getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        tags.add(new Annotation<>(span(token.getAnnotationSpan()), token.getPosTag()));
      }
    }
    return document.with(Layers.POS_TAGS, tags);
  }

  /** Converts a wire span to the core OpenNLP span type. */
  private Span span(AnnotationSpan span) {
    return new Span(span.getStart(), span.getEnd());
  }
}
