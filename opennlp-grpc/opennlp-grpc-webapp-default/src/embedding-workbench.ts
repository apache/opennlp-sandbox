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

import type { DocumentShapeView } from "./document-shape";

export interface EmbeddingVector {
  modelId: string;
  granularity: string;
  vector: number[];
  start?: number;
  end?: number;
}

export interface SessionDocument {
  id: string;
  title: string;
  shape: DocumentShapeView;
  vectors: EmbeddingVector[];
  json: string;
}

export interface SearchHit {
  document: SessionDocument;
  modelId: string;
  score: number;
}

export type VectorQuery = Pick<EmbeddingVector, "modelId" | "vector">;

export function readEmbeddingVectors(shape: DocumentShapeView): EmbeddingVector[] {
  return shape.layers
    .filter((layer) => layer.valueType === "Embedding")
    .flatMap((layer) => layer.annotations)
    .flatMap((annotation) => {
      const modelId = stringValue(annotation.source.modelId);
      const vector = numericVector(annotation.source.vector);
      if (!modelId || !vector) {
        return [];
      }
      return [{
        modelId,
        granularity: stringValue(annotation.source.granularity) || inferGranularity(annotation.start, annotation.end),
        vector,
        start: annotation.start,
        end: annotation.end,
      }];
    });
}

export function representativeVectors(shape: DocumentShapeView): EmbeddingVector[] {
  const byModel = new Map<string, EmbeddingVector[]>();
  for (const embedding of readEmbeddingVectors(shape)) {
    const embeddings = byModel.get(embedding.modelId) ?? [];
    embeddings.push(embedding);
    byModel.set(embedding.modelId, embeddings);
  }
  return [...byModel.entries()].flatMap(([, embeddings]) => {
    const documentVector = embeddings.find((embedding) =>
      embedding.granularity.toUpperCase().includes("DOCUMENT")
      || (embedding.start === undefined && embedding.end === undefined));
    if (documentVector) {
      return [documentVector];
    }
    const mean = meanVector(embeddings.map((embedding) => embedding.vector));
    return mean ? [{ ...embeddings[0]!, granularity: "AGGREGATED", vector: mean }] : [];
  });
}

export function cosineSimilarity(left: number[], right: number[]): number | undefined {
  if (left.length === 0 || left.length !== right.length) {
    return undefined;
  }
  let dotProduct = 0;
  let leftMagnitude = 0;
  let rightMagnitude = 0;
  for (let index = 0; index < left.length; index++) {
    const leftValue = left[index]!;
    const rightValue = right[index]!;
    if (!Number.isFinite(leftValue) || !Number.isFinite(rightValue)) {
      return undefined;
    }
    dotProduct += leftValue * rightValue;
    leftMagnitude += leftValue * leftValue;
    rightMagnitude += rightValue * rightValue;
  }
  if (leftMagnitude === 0 || rightMagnitude === 0) {
    return undefined;
  }
  return dotProduct / Math.sqrt(leftMagnitude * rightMagnitude);
}

export class SessionVectorIndex {
  readonly #documents = new Map<string, SessionDocument>();

  get size(): number {
    return this.#documents.size;
  }

  add(id: string, title: string, shape: DocumentShapeView, json = ""): boolean {
    const vectors = representativeVectors(shape);
    if (!id.trim() || vectors.length === 0) {
      return false;
    }
    this.#documents.set(id, { id, title: title.trim() || id, shape, vectors, json });
    return true;
  }

  clear(): void {
    this.#documents.clear();
  }

  search(query: DocumentShapeView, limit = 10): SearchHit[] {
    const queryVectors = representativeVectors(query);
    return [...this.#documents.values()]
      .flatMap((document) => bestMatch(document, queryVectors))
      .sort((left, right) => right.score - left.score || left.document.id.localeCompare(right.document.id))
      .slice(0, Math.max(0, limit));
  }
}

function bestMatch(document: SessionDocument, queries: EmbeddingVector[]): SearchHit[] {
  let best: SearchHit | undefined;
  for (const candidate of document.vectors) {
    for (const query of queries) {
      if (candidate.modelId !== query.modelId) {
        continue;
      }
      const score = cosineSimilarity(candidate.vector, query.vector);
      if (score !== undefined && (!best || score > best.score)) {
        best = { document, modelId: candidate.modelId, score };
      }
    }
  }
  return best ? [best] : [];
}

function meanVector(vectors: number[][]): number[] | undefined {
  const dimensions = vectors[0]?.length ?? 0;
  if (dimensions === 0 || vectors.some((vector) => vector.length !== dimensions)) {
    return undefined;
  }
  return Array.from({ length: dimensions }, (_, dimension) =>
    vectors.reduce((sum, vector) => sum + vector[dimension]!, 0) / vectors.length);
}

function numericVector(value: unknown): number[] | undefined {
  if (!Array.isArray(value) || value.length === 0
      || value.some((entry) => typeof entry !== "number" || !Number.isFinite(entry))
      || value.every((entry) => entry === 0)) {
    return undefined;
  }
  return value as number[];
}

function inferGranularity(start: number | undefined, end: number | undefined): string {
  return start === undefined && end === undefined ? "DOCUMENT" : "POSITIONAL";
}

function stringValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
