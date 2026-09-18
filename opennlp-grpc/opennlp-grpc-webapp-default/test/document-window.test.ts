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

import { documentWindow, supportsCompleteGraph } from "../src/document-window";

describe("large document windows", () => {
  it("keeps a short document in one projection", () => {
    expect(documentWindow(5_000, 0)).toEqual({
      start: 0,
      end: 5_000,
      page: 0,
      pageCount: 1,
    });
  });

  it("clamps Alice-sized text to a bounded page selected by its position slider", () => {
    expect(documentWindow(151_064, 99)).toEqual({
      start: 144_000,
      end: 151_064,
      page: 9,
      pageCount: 10,
    });
  });

  it("prevents an unbounded complete graph while retaining balanced overview graphs", () => {
    expect(supportsCompleteGraph(5_000)).toBe(true);
    expect(supportsCompleteGraph(5_001)).toBe(false);
  });
});
