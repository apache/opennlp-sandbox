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
package org.apache.opennlp.grpc.processor;

import org.apache.opennlp.grpc.v1.OffsetEncoding;

/**
 * Converts annotation offsets from Java/OpenNLP UTF-16 code-unit indices into the
 * {@link OffsetEncoding} requested by the client. The wire default is UTF-8 bytes,
 * which aligns with the protobuf encoding of {@code raw_text} so non-JVM clients
 * slice the text correctly without conversion.
 *
 * <p>Span boundaries produced by OpenNLP always fall on Unicode code-point
 * boundaries, so a single prefix table keyed by Java char index is sufficient.
 */
final class OffsetMapper {

  private final int[] javaIndexToTarget;
  private final OffsetEncoding encoding;

  private OffsetMapper(int[] javaIndexToTarget, OffsetEncoding encoding) {
    this.javaIndexToTarget = javaIndexToTarget;
    this.encoding = encoding;
  }

  /** Resolves the requested encoding, mapping {@code UNSPECIFIED} to the UTF-8 byte default. */
  static OffsetEncoding resolve(OffsetEncoding requested) {
    return requested == null || requested == OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED
        ? OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE
        : requested;
  }

  static OffsetMapper forText(String text, OffsetEncoding requested) {
    final OffsetEncoding resolved = resolve(requested);
    final int length = text.length();
    final int[] map = new int[length + 1];
    int target = 0;
    int i = 0;
    while (i < length) {
      final int codePoint = text.codePointAt(i);
      final int charCount = Character.charCount(codePoint);
      map[i] = target;
      if (charCount == 2) {
        // Low surrogate index shares the code point start; never a span boundary.
        map[i + 1] = target;
      }
      target += unitsFor(codePoint, resolved, charCount);
      i += charCount;
    }
    map[length] = target;
    return new OffsetMapper(map, resolved);
  }

  OffsetEncoding encoding() {
    return encoding;
  }

  int toTarget(int javaIndex) {
    return javaIndexToTarget[javaIndex];
  }

  private static int unitsFor(int codePoint, OffsetEncoding encoding, int charCount) {
    return switch (encoding) {
      case OFFSET_ENCODING_UTF16_CODE_UNIT -> charCount;
      case OFFSET_ENCODING_UNICODE_CODE_POINT -> 1;
      default -> utf8ByteLength(codePoint);
    };
  }

  private static int utf8ByteLength(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    if (codePoint < 0x10000) {
      return 3;
    }
    return 4;
  }
}
