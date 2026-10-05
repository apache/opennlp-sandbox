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

import { discoverModelBundles, discoverProfiles } from "../src/discovery";

describe("service discovery", () => {
  it("normalizes profile names from common response envelopes", () => {
    expect(discoverProfiles({ profiles: ["default", { id: "accurate", title: "Accurate" }] })).toEqual([
      { id: "default", label: "default" },
      { id: "accurate", label: "Accurate" },
    ]);
    expect(discoverProfiles({ availableProfileIds: ["fast"] })).toEqual([{ id: "fast", label: "fast" }]);
  });

  it("normalizes model bundle identifiers without losing labels", () => {
    expect(
      discoverModelBundles({
        bundles: ["en-default", { bundleId: "en-news", name: "English news" }],
      }),
    ).toEqual([
      { id: "en-default", label: "en-default" },
      { id: "en-news", label: "English news" },
    ]);
  });

  it("ignores malformed discovery entries", () => {
    expect(discoverProfiles({ profiles: [null, {}, 42, ""] })).toEqual([]);
    expect(discoverModelBundles({ bundles: [{ displayName: "Missing id" }] })).toEqual([]);
  });
});
