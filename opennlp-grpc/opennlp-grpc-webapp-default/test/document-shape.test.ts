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

import { readDocumentShape } from "../src/document-shape";

describe("document shape reader", () => {
  it("reads typed annotation layers without falling back to legacy fields", () => {
    const result = readDocumentShape({
      document: {
        rawText: "OpenNLP works.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: {
          layers: [
            {
              id: "opennlp:tokens",
              scope: "LAYER_SCOPE_POSITIONAL",
              identity: { standard: "STANDARD_LAYER_TOKENS" },
              stringValues: {
                annotations: [
                  { span: { start: 0, end: 7 }, value: "OpenNLP", probability: 0.99 },
                  { span: { start: 8, end: 13 }, value: "works" },
                ],
              },
            },
          ],
        },
      },
    });

    expect(result.rawText).toBe("OpenNLP works.");
    expect(result.layers).toHaveLength(1);
    expect(result.layers[0]).toMatchObject({
      id: "opennlp:tokens",
      title: "Tokens",
      valueType: "String",
      standardIdentity: "STANDARD_LAYER_TOKENS",
    });
    expect(result.layers[0]?.annotations[0]).toMatchObject({
      start: 0,
      end: 7,
      label: "OpenNLP",
      probability: 0.99,
    });
  });

  it("converts UTF-8 byte and Unicode code-point spans to browser string offsets", () => {
    const response = (offsetEncoding: string, start: number, end: number) => ({
      document: {
        rawText: "A😀 café",
        offsetEncoding,
        layers: {
          layers: [
            {
              id: "example:selection",
              scope: "LAYER_SCOPE_POSITIONAL",
              identity: { custom: "example:selection" },
              stringValues: { annotations: [{ span: { start, end }, value: "😀" }] },
            },
          ],
        },
      },
    });

    expect(readDocumentShape(response("OFFSET_ENCODING_UTF8_BYTE", 1, 5)).layers[0]?.annotations[0])
      .toMatchObject({ start: 1, end: 3 });
    expect(readDocumentShape(response("OFFSET_ENCODING_UNICODE_CODE_POINT", 1, 2)).layers[0]?.annotations[0])
      .toMatchObject({ start: 1, end: 3 });
  });

  it("summarizes category and embedding value types", () => {
    const result = readDocumentShape({
      document: {
        rawText: "A sentence.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: {
          layers: [
            {
              id: "opennlp:sentiment",
              categoryValues: { annotations: [{ span: { start: 0, end: 11 }, label: "positive", score: 0.8 }] },
            },
            {
              id: "opennlp:embeddings",
              embeddingValues: {
                annotations: [{ span: { start: 0, end: 11 }, modelId: "mini", vector: [0.1, -0.2, 0.3] }],
              },
            },
          ],
        },
      },
    });

    expect(result.layers[0]?.annotations[0]).toMatchObject({ label: "positive", score: 0.8 });
    expect(result.layers[1]?.annotations[0]).toMatchObject({ label: "mini (3 dimensions)" });
  });

  it("returns an empty view for malformed responses", () => {
    expect(readDocumentShape(null)).toEqual({ rawText: "", offsetEncoding: "", layers: [] });
    expect(readDocumentShape({ document: { rawText: 42, layers: [] } })).toEqual({
      rawText: "",
      offsetEncoding: "",
      layers: [],
    });
  });
});
