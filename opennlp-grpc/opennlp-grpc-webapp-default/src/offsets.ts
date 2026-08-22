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

export interface BrowserSpan {
  start: number;
  end: number;
}

export function toBrowserSpan(
  text: string,
  start: number,
  end: number,
  encoding: string,
): BrowserSpan | undefined {
  const browserStart = toBrowserOffset(text, start, encoding);
  const browserEnd = toBrowserOffset(text, end, encoding);
  return browserStart !== undefined && browserEnd !== undefined && browserEnd > browserStart
    ? { start: browserStart, end: browserEnd }
    : undefined;
}

export function toBrowserOffset(text: string, offset: number, encoding: string): number | undefined {
  if (!Number.isSafeInteger(offset) || offset < 0) {
    return undefined;
  }
  if (encoding === "OFFSET_ENCODING_UTF16_CODE_UNIT") {
    if (offset > text.length) {
      return undefined;
    }
    const previous = offset > 0 ? text.charCodeAt(offset - 1) : undefined;
    const current = offset < text.length ? text.charCodeAt(offset) : undefined;
    return previous !== undefined && current !== undefined
        && previous >= 0xd800 && previous <= 0xdbff
        && current >= 0xdc00 && current <= 0xdfff
      ? undefined
      : offset;
  }
  if (encoding === "OFFSET_ENCODING_UNICODE_CODE_POINT") {
    if (offset === 0) {
      return 0;
    }
    let codePointOffset = 0;
    let browserOffset = 0;
    for (const character of text) {
      codePointOffset++;
      browserOffset += character.length;
      if (codePointOffset === offset) {
        return browserOffset;
      }
    }
    return undefined;
  }

  let byteOffset = 0;
  let browserOffset = 0;
  if (offset === 0) {
    return 0;
  }
  for (const character of text) {
    byteOffset += utf8Length(character.codePointAt(0)!);
    browserOffset += character.length;
    if (byteOffset === offset) {
      return browserOffset;
    }
    if (byteOffset > offset) {
      return undefined;
    }
  }
  return byteOffset === offset ? browserOffset : undefined;
}

function utf8Length(codePoint: number): number {
  if (codePoint <= 0x7f) {
    return 1;
  }
  if (codePoint <= 0x7ff) {
    return 2;
  }
  return codePoint <= 0xffff ? 3 : 4;
}
