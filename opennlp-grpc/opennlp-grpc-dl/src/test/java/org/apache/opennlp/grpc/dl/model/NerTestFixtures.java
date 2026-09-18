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
package org.apache.opennlp.grpc.dl.model;

import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.Token;

/** Sentence fixtures shared by the NER model tests (mirrors the server module test fixture). */
final class NerTestFixtures {

  private NerTestFixtures() {
  }

  /**
   * Builds one token with a document-relative character span.
   *
   * @param text The token text.
   * @param start The inclusive document start offset.
   * @param end The exclusive document end offset.
   *
   * @return The token. Never {@code null}.
   */
  static Token token(String text, int start, int end) {
    return Token.newBuilder()
        .setText(text)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start).setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT).build())
        .build();
  }
}
