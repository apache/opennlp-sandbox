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

import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Keeps retired vocabulary out of every user-visible string. The glossary decisions that
 * retired each word live in docs/ai-slop/research/industry-terminology; the wire keeps its
 * names (PersistIndex, SealIndex, immutable), the screen uses the standard ones.
 */

/** Each retired word with the phrase that replaced it, for the failure message. */
const RETIRED: ReadonlyArray<readonly [word: string, replacement: string]> = [
  ["workspace", "live index"],
  ["workspaces", "live indexes"],
  ["dynamic", "live"],
  ["checkpoint", "save to disk"],
  ["seal", "make read-only"],
  ["sealed", "read-only"],
  ["immutable", "read-only"],
  ["drift", "coverage"],
  ["projection", "chunk group"],
  ["projections", "chunk groups"],
  ["x-ray", "alignment"],
  ["shape", "typed annotations"],
  ["bundle", "model pack"],
  ["bundles", "model packs"],
  ["workflow", "build"],
  ["workflows", "builds"],
  ["compound query", "advanced search"],
  ["feature preset", "preset"],
];

const ROOT = join(__dirname, "..");

/** Text a person reads in index.html: element text plus the attributes browsers show. */
function visibleHtmlStrings(): string[] {
  const html = readFileSync(join(ROOT, "index.html"), "utf8");
  const strings: string[] = [];
  let index = 0;
  let inScript = false;
  while (index < html.length) {
    if (html[index] === "<") {
      const close = html.indexOf(">", index);
      const tag = html.slice(index + 1, close);
      if (tag.startsWith("script")) {
        inScript = true;
      } else if (tag.startsWith("/script")) {
        inScript = false;
      }
      // An option's value is wire data; every other value attribute is text a person sees.
      const attributes = tag.startsWith("option")
        ? ["placeholder", "title", "aria-label"]
        : ["placeholder", "title", "aria-label", "value"];
      for (const attribute of attributes) {
        const marker = ` ${attribute}="`;
        const start = tag.indexOf(marker);
        if (start >= 0) {
          const end = tag.indexOf("\"", start + marker.length);
          strings.push(tag.slice(start + marker.length, end));
        }
      }
      index = close + 1;
      continue;
    }
    const next = html.indexOf("<", index);
    const text = html.slice(index, next < 0 ? html.length : next).replace(/\s+/g, " ").trim();
    if (text && !inScript) {
      strings.push(text);
    }
    index = next < 0 ? html.length : next;
  }
  return strings;
}

/** Double-quoted and template literals in the sources that read as prose: they hold a space. */
function proseLiteralsInSources(): Array<{ file: string; text: string }> {
  const found: Array<{ file: string; text: string }> = [];
  const dir = join(ROOT, "src");
  for (const file of readdirSync(dir).filter((name) => name.endsWith(".ts")).sort()) {
    const source = readFileSync(join(dir, file), "utf8");
    for (const quote of ["\"", "`"]) {
      let index = source.indexOf(quote);
      while (index >= 0) {
        let end = index + 1;
        while (end < source.length && source[end] !== quote) {
          if (source[end] === "\\") {
            end++;
          }
          if (quote === "\"" && source[end] === "\n") {
            break;
          }
          end++;
        }
        const text = withoutTemplateExpressions(source.slice(index + 1, end));
        if (text.includes(" ") && !text.startsWith("/") && !text.includes("://")) {
          found.push({ file, text });
        }
        index = source.indexOf(quote, end + 1);
      }
    }
  }
  return found;
}

/** Drops every `${...}` expression: code inside a template is not prose. */
function withoutTemplateExpressions(text: string): string {
  let out = "";
  let depth = 0;
  let index = 0;
  while (index < text.length) {
    if (text.startsWith("${", index)) {
      depth++;
      index += 2;
      continue;
    }
    if (depth > 0 && text[index] === "}") {
      depth--;
      index++;
      continue;
    }
    if (depth === 0) {
      out += text[index];
    }
    index++;
  }
  return out;
}

/** Whether a retired word appears as a whole word, case-insensitively. */
function containsWord(text: string, word: string): boolean {
  const lower = text.toLowerCase();
  let from = 0;
  while (from <= lower.length - word.length) {
    const at = lower.indexOf(word, from);
    if (at < 0) {
      return false;
    }
    const before = at === 0 ? " " : lower[at - 1]!;
    const after = at + word.length >= lower.length ? " " : lower[at + word.length]!;
    if (!isWordChar(before) && !isWordChar(after)) {
      return true;
    }
    from = at + 1;
  }
  return false;
}

function isWordChar(char: string): boolean {
  return (char >= "a" && char <= "z") || (char >= "0" && char <= "9") || char === "-";
}

/** Strings that mention a retired word on purpose: API names quoted for developers. */
const ALLOWED = [
  "From code these are the persist, seal, reindex, alias, and collection RPCs on",
  // The coverage flyout defines the retired word on purpose: falling coverage is drift.
  "Falling coverage over time is vocabulary drift",
];

function violations(strings: Array<{ file: string; text: string }>): string[] {
  const found: string[] = [];
  for (const { file, text } of strings) {
    if (ALLOWED.some((allowed) => text.includes(allowed))) {
      continue;
    }
    for (const [word, replacement] of RETIRED) {
      if (containsWord(text, word)) {
        found.push(`${file}: "${text.slice(0, 90)}" uses "${word}" (say "${replacement}")`);
      }
    }
  }
  return found;
}

describe("retired vocabulary", () => {
  it("is absent from every visible string in index.html", () => {
    expect(violations(visibleHtmlStrings().map((text) => ({ file: "index.html", text }))))
      .toEqual([]);
  });

  it("is absent from every prose literal in the sources", () => {
    expect(violations(proseLiteralsInSources())).toEqual([]);
  });
});
