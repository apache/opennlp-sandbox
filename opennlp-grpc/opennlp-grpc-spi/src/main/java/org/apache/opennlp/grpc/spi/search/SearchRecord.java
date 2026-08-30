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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.spi.search;

import java.nio.charset.StandardCharsets;

import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;

/**
 * Immutable source and chunk metadata associated with one indexed vector.
 *
 * @param documentId Stable source-document identifier.
 * @param chunkId Stable indexed-chunk identifier.
 * @param chunkGroupId Stable chunk projection identity.
 * @param sourceDocument Source text retained by the bundle and its metadata.
 * @param sourceSpan Authoritative span in {@code sourceDocument.raw_text}.
 * @param indexedText Text emitted by offline chunk preparation and embedded into the index.
 */
public record SearchRecord(
    String documentId,
    String chunkId,
    String chunkGroupId,
    OpenNlpDocument sourceDocument,
    AnnotationSpan sourceSpan,
    String indexedText) {

  /** Validates identifiers, text, coordinate space, and offset boundaries. */
  public SearchRecord {
    requireText(documentId, "documentId");
    requireText(chunkId, "chunkId");
    requireText(chunkGroupId, "chunkGroupId");
    if (sourceDocument == null) {
      throw new IllegalArgumentException("sourceDocument must not be null");
    }
    if (sourceDocument.getRawText().isBlank()) {
      throw new IllegalArgumentException("sourceDocument.raw_text must not be blank");
    }
    if (!documentId.equals(sourceDocument.getDocId())) {
      throw new IllegalArgumentException("documentId '" + documentId
          + "' must equal sourceDocument.doc_id '" + sourceDocument.getDocId() + "'");
    }
    if (sourceSpan == null) {
      throw new IllegalArgumentException("sourceSpan must not be null");
    }
    if (sourceSpan.getSpace() != CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT) {
      throw new IllegalArgumentException(
          "sourceSpan.space must be COORDINATE_SPACE_CHAR_DOCUMENT");
    }
    validateSpan(sourceDocument, sourceSpan);
    requireText(indexedText, "indexedText");
  }

  /**
   * Creates a record for a source format that does not name chunk projections.
   *
   * @param documentId Stable source-document identifier.
   * @param chunkId Stable indexed-chunk identifier.
   * @param sourceDocument Source text retained by the bundle and its metadata.
   * @param sourceSpan Authoritative source span.
   * @param indexedText Text represented by the indexed vector.
   * @throws IllegalArgumentException If an identifier, document, span, or indexed text is invalid.
   */
  public SearchRecord(String documentId, String chunkId, OpenNlpDocument sourceDocument,
      AnnotationSpan sourceSpan, String indexedText) {
    this(documentId, chunkId, "default", sourceDocument, sourceSpan, indexedText);
  }

  private static void validateSpan(OpenNlpDocument document, AnnotationSpan span) {
    final int start = span.getStart();
    final int end = span.getEnd();
    if (start < 0 || end < start) {
      throw new IllegalArgumentException(
          "sourceSpan must have 0 <= start <= end, was [" + start + ", " + end + ")");
    }
    final String text = document.getRawText();
    final OffsetEncoding encoding = document.getOffsetEncoding();
    final int limit = switch (encoding) {
      case OFFSET_ENCODING_UTF16_CODE_UNIT -> text.length();
      case OFFSET_ENCODING_UNICODE_CODE_POINT -> text.codePointCount(0, text.length());
      case OFFSET_ENCODING_UTF8_BYTE -> text.getBytes(StandardCharsets.UTF_8).length;
      case OFFSET_ENCODING_UNSPECIFIED -> throw new IllegalArgumentException(
          "sourceDocument.offset_encoding must be explicit");
      case UNRECOGNIZED -> throw new IllegalArgumentException(
          "sourceDocument.offset_encoding is unrecognized");
    };
    if (end > limit) {
      throw new IllegalArgumentException("sourceSpan end " + end + " exceeds source text length "
          + limit + " in " + encoding);
    }
    if (encoding == OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE
        && (!isUtf8Boundary(text, start) || !isUtf8Boundary(text, end))) {
      throw new IllegalArgumentException(
          "sourceSpan start and end must be UTF-8 code point boundaries");
    }
    if (encoding == OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT
        && (!isUtf16Boundary(text, start) || !isUtf16Boundary(text, end))) {
      throw new IllegalArgumentException("sourceSpan must not split a UTF-16 surrogate pair");
    }
  }

  private static boolean isUtf8Boundary(String text, int offset) {
    if (offset == 0) {
      return true;
    }
    int bytes = 0;
    for (int index = 0; index < text.length();) {
      final int codePoint = text.codePointAt(index);
      bytes += utf8Length(codePoint);
      if (bytes == offset) {
        return true;
      }
      if (bytes > offset) {
        return false;
      }
      index += Character.charCount(codePoint);
    }
    return bytes == offset;
  }

  private static int utf8Length(int codePoint) {
    if (codePoint <= 0x7f) {
      return 1;
    }
    if (codePoint <= 0x7ff) {
      return 2;
    }
    return codePoint <= 0xffff ? 3 : 4;
  }

  private static boolean isUtf16Boundary(String text, int offset) {
    return offset == 0 || offset == text.length()
        || !(Character.isHighSurrogate(text.charAt(offset - 1))
            && Character.isLowSurrogate(text.charAt(offset)));
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
