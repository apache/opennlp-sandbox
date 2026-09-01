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
import {
  buildDependencyGraph,
  buildDocumentGraph,
  buildEntityRelationGraph,
  buildHeatmapRows,
  buildSimilarityHeatmapRows,
} from "../src/visualization-data";

const shape = readDocumentShape({
  document: {
    rawText: "OpenNLP works. Search is useful.",
    offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    layers: {
      layers: [
        {
          id: "opennlp:sentence-embeddings",
          embeddingValues: {
            annotations: [
              { span: { start: 0, end: 14 }, modelId: "tiny-test", vector: [1, 0] },
              { span: { start: 15, end: 32 }, modelId: "tiny-test", vector: [0, 1] },
            ],
          },
        },
        {
          id: "opennlp:sentiment",
          categoryValues: {
            annotations: [
              { span: { start: 0, end: 14 }, label: "positive", score: 0.8 },
              { span: { start: 15, end: 24 }, label: "negative", score: 0.7 },
              { span: { start: 25, end: 32 }, label: "neutral", score: 0.1 },
            ],
          },
        },
      ],
    },
  },
});

/** A document whose only layer is a sentiment layer carrying the given category annotations. */
function sentimentShape(annotations: Array<{ span: { start: number; end: number }; label: string; score: number }>) {
  return readDocumentShape({
    document: {
      rawText: "OpenNLP works. Search is useful.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: { layers: [{ id: "opennlp:sentiment", categoryValues: { annotations } }] },
    },
  });
}

describe("visualization data", () => {
  it("leaves semantic ranking to the server and reads typed sentiment rows", () => {
    const rows = buildHeatmapRows(shape);

    expect(rows.semantic).toEqual([]);
    expect(rows.sentiment).toMatchObject([
      { start: 0, end: 14, score: 0.8, category: "positive" },
      { start: 15, end: 24, score: -0.7, category: "negative" },
      { start: 25, end: 32, score: 0, category: "neutral" },
    ]);
  });

  it("reads ordinal star labels as signed polarity instead of raw confidence", () => {
    const rows = buildHeatmapRows(sentimentShape([
      { span: { start: 0, end: 7 }, label: "1_star", score: 0.884 },
      { span: { start: 8, end: 14 }, label: "3 stars", score: 0.6 },
      { span: { start: 15, end: 24 }, label: "5_stars", score: 0.89 },
      { span: { start: 25, end: 32 }, label: "2-stars", score: 0.5 },
    ])).sentiment;

    expect(rows.map((row) => row.score)).toEqual([-0.884, 0, 0.89, -0.25]);
    expect(rows.map((row) => row.category)).toEqual(["1_star", "3 stars", "5_stars", "2-stars"]);
  });

  it("scores a label of unknown shape as neutral rather than trusting its confidence", () => {
    const rows = buildHeatmapRows(sentimentShape([
      { span: { start: 0, end: 14 }, label: "LABEL_0", score: 0.97 },
      { span: { start: 15, end: 32 }, label: "6_stars", score: 0.97 },
    ])).sentiment;

    expect(rows.map((row) => row.score)).toEqual([0, 0]);
  });

  it("maps only server-ranked chunks from the current document into the shared heatmap", () => {
    const rows = buildSimilarityHeatmapRows(shape.rawText, [
      {
        sourceText: shape.rawText,
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        start: 0,
        end: 14,
        indexedChunkText: "OpenNLP works.",
        score: 0.91,
        modelId: "tiny-test",
      },
      {
        sourceText: "Another document",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        start: 0,
        end: 7,
        indexedChunkText: "Another",
        score: 0.99,
        modelId: "tiny-test",
      },
    ]);

    expect(rows).toEqual([{
      start: 0,
      end: 14,
      label: "OpenNLP works.",
      score: 0.91,
      modelId: "tiny-test",
    }]);
  });

  it("builds a bounded graph that preserves layer and annotation references", () => {
    const graph = buildDocumentGraph(shape, 3);

    expect(graph.nodes[0]).toMatchObject({ id: "document", kind: "document" });
    expect(graph.nodes.filter((node) => node.kind === "layer")).toHaveLength(2);
    expect(graph.nodes.filter((node) => node.kind === "annotation")).toHaveLength(3);
    expect(graph.links).toContainEqual({ source: "document", target: "layer:opennlp:sentiment" });
    expect(graph.truncated).toBe(true);
  });

  it("shares a bounded graph budget across every returned layer", () => {
    const graph = buildDocumentGraph(shape, 2);

    expect(graph.nodes.filter((node) => node.kind === "annotation").map((node) => node.layerId))
      .toEqual(["opennlp:sentence-embeddings", "opennlp:sentiment"]);
    expect(graph.truncated).toBe(true);
  });

  it("can project every annotation when the caller requests a complete graph", () => {
    const graph = buildDocumentGraph(shape, 5);

    expect(graph.nodes.filter((node) => node.kind === "annotation")).toHaveLength(5);
    expect(graph.truncated).toBe(false);
  });

  it("builds labeled dependency arcs over token nodes", () => {
    const graph = buildDependencyGraph(readDocumentShape({ document: {
      rawText: "Acme acquired Bolt",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: { layers: [
        { id: "opennlp:tokens", identity: { standard: "STANDARD_LAYER_TOKENS" },
          stringValues: { annotations: [
            { span: { start: 0, end: 4 }, value: "Acme" },
            { span: { start: 5, end: 13 }, value: "acquired" },
            { span: { start: 14, end: 18 }, value: "Bolt" },
          ] } },
        { id: "opennlp:dependencies", identity: { standard: "STANDARD_LAYER_DEPENDENCIES" },
          dependencyValues: { annotations: [
            { span: { start: 0, end: 4 }, headTokenIndex: 1, dependentTokenIndex: 0, relation: "nsubj" },
            { span: { start: 5, end: 13 }, headTokenIndex: -1, dependentTokenIndex: 1, relation: "root" },
            { span: { start: 14, end: 18 }, headTokenIndex: 1, dependentTokenIndex: 2, relation: "obj" },
          ] } },
      ] },
    } }));

    expect(graph.nodes.map((node) => node.label)).toEqual(["ROOT", "Acme", "acquired", "Bolt"]);
    expect(graph.links).toMatchObject([
      { source: "token:1", target: "token:0", label: "nsubj" },
      { source: "dependency-root", target: "token:1", label: "root" },
      { source: "token:1", target: "token:2", label: "obj" },
    ]);
    expect(graph.nodes[1]).toMatchObject({ layerId: "opennlp:tokens", annotationIndex: 0 });
    expect(graph.links[0]).toMatchObject({
      layerId: "opennlp:dependencies", annotationIndex: 0,
    });
  });

  it("builds an entity network with typed relation edges", () => {
    const graph = buildEntityRelationGraph(readDocumentShape({ document: {
      rawText: "Acme acquired Bolt",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      layers: { layers: [
        { id: "opennlp:entities", identity: { standard: "STANDARD_LAYER_ENTITIES" },
          entityValues: { annotations: [
            { annotationSpan: { start: 0, end: 4 }, text: "Acme", entityType: "organization" },
            { annotationSpan: { start: 14, end: 18 }, text: "Bolt", entityType: "organization" },
          ] } },
        { id: "opennlp:relations", identity: { standard: "STANDARD_LAYER_RELATIONS" },
          relationValues: { annotations: [{
            span: { start: 0, end: 18 }, type: "acquisition",
            subjectEntityIndex: 0, objectEntityIndex: 1,
          }] } },
      ] },
    } }));

    expect(graph.nodes).toMatchObject([
      { label: "Acme", category: "organization", layerId: "opennlp:entities", annotationIndex: 0 },
      { label: "Bolt", category: "organization", layerId: "opennlp:entities", annotationIndex: 1 },
    ]);
    expect(graph.links).toMatchObject([{
      source: "entity:0", target: "entity:1", label: "acquisition",
      layerId: "opennlp:relations", annotationIndex: 0,
    }]);
  });
});
