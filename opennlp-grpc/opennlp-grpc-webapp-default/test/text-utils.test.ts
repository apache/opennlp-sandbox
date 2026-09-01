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

import { describe, expect, it } from "vitest";

import {
  asciiLowerCase,
  asciiUpperCase,
  collapseWhitespace,
  compareCodePoints,
  ellipsizeCodePoints,
  escapeHtml,
  firstCodePoints,
  formatInteger,
  replaceCharacter,
  splitOnCharacters,
  splitWords,
  timestampLabel,
  withoutPrefix,
} from "../src/text-utils";

describe("locale-independent cursor text helpers", () => {
  it("folds ASCII without changing non-ASCII code points", () => {
    expect(asciiLowerCase("OpenNLP_İÉ")).toBe("opennlp_İÉ");
    expect(asciiUpperCase("OpenNLP_ıé")).toBe("OPENNLP_ıé");
  });

  it("collapses ECMAScript whitespace while preserving non-whitespace format characters", () => {
    expect(collapseWhitespace("\t Apache\u00a0\nOpenNLP\u2003\ufeff ")).toBe("Apache OpenNLP");
    expect(collapseWhitespace("a\u200bb")).toBe("a\u200bb");
  });

  it("splits words and identifier parts without regular expressions", () => {
    expect(splitWords(" Apache\u202fOpenNLP\nUI ")).toEqual(["Apache", "OpenNLP", "UI"]);
    expect(splitOnCharacters("chunk-group_values", "-_")).toEqual(["chunk", "group", "values"]);
  });

  it("performs bounded replacement, prefix removal, and code-point slicing", () => {
    expect(replaceCharacter("SEARCH_METRIC_DOT_PRODUCT", "_", " ")).toBe("SEARCH METRIC DOT PRODUCT");
    expect(withoutPrefix("STANDARD_SEARCH_PROVIDER_TURBO_QUANT", "STANDARD_SEARCH_PROVIDER_"))
      .toBe("TURBO_QUANT");
    expect(firstCodePoints("😀OpenNLP", 2)).toBe("😀O");
  });

  it("escapes chart tooltip text and formats integers deterministically", () => {
    expect(escapeHtml("<&>\"'😀")).toBe("&lt;&amp;&gt;&quot;&#39;😀");
    expect(formatInteger(1234567)).toBe("1,234,567");
    expect(formatInteger(-1234)).toBe("-1,234");
  });

  it("compares complete Unicode code points without locale collation", () => {
    expect(compareCodePoints("z", "ä")).toBeLessThan(0);
    expect(compareCodePoints("😀a", "😀b")).toBeLessThan(0);
    expect(compareCodePoints("same", "same")).toBe(0);
  });

  it("ellipsizes without splitting supplementary characters", () => {
    expect(ellipsizeCodePoints("A😀BC", 3)).toBe("A😀…");
    expect(ellipsizeCodePoints("A😀B", 3)).toBe("A😀B");
  });

  it("formats ISO and epoch-second timestamps and rejects unreadable ones", () => {
    expect(timestampLabel("2026-08-20T14:05:30Z")).toBe("2026-08-20 14:05 UTC");
    expect(timestampLabel("1755698730")).toBe("2025-08-20 14:05 UTC");
    expect(timestampLabel("")).toBe("");
    expect(timestampLabel("not a date")).toBe("");
  });
});
