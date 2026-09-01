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

import { jsonPresentation, LARGE_RESPONSE_MESSAGE } from "../src/json-response";

describe("protobuf JSON presentation", () => {
  it("formats a small response for the inline JSON tab", () => {
    expect(jsonPresentation({ document: { rawText: "Hello" } }, 12, 3)).toEqual({
      inline: true,
      text: '{\n  "document": {\n    "rawText": "Hello"\n  }\n}',
    });
  });

  it("does not serialize a large response until the user explicitly requests it", () => {
    const response = { document: { rawText: "Alice", layers: [{ annotations: [1] }] } };

    expect(jsonPresentation(response, 151_064, 527_430)).toEqual({
      inline: false,
      text: LARGE_RESPONSE_MESSAGE,
    });
  });
});
