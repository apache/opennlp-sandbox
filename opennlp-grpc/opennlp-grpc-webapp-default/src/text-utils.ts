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

/** Lowercases ASCII letters without invoking locale-sensitive Unicode casing. */
export function asciiLowerCase(value: string): string {
  let result = "";
  for (const character of value) {
    const codePoint = character.codePointAt(0)!;
    result += codePoint >= 0x41 && codePoint <= 0x5a
      ? String.fromCodePoint(codePoint + 0x20)
      : character;
  }
  return result;
}

/** Uppercases ASCII letters without invoking locale-sensitive Unicode casing. */
export function asciiUpperCase(value: string): string {
  let result = "";
  for (const character of value) {
    const codePoint = character.codePointAt(0)!;
    result += codePoint >= 0x61 && codePoint <= 0x7a
      ? String.fromCodePoint(codePoint - 0x20)
      : character;
  }
  return result;
}

/** Collapses ECMAScript whitespace runs to one ASCII space and trims the ends. */
export function collapseWhitespace(value: string): string {
  let result = "";
  let pendingSpace = false;
  for (const character of value) {
    if (isWhitespaceCodePoint(character.codePointAt(0)!)) {
      pendingSpace = result.length > 0;
      continue;
    }
    if (pendingSpace) {
      result += " ";
      pendingSpace = false;
    }
    result += character;
  }
  return result;
}

/** Splits on ECMAScript whitespace. */
export function splitWords(value: string): string[] {
  const words: string[] = [];
  let word = "";
  for (const character of value) {
    if (isWhitespaceCodePoint(character.codePointAt(0)!)) {
      if (word) {
        words.push(word);
        word = "";
      }
    } else {
      word += character;
    }
  }
  if (word) {
    words.push(word);
  }
  return words;
}

/** Splits on any listed code point, omitting empty parts. */
export function splitOnCharacters(value: string, separators: string): string[] {
  const parts: string[] = [];
  let part = "";
  for (const character of value) {
    if (hasCharacter(separators, character)) {
      if (part) {
        parts.push(part);
        part = "";
      }
    } else {
      part += character;
    }
  }
  if (part) {
    parts.push(part);
  }
  return parts;
}

/** Replaces every occurrence of one code point with a fixed string. */
export function replaceCharacter(value: string, target: string, replacement: string): string {
  let result = "";
  for (const character of value) {
    result += character === target ? replacement : character;
  }
  return result;
}

/** Removes a fixed prefix when present. */
export function withoutPrefix(value: string, prefix: string): string {
  return value.startsWith(prefix) ? value.slice(prefix.length) : value;
}

/** Returns at most count complete Unicode code points. */
export function firstCodePoints(value: string, count: number): string {
  if (count <= 0) {
    return "";
  }
  let result = "";
  let consumed = 0;
  for (const character of value) {
    if (consumed >= count) {
      break;
    }
    result += character;
    consumed++;
  }
  return result;
}

/** Compares strings by Unicode code point without locale collation. */
export function compareCodePoints(left: string, right: string): number {
  const leftCursor = left[Symbol.iterator]();
  const rightCursor = right[Symbol.iterator]();
  while (true) {
    const leftValue = leftCursor.next();
    const rightValue = rightCursor.next();
    if (leftValue.done || rightValue.done) {
      if (leftValue.done && rightValue.done) {
        return 0;
      }
      return leftValue.done ? -1 : 1;
    }
    const difference = leftValue.value.codePointAt(0)! - rightValue.value.codePointAt(0)!;
    if (difference !== 0) {
      return difference;
    }
  }
}

/** Shortens a string to a code-point limit and uses the final position for an ellipsis. */
export function ellipsizeCodePoints(value: string, limit: number): string {
  if (limit <= 0) {
    return "";
  }
  let prefix = "";
  let count = 0;
  for (const character of value) {
    if (count < limit - 1) {
      prefix += character;
    }
    count++;
    if (count > limit) {
      return `${prefix}…`;
    }
  }
  return value;
}

/** Escapes text for HTML strings used by the chart tooltip API. */
export function escapeHtml(value: string): string {
  let result = "";
  for (const character of value) {
    switch (character) {
      case "&":
        result += "&amp;";
        break;
      case "<":
        result += "&lt;";
        break;
      case ">":
        result += "&gt;";
        break;
      case "\"":
        result += "&quot;";
        break;
      case "'":
        result += "&#39;";
        break;
      default:
        result += character;
    }
  }
  return result;
}

/** Formats an integer with fixed ASCII comma grouping. */
export function formatInteger(value: number): string {
  if (!Number.isFinite(value)) {
    return String(value);
  }
  const integer = Math.trunc(value);
  const negative = integer < 0;
  const digits = String(Math.abs(integer));
  let result = "";
  for (let index = 0; index < digits.length; index++) {
    if (index > 0 && (digits.length - index) % 3 === 0) {
      result += ",";
    }
    result += digits.charAt(index);
  }
  return negative ? `-${result}` : result;
}

function hasCharacter(value: string, target: string): boolean {
  for (const character of value) {
    if (character === target) {
      return true;
    }
  }
  return false;
}

/** Returns whether a code point has ECMAScript whitespace semantics. */
export function isWhitespaceCodePoint(codePoint: number): boolean {
  return (codePoint >= 0x0009 && codePoint <= 0x000d)
    || codePoint === 0x0020
    || codePoint === 0x00a0
    || codePoint === 0x1680
    || (codePoint >= 0x2000 && codePoint <= 0x200a)
    || codePoint === 0x2028
    || codePoint === 0x2029
    || codePoint === 0x202f
    || codePoint === 0x205f
    || codePoint === 0x3000
    || codePoint === 0xfeff;
}

/**
 * Splits blank-line-delimited text into trimmed, non-empty document blocks.
 * A blank line is empty or holds only spaces and tabs; CRLF pairs count once.
 */
export function splitBlankLineDocuments(text: string): string[] {
  const blocks: string[] = [];
  let block = "";
  let cursor = 0;
  while (cursor <= text.length) {
    const start = cursor;
    while (cursor < text.length && text.charAt(cursor) !== "\n"
        && text.charAt(cursor) !== "\r") {
      cursor++;
    }
    const line = text.slice(start, cursor);
    if (isBlankLine(line)) {
      if (block) {
        blocks.push(block);
        block = "";
      }
    } else {
      block += block ? `\n${line}` : line;
    }
    if (cursor >= text.length) {
      break;
    }
    if (text.charAt(cursor) === "\r" && text.charAt(cursor + 1) === "\n") {
      cursor++;
    }
    cursor++;
  }
  if (block) {
    blocks.push(block);
  }
  return blocks.map((value) => value.trim()).filter((value) => value.length > 0);
}

/** Reports whether a line is empty or holds only spaces and tabs. */
function isBlankLine(line: string): boolean {
  for (const character of line) {
    if (character !== " " && character !== "\t") {
      return false;
    }
  }
  return true;
}

/**
 * Formats a protobuf JSON timestamp, given as an ISO-8601 string or as epoch
 * seconds, as "YYYY-MM-DD HH:MM UTC"; empty when the value is unreadable.
 */
export function timestampLabel(value: string): string {
  if (!value) {
    return "";
  }
  const date = isDecimalDigits(value) ? new Date(Number(value) * 1000) : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const iso = date.toISOString();
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)} UTC`;
}

/** Reports whether a non-empty string holds only the ASCII digits 0-9. */
function isDecimalDigits(value: string): boolean {
  for (const character of value) {
    if (character < "0" || character > "9") {
      return false;
    }
  }
  return value.length > 0;
}
