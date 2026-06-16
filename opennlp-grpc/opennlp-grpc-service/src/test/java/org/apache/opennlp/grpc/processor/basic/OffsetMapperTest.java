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

import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OffsetMapperTest {

  // "Hi 😀." : H,i,space are 1 Java char each; 😀 (U+1F600) is a surrogate pair
  // (2 Java chars, 1 code point, 4 UTF-8 bytes); '.' is 1 char. Java length = 6.
  private static final String TEXT = "Hi 😀.";

  @Test
  void unspecifiedDefaultsToUtf8Byte() {
    final OffsetMapper mapper = OffsetMapper.forText(TEXT, OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED);
    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE, mapper.encoding());
  }

  @Test
  void utf8ByteOffsets() {
    final OffsetMapper mapper = OffsetMapper.forText(TEXT, OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);
    assertEquals(0, mapper.toTarget(0));   // start
    assertEquals(3, mapper.toTarget(3));   // before emoji: "Hi " = 3 bytes
    assertEquals(7, mapper.toTarget(5));   // after emoji (4 bytes): 3 + 4 = 7
    assertEquals(8, mapper.toTarget(6));   // plus '.' = 8 total bytes
  }

  @Test
  void utf16CodeUnitOffsetsAreIdentity() {
    final OffsetMapper mapper = OffsetMapper.forText(TEXT, OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT);
    assertEquals(3, mapper.toTarget(3));
    assertEquals(5, mapper.toTarget(5));
    assertEquals(6, mapper.toTarget(6));
  }

  @Test
  void unicodeCodePointOffsets() {
    final OffsetMapper mapper = OffsetMapper.forText(TEXT, OffsetEncoding.OFFSET_ENCODING_UNICODE_CODE_POINT);
    assertEquals(3, mapper.toTarget(3));   // H, i, space = 3 code points
    assertEquals(4, mapper.toTarget(5));   // + emoji = 4 code points
    assertEquals(5, mapper.toTarget(6));   // + '.' = 5 code points
  }

  // "Café!" : C,a,f,e are 1 byte each; U+0301 (combining acute accent) is its own
  // Java char / code point but 2 UTF-8 bytes; '!' is 1 byte. Java length = 6.
  private static final String COMBINING = "Cafe\u0301!";

  @Test
  void combiningMarkUtf8Offsets() {
    final OffsetMapper mapper = OffsetMapper.forText(COMBINING, OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);
    assertEquals(4, mapper.toTarget(4));   // "Cafe" = 4 bytes (before the combining mark)
    assertEquals(6, mapper.toTarget(5));   // + combining mark (2 bytes) = 6
    assertEquals(7, mapper.toTarget(6));   // + '!' = 7 total bytes
  }

  @Test
  void combiningMarkCodePointOffsets() {
    final OffsetMapper mapper =
        OffsetMapper.forText(COMBINING, OffsetEncoding.OFFSET_ENCODING_UNICODE_CODE_POINT);
    // No surrogates: every Java char is one code point, so offsets are the identity.
    assertEquals(4, mapper.toTarget(4));
    assertEquals(5, mapper.toTarget(5));
    assertEquals(6, mapper.toTarget(6));
  }

  @Test
  void threeByteCjkUtf8Offsets() {
    // "A你B": '你' (U+4F60) is one Java char / code point but 3 UTF-8 bytes.
    final OffsetMapper mapper =
        OffsetMapper.forText("A你B", OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);
    assertEquals(1, mapper.toTarget(1));   // "A" = 1 byte
    assertEquals(4, mapper.toTarget(2));   // + 3-byte CJK = 4
    assertEquals(5, mapper.toTarget(3));   // + "B" = 5 total bytes
  }

  @Test
  void multipleSurrogatePairsUtf8Offsets() {
    // Two emoji back to back: each is a surrogate pair (2 Java chars, 4 UTF-8 bytes).
    final OffsetMapper mapper =
        OffsetMapper.forText("😀😀", OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);
    assertEquals(0, mapper.toTarget(0));
    assertEquals(4, mapper.toTarget(2));   // after first emoji = 4 bytes
    assertEquals(8, mapper.toTarget(4));   // after second emoji = 8 bytes
  }
}
