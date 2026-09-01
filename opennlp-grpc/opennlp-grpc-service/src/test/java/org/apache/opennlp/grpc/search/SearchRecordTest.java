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
package org.apache.opennlp.grpc.search;

import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchRecord;

class SearchRecordTest {

  @Test
  void rejectsInvalidUtf8SourceSpans() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId("doc")
        .setRawText("café 😀")
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE)
        .build();

    assertThrows(IllegalArgumentException.class, () -> new SearchRecord(
        "doc", "chunk", document, span(0, 7), "café 😀"));
    new SearchRecord("doc", "chunk", document, span(0, 10), "café 😀");
  }

  @Test
  void rejectsOutOfRangeUtf16AndNonDocumentSpans() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId("doc")
        .setRawText("hello")
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();

    assertThrows(IllegalArgumentException.class, () -> new SearchRecord(
        "doc", "chunk", document, span(0, 6), "hello"));
    assertThrows(IllegalArgumentException.class, () -> new SearchRecord(
        "doc", "chunk", document,
        AnnotationSpan.newBuilder().setStart(0).setEnd(5)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_TOKEN_SENTENCE).build(), "hello"));
  }

  @Test
  void rejectsNonFiniteAndOutOfRangeScores() {
    final SearchRecord record = new SearchRecord("doc", "chunk",
        OpenNlpDocument.newBuilder().setDocId("doc").setRawText("text")
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT).build(),
        span(0, 4), "text");

    assertThrows(IllegalArgumentException.class, () -> new SearchResult(record, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> new SearchResult(record, 1.01));
    assertThrows(IllegalArgumentException.class, () -> new SearchResult(record, -1.01));
  }

  @Test
  void requiresExplicitOffsetsAndMatchingDocumentIdentity() {
    final OpenNlpDocument unspecified = OpenNlpDocument.newBuilder()
        .setDocId("doc").setRawText("text").build();
    assertThrows(IllegalArgumentException.class,
        () -> new SearchRecord("doc", "chunk", unspecified, span(0, 4), "text"));

    final OpenNlpDocument other = unspecified.toBuilder()
        .setDocId("other")
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();
    assertThrows(IllegalArgumentException.class,
        () -> new SearchRecord("doc", "chunk", other, span(0, 4), "text"));
  }

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }
}
