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
  annotationConfidence,
  combinedAnnotationSegments,
  documentAnnotationChips,
  documentScopedAnnotations,
  isDefaultOverlayLayer,
  layerAccent,
  readDocumentShape,
  summarizeDocumentShape,
} from "../src/document-shape";

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

  it("reads entity annotation spans and display text from protobuf JSON", () => {
    const result = readDocumentShape({
      document: {
        rawText: "George Washington visited Paris.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:entities",
          entityValues: { annotations: [{
            annotationSpan: { end: 17, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
            entityType: "person",
            text: "George Washington",
            probability: 0.92,
          }] },
        }] },
      },
    });

    expect(result.layers[0]?.annotations[0]).toMatchObject({
      start: 0,
      end: 17,
      label: "George Washington",
      probability: 0.92,
    });
  });

  it("returns an empty view for malformed responses", () => {
    expect(readDocumentShape(null)).toEqual({ rawText: "", offsetEncoding: "", layers: [] });
    expect(readDocumentShape({ document: { rawText: 42, layers: [] } })).toEqual({
      rawText: "",
      offsetEncoding: "",
      layers: [],
    });
  });

  it("summarizes results and assigns stable layer accents", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "One two.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: {
          layers: [
            { id: "opennlp:tokens", stringValues: { annotations: [{ value: "One" }, { value: "two" }] } },
            { id: "opennlp:entities", entityValues: { annotations: [{ type: "thing" }] } },
          ],
        },
      },
    });

    expect(summarizeDocumentShape(shape)).toEqual({
      layerCount: 2,
      annotationCount: 3,
      offsetEncodingLabel: "UTF-16",
    });
    expect(layerAccent(shape.layers[0]!)).toBe("cyan");
    expect(layerAccent(shape.layers[1]!)).toBe("violet");
  });

  it("formats delimiter-separated layer names and matches ASCII identities without locale casing", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Text",
        layers: { layers: [
          {
            id: "opennlp:CHUNK_group-values",
            identity: { standard: "STANDARD_LAYER_ENTITIES" },
            stringValues: { annotations: [] },
          },
          { id: "custom:CHUNK_group-values", stringValues: { annotations: [] } },
        ] },
      },
    });

    // A declared standard identity names the layer; only an undeclared one falls back to
    // its id, where long all-caps parts are title-cased and short ones (NFC, UD) stay acronyms.
    expect(shape.layers[0]?.title).toBe("Entities");
    expect(layerAccent(shape.layers[0]!)).toBe("violet");
    expect(shape.layers[1]?.title).toBe("Chunk Group Values");
  });

  it("titles layers from their standard identity and qualifier instead of their id", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Text",
        layers: { layers: [
          { id: "opennlp:pos", identity: { standard: "STANDARD_LAYER_POS_TAGS" }, stringValues: { annotations: [] } },
          { id: "opennlp:terms:stem", identity: { standard: "STANDARD_LAYER_TERMS", qualifier: "stem" }, stringValues: { annotations: [] } },
          { id: "opennlp:stems", identity: { standard: "STANDARD_LAYER_STEMS" }, stringValues: { annotations: [] } },
          { id: "opennlp:chunks", identity: { standard: "STANDARD_LAYER_SYNTACTIC_CHUNKS" }, stringValues: { annotations: [] } },
          { id: "opennlp:tv", identity: { standard: "STANDARD_LAYER_TERM_VECTORS" }, stringValues: { annotations: [] } },
          { id: "opennlp:mystery", identity: { standard: "STANDARD_LAYER_UNSPECIFIED" }, stringValues: { annotations: [] } },
        ] },
      },
    });

    expect(shape.layers.map((layer) => layer.title)).toEqual([
      "POS tags", "Terms (stem)", "Stems", "Phrase chunks", "Term vectors", "Mystery",
    ]);
  });

  it("builds one combined projection across overlapping typed layers", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Paris wins.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [
          { id: "opennlp:tokens", stringValues: { annotations: [
            { span: { end: 5 }, value: "Paris" },
            { span: { start: 6, end: 10 }, value: "wins" },
          ] } },
          { id: "opennlp:entities", entityValues: { annotations: [
            { annotationSpan: { end: 5 }, entityType: "location", text: "Paris" },
          ] } },
          { id: "opennlp:analytics", analyticsValues: { annotations: [
            { totalTokens: 2, totalSentences: 1 },
          ] } },
        ] },
      },
    });

    expect(combinedAnnotationSegments(shape)).toMatchObject([
      {
        start: 0,
        end: 5,
        entries: [
          { layer: { id: "opennlp:tokens" }, annotation: { label: "Paris" } },
          { layer: { id: "opennlp:entities" }, annotation: { label: "Paris" } },
        ],
      },
      { start: 6, end: 10, entries: [{ layer: { id: "opennlp:tokens" } }] },
    ]);
    expect(documentScopedAnnotations(shape)).toMatchObject([
      { layer: { id: "opennlp:analytics" }, annotation: { label: "Annotation 1" } },
    ]);
  });

  it("does not present zero-width positional subwords as document annotations", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Good afternoon.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:subwords",
          scope: "LAYER_SCOPE_POSITIONAL",
          identity: { standard: "STANDARD_LAYER_SUBWORDS" },
          subwordValues: { annotations: [{
            span: { start: 0, end: 0 },
            piece: "▁",
            vocabularyId: 4,
          }] },
        }] },
      },
    });

    expect(shape.layers[0]?.annotations[0]).toMatchObject({
      start: 0,
      end: 0,
      label: "▁",
    });
    expect(documentScopedAnnotations(shape)).toEqual([]);
  });

  it("builds thirty thousand annotation segments within a bounded time", () => {
    const annotationCount = 30_000;
    const rawText = "x ".repeat(annotationCount);
    const annotations = Array.from({ length: annotationCount }, (_, index) => ({
      start: index * 2,
      end: index * 2 + 1,
      label: `token-${index}`,
      source: {},
    }));
    const started = performance.now();

    const segments = combinedAnnotationSegments({
      rawText,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [{
        id: "opennlp:tokens",
        title: "Tokens",
        scope: "LAYER_SCOPE_POSITIONAL",
        valueType: "String",
        annotations,
      }],
    });

    expect(segments).toHaveLength(annotationCount);
    expect(performance.now() - started).toBeLessThan(1_000);
  }, 2_000);

  it("collapses a document-scoped category layer to its most probable chip", () => {
    const sentiment = {
      id: "opennlp:sentiment",
      title: "Sentiment",
      scope: "LAYER_SCOPE_DOCUMENT",
      valueType: "Category",
      annotations: [
        { label: "1 star", probability: 0.05, source: {} },
        { label: "4 stars", probability: 0.62, source: {} },
        { label: "5 stars", probability: 0.21, source: {} },
        { label: "3 stars", probability: 0.08, source: {} },
        { label: "2 stars", probability: 0.04, source: {} },
      ],
    };
    const language = {
      id: "opennlp:language",
      title: "Language",
      scope: "LAYER_SCOPE_DOCUMENT",
      valueType: "String",
      annotations: [
        { label: "eng", source: {} },
        { label: "deu", source: {} },
      ],
    };
    const chips = documentAnnotationChips({
      rawText: "Lovely.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: [sentiment, language],
    });

    expect(chips).toHaveLength(3);
    expect(chips[0]).toMatchObject({
      annotation: { label: "4 stars" },
      totalCount: 5,
    });
    // Non-category layers keep one chip per document-scoped annotation.
    expect(chips[1]).toMatchObject({ annotation: { label: "eng" }, totalCount: 1 });
    expect(chips[2]).toMatchObject({ annotation: { label: "deu" }, totalCount: 1 });
  });

  it("keeps only entity and sentence layers in the calm first-run overlay", () => {
    const layer = (id: string, standardIdentity?: string) => ({
      id,
      title: id,
      scope: "LAYER_SCOPE_POSITIONAL",
      valueType: "String",
      standardIdentity,
      annotations: [],
    });

    expect(isDefaultOverlayLayer(layer("custom:names", "STANDARD_LAYER_ENTITIES"))).toBe(true);
    expect(isDefaultOverlayLayer(layer("opennlp:sentences"))).toBe(true);
    expect(isDefaultOverlayLayer(layer("opennlp:tokens", "STANDARD_LAYER_TOKENS"))).toBe(false);
    expect(isDefaultOverlayLayer(layer("opennlp:pos-tags"))).toBe(false);
  });

  it("ranks annotation confidence as probability first, then score, then zero", () => {
    expect(annotationConfidence({ label: "a", probability: 0.4, score: 0.9, source: {} })).toBe(0.4);
    expect(annotationConfidence({ label: "a", score: 0.9, source: {} })).toBe(0.9);
    expect(annotationConfidence({ label: "a", source: {} })).toBe(0);
  });
});
