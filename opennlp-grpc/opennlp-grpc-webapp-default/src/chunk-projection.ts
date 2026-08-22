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

export interface ChunkProjectionItem {
  index: number;
  start: number;
  end: number;
  text: string;
  embeddingCount: number;
  embeddings: ChunkProjectionEmbedding[];
}

export interface ChunkProjectionEmbedding {
  modelId: string;
  granularity: string;
  vector: number[];
}

export interface ChunkProjectionGroup {
  id: string;
  title: string;
  strategy: string;
  embeddingModelIds: string[];
  chunks: ChunkProjectionItem[];
}

const STRATEGY_NAMES: Readonly<Record<string, string>> = {
  STANDARD_CHUNKING_STRATEGY_SENTENCE: "Sentence",
  STANDARD_CHUNKING_STRATEGY_TOKEN: "Token window",
  STANDARD_CHUNKING_STRATEGY_SEMANTIC: "Semantic",
  STANDARD_CHUNKING_STRATEGY_CATEGORY: "Category",
};

export function readChunkProjection(value: unknown): ChunkProjectionGroup[] {
  const response = record(value);
  const documentValue = record(response?.document);
  const result: ChunkProjectionGroup[] = [];
  for (const candidate of array(documentValue?.chunkEmbeddingGroups)) {
    const group = record(candidate);
    const id = text(group?.groupId);
    const strategyValue = record(group?.strategy);
    const strategyId = text(strategyValue?.standard);
    if (!group || !id || !strategyId) {
      continue;
    }
    const chunks: ChunkProjectionItem[] = [];
    for (const chunkValue of array(group.chunks)) {
      const chunk = record(chunkValue);
      const span = record(chunk?.annotationSpan);
      const content = text(chunk?.textContent);
      const start = wholeNumber(span?.start, 0);
      const end = wholeNumber(span?.end, -1);
      if (!chunk || !span || !content || start < 0 || end < start) {
        continue;
      }
      chunks.push({
        index: chunks.length + 1,
        start,
        end,
        text: content,
        embeddingCount: array(chunk.embeddings).length,
        embeddings: array(chunk.embeddings).flatMap((value) => {
          const embedding = record(value);
          if (!embedding) {
            return [];
          }
          const route = record(embedding.route);
          return [{
            modelId: text(embedding.modelId) || text(route?.modelId) || "Unidentified model",
            granularity: text(embedding.granularity),
            vector: numberVector(embedding.vector),
          }];
        }),
      });
    }
    result.push({
      id,
      title: text(group.resultSetName) || id,
      strategy: STRATEGY_NAMES[strategyId] ?? strategyId,
      embeddingModelIds: array(group.embeddingModelIds).map(text).filter(nonEmpty),
      chunks,
    });
  }
  return result;
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function wholeNumber(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isSafeInteger(value) ? value : fallback;
}

function nonEmpty(value: string): boolean {
  return value.length > 0;
}

function numberVector(value: unknown): number[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const result: number[] = [];
  for (const entry of value) {
    if (typeof entry !== "number" || !Number.isFinite(entry)) {
      return [];
    }
    result.push(entry);
  }
  return result;
}
