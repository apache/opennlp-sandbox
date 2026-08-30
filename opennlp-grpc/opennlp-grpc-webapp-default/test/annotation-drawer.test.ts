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

/** @vitest-environment jsdom */

import { beforeEach, describe, expect, it, vi } from "vitest";

import { AnnotationDrawer } from "../src/annotation-drawer";
import { readDocumentShape } from "../src/document-shape";

describe("annotation drawer", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="annotation-drawer-backdrop" hidden></div>
      <aside id="annotation-details" hidden>
        <button id="annotation-details-close" type="button">Close</button>
        <div id="annotation-details-content"></div>
      </aside>`;
  });

  it("shows every typed annotation covering a combined text segment", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Paris",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [
          { id: "opennlp:tokens", stringValues: { annotations: [{ span: { end: 5 }, value: "Paris" }] } },
          { id: "opennlp:entities", entityValues: { annotations: [{
            annotationSpan: { end: 5 }, entityType: "location", text: "Paris",
          }] } },
        ] },
      },
    });
    const entries = shape.layers.flatMap((layer) => layer.annotations.map((annotation) => ({ layer, annotation })));
    const drawer = new AnnotationDrawer();

    drawer.showAnnotations("Paris", 0, 5, entries);

    const panel = document.getElementById("annotation-details")!;
    expect(panel.hidden).toBe(false);
    expect(panel.textContent).toContain("2 annotations");
    expect(panel.textContent).toContain("opennlp:tokens");
    expect(panel.textContent).toContain("opennlp:entities");
    expect(panel.textContent).toContain("location");
  });

  it("does not repeat a covering sentence vector in each combined word popover", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Alice",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [
          { id: "opennlp:tokens", stringValues: { annotations: [{
            span: { end: 5 }, value: "Alice",
          }] } },
          { id: "opennlp:embeddings", embeddingValues: { annotations: [{
            span: { end: 20 },
            modelId: "legal-mini",
            granularity: "EMBEDDING_GRANULARITY_SENTENCE",
            vector: [0.1, 0.2, 0.3, 0.4],
          }] } },
        ] },
      },
    });
    const entries = shape.layers.flatMap((layer) => layer.annotations.map((annotation) => ({ layer, annotation })));
    const drawer = new AnnotationDrawer();

    drawer.showAnnotations("Alice", 0, 5, entries);

    const content = document.getElementById("annotation-details-content")!;
    expect(content.textContent).toContain("1 annotation");
    expect(content.textContent).toContain("opennlp:tokens");
    expect(content.textContent).not.toContain("legal-mini");
  });

  it("summarizes and copies the complete vector for a selected chunk", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    const drawer = new AnnotationDrawer();
    const trigger = document.createElement("button");

    drawer.showChunk({
      id: "sentence-chunks",
      title: "Sentence chunks",
      strategy: "Sentence",
      embeddingModelIds: ["legal-mini"],
      chunks: [],
    }, {
      index: 1,
      start: 0,
      end: 14,
      text: "We the People.",
      embeddingCount: 1,
      embeddings: [{
        modelId: "legal-mini",
        granularity: "EMBEDDING_GRANULARITY_CHUNK_LEVEL",
        vector: [0.125, -0.5, 0.75, 0.25],
      }],
    }, trigger);

    const content = document.getElementById("annotation-details-content")!;
    expect(content.textContent).toContain("4 dimensions");
    expect(content.textContent).toContain("0.125000, -0.500000, 0.750000");
    expect(content.textContent).not.toContain("0.250000");
    const copy = Array.from(content.querySelectorAll("button"))
      .find((button) => button.textContent === "Copy vector")!;
    copy.click();
    await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith("[0.125,-0.5,0.75,0.25]"));
  });

  it("restores the copy button label after the copied confirmation", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    vi.useFakeTimers();
    try {
      const drawer = new AnnotationDrawer();
      drawer.showChunk({
        id: "sentence-chunks",
        title: "Sentence chunks",
        strategy: "Sentence",
        embeddingModelIds: ["legal-mini"],
        chunks: [],
      }, {
        index: 1,
        start: 0,
        end: 14,
        text: "We the People.",
        embeddingCount: 1,
        embeddings: [{
          modelId: "legal-mini",
          granularity: "EMBEDDING_GRANULARITY_CHUNK_LEVEL",
          vector: [0.125, -0.5],
        }],
      }, document.createElement("button"));
      const copy = Array.from(document.querySelectorAll("button"))
        .find((button) => button.textContent === "Copy vector")!;

      copy.click();
      await Promise.resolve();
      await Promise.resolve();

      expect(copy.textContent).toBe("Copied");
      vi.advanceTimersByTime(1500);
      expect(copy.textContent).toBe("Copy vector");
    } finally {
      vi.useRealTimers();
    }
  });

  it("lists the engines that recognized an entity", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Kansas City",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:entities",
          entityValues: { annotations: [{
            annotationSpan: { end: 11 },
            entityType: "city",
            sources: [
              { recognizerId: "city", engine: "dictionary" },
              { recognizerId: "city", engine: "onnx", probability: 0.91 },
            ],
          }] },
        }] },
      },
    });
    const layer = shape.layers[0]!;
    const drawer = new AnnotationDrawer();

    drawer.showAnnotation(layer, layer.annotations[0]!);

    const content = document.getElementById("annotation-details-content")!;
    expect(content.textContent).toContain("Recognized by");
    expect(content.textContent).toContain("city (dictionary)");
    expect(content.textContent).toContain("city (onnx)");
  });

  it("renders annotation JSON as compact structured fields", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Paris",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:entities",
          entityValues: { annotations: [{
            annotationSpan: { start: 0, end: 5 },
            entityType: "location",
            text: "Paris",
            sources: [{ recognizerId: "location", engine: "onnx", probability: 0.91 }],
          }] },
        }] },
      },
    });
    const layer = shape.layers[0]!;
    const drawer = new AnnotationDrawer();

    drawer.showAnnotation(layer, layer.annotations[0]!);

    const content = document.getElementById("annotation-details-content")!;
    const value = content.querySelector(".structured-value")!;
    expect(value).not.toBeNull();
    expect(value.querySelectorAll(":scope > dl > dt")).toHaveLength(4);
    expect(value.textContent).toContain("annotationSpan");
    expect(value.textContent).toContain("start: 0");
    expect(value.textContent).toContain("end: 5");
    expect(value.textContent).toContain("1 item");
    expect(content.querySelector("pre")).toBeNull();
  });

  it("summarizes a vector nested in JSON and copies the complete vector", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });
    const drawer = new AnnotationDrawer();

    drawer.showAnnotation({
      id: "custom:vector-result",
      title: "Vector result",
      scope: "LAYER_SCOPE_DOCUMENT",
      valueType: "Analytics",
      annotations: [],
    }, {
      label: "vector result",
      source: {
        method: "centroid",
        vector: [0.125, -0.5, 0.75, 0.25],
      },
    });

    const content = document.getElementById("annotation-details-content")!;
    const summary = content.querySelector(".structured-vector")!;
    expect(summary.textContent).toContain("4 values");
    expect(summary.textContent).toContain("0.125, -0.5, 0.75");
    expect(summary.textContent).not.toContain("0.25");
    const copy = Array.from(summary.querySelectorAll("button"))
      .find((button) => button.textContent === "Copy vector")!;
    copy.click();
    await vi.waitFor(() => expect(writeText).toHaveBeenCalledWith("[0.125,-0.5,0.75,0.25]"));
  });

  it("summarizes a sentence embedding instead of dumping its raw vector", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "We the People.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:embeddings",
          embeddingValues: { annotations: [{
            span: { end: 14 },
            modelId: "legal-mini",
            granularity: "EMBEDDING_GRANULARITY_SENTENCE",
            vector: [0.125, -0.5, 0.75, 0.25],
          }] },
        }] },
      },
    });
    const layer = shape.layers[0]!;
    const drawer = new AnnotationDrawer();

    drawer.showAnnotation(layer, layer.annotations[0]!);

    const content = document.getElementById("annotation-details-content")!;
    expect(content.textContent).toContain("Sentence");
    expect(content.textContent).toContain("4 dimensions");
    expect(content.textContent).toContain("0.125000, -0.500000, 0.750000");
    expect(content.querySelector("pre")).toBeNull();
  });

  it("shows a search chunk's score, provenance, and all intersecting typed annotations", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Paris is lovely.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [{
          id: "opennlp:entities",
          entityValues: { annotations: [{
            annotationSpan: { start: 0, end: 5 },
            entityType: "location",
            text: "Paris",
          }] },
        }, {
          id: "opennlp:pos",
          wordTypeValues: { annotations: [{
            annotationSpan: { start: 0, end: 5 },
            word: "Paris",
            tag: "NNP",
          }] },
        }] },
      },
    });
    const drawer = new AnnotationDrawer();
    const trigger = document.createElement("button");

    drawer.showSearchHit({
      id: "paris/paris:0:0",
      documentId: "paris",
      chunkId: "paris:0:0",
      chunkGroupId: "sentence-chunks",
      score: 0.9375,
      sourceDocument: { docId: "paris", rawText: "Paris is lovely." },
      sourceText: "Paris is lovely.",
      start: 0,
      end: 16,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      indexedChunkText: "Paris is lovely.",
      modelId: "legal-mini",
      backendId: "static",
      vectorSpaceId: "legal-mini-space",
      providerId: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
      indexId: "current-document",
      corpusTitle: "Current document",
      provenance: "Analyzed in this browser session",
      licenseName: "CC0-1.0",
      build: { bundleArtifactHash: "sha256:abcd" },
      matchedSpans: [],
    }, shape, trigger);

    const content = document.getElementById("annotation-details-content")!;
    expect(content.textContent).toContain("Cosine score");
    expect(content.textContent).toContain("0.9375");
    expect(content.textContent).toContain("sentence-chunks");
    expect(content.textContent).toContain("legal-mini-space");
    expect(content.textContent).toContain("CC0-1.0");
    expect(content.textContent).toContain("opennlp:entities");
    expect(content.textContent).toContain("location");
    expect(content.textContent).toContain("opennlp:pos");
    expect(content.textContent).toContain("NNP");
  });
});
