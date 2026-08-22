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

import { toBrowserOffset, toBrowserSpan } from "../src/offsets";

describe("shared offset conversion", () => {
  it("maps whole UTF-8 and code-point spans over supplementary characters", () => {
    expect(toBrowserSpan("A😀B", 1, 5, "OFFSET_ENCODING_UTF8_BYTE")).toEqual({ start: 1, end: 3 });
    expect(toBrowserSpan("A😀B", 1, 2, "OFFSET_ENCODING_UNICODE_CODE_POINT"))
      .toEqual({ start: 1, end: 3 });
  });

  it("rejects offsets inside UTF-8 sequences and non-integral code-point offsets", () => {
    expect(toBrowserOffset("A😀B", 2, "OFFSET_ENCODING_UTF8_BYTE")).toBeUndefined();
    expect(toBrowserOffset("A😀B", 1.5, "OFFSET_ENCODING_UNICODE_CODE_POINT")).toBeUndefined();
  });

  it("rejects UTF-16 offsets inside a surrogate pair", () => {
    expect(toBrowserOffset("A😀B", 2, "OFFSET_ENCODING_UTF16_CODE_UNIT")).toBeUndefined();
  });
});
