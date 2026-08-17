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

import { toBrowserSpan } from "./offsets";

/** Stable browser view of one server-configured immutable search index. */
export interface SearchIndexBuild {
  bundleFormatVersion?: number;
  bundleArtifactHash?: string;
  builderId?: string;
  builderVersion?: string;
  preparationConfigHash?: string;
}

export interface SearchIndex {
  id: string;
  label: string;
  providerId: string;
  modelId: string;
  backendId: string;
  vectorSpaceId: string;
  artifactHash?: string;
  dimension?: number;
  metric: string;
  size?: number;
  maxTopK?: number;
  maxQueryBytes?: number;
  maxResponseBytes?: number;
  immutable: boolean;
  corpusTitle: string;
  provenance: string;
  sourceUri?: string;
  licenseName?: string;
  licenseUri?: string;
  corpusArtifactHash?: string;
  build: SearchIndexBuild;
}

export interface SearchEmbeddingRoute {
  modelId: string;
  backendId: string;
  vectorSpaceId: string;
  artifactHash?: string;
}

/** Stable browser view of one source-mapped search hit. */
export interface SearchHit {
  id: string;
  documentId: string;
  chunkId: string;
  score: number;
  sourceDocument: Record<string, unknown>;
  sourceText: string;
  start: number;
  end: number;
  offsetEncoding: string;
  emittedChunkText: string;
  modelId: string;
  backendId: string;
  vectorSpaceId: string;
  providerId: string;
  artifactHash?: string;
  indexId: string;
  corpusTitle: string;
  provenance: string;
  sourceUri?: string;
  licenseName?: string;
  licenseUri?: string;
  corpusArtifactHash?: string;
  build: SearchIndexBuild;
  queryEmbeddingRoute: SearchEmbeddingRoute;
}

export interface SearchResponse {
  hits: SearchHit[];
  truncated: boolean;
}

/** Request body for `POST /api/v1/search`. */
export interface SearchRequest {
  indexId: string;
  query: {
    docId?: string;
    rawText: string;
  };
  topK: number;
}

export function createSearchRequest(indexId: string, query: string, topK: number): SearchRequest {
  return { indexId, query: { rawText: query }, topK };
}

export function readSearchIndexes(response: unknown): SearchIndex[] {
  const envelope = record(response);
  const indexes = Array.isArray(envelope?.indexes) ? envelope.indexes : [];
  return indexes.flatMap((value) => {
    const descriptor = record(value);
    const id = text(descriptor?.indexId);
    if (!descriptor || !id) {
      return [];
    }
    const provider = record(descriptor.provider);
    const route = record(descriptor.embeddingRoute);
    const corpus = record(descriptor.corpus);
    const build = record(descriptor.build);
    const providerId = providerIdentity(provider);
    const embeddingRoute = readEmbeddingRoute(route);
    const metric = text(descriptor.metric);
    if (!providerId || !embeddingRoute || descriptor.immutable !== true
        || metric !== "SEARCH_METRIC_COSINE") {
      return [];
    }
    return [{
      id,
      label: text(descriptor.displayName) || id,
      providerId,
      modelId: embeddingRoute.modelId,
      backendId: embeddingRoute.backendId,
      vectorSpaceId: embeddingRoute.vectorSpaceId,
      artifactHash: embeddingRoute.artifactHash,
      dimension: nonNegativeInteger(descriptor.dimension),
      metric,
      size: nonNegativeInteger(descriptor.size),
      maxTopK: positiveInteger(descriptor.maxTopK),
      maxQueryBytes: positiveInteger(descriptor.maxQueryBytes),
      maxResponseBytes: positiveInteger(descriptor.maxResponseBytes),
      immutable: descriptor.immutable === true,
      corpusTitle: text(corpus?.title) || "Untitled corpus",
      provenance: text(corpus?.provenanceSummary),
      sourceUri: safeUri(corpus?.sourceUri),
      licenseName: optionalText(corpus?.licenseName),
      licenseUri: safeUri(corpus?.licenseUri),
      corpusArtifactHash: optionalText(corpus?.artifactHash),
      build: {
        bundleFormatVersion: positiveInteger(build?.bundleFormatVersion),
        bundleArtifactHash: optionalText(build?.bundleArtifactHash),
        builderId: optionalText(build?.builderId),
        builderVersion: optionalText(build?.builderVersion),
        preparationConfigHash: optionalText(build?.preparationConfigHash),
      },
    }];
  });
}

export function readSearchResponse(response: unknown): SearchResponse {
  const envelope = record(response);
  const index = readSearchIndexes({ indexes: envelope?.index ? [envelope.index] : [] })[0];
  const queryEmbeddingRoute = readEmbeddingRoute(record(envelope?.queryEmbeddingRoute));
  if (!index || !queryEmbeddingRoute || queryEmbeddingRoute.modelId !== index.modelId
      || queryEmbeddingRoute.vectorSpaceId !== index.vectorSpaceId) {
    return { hits: [], truncated: false };
  }
  const values = Array.isArray(envelope?.hits) ? envelope.hits : [];
  const hits = values.flatMap((value, position) => {
    const hit = record(value);
    const sourceDocument = record(hit?.sourceDocument);
    const sourceSpan = record(hit?.sourceSpan);
    const sourceText = nonBlankText(sourceDocument?.rawText);
    const documentId = nonBlankText(hit?.documentId);
    const sourceDocumentId = nonBlankText(sourceDocument?.docId);
    const chunkId = nonBlankText(hit?.chunkId);
    const emittedChunkText = nonBlankText(hit?.emittedText);
    const start = nonNegativeInteger(sourceSpan?.start);
    const end = nonNegativeInteger(sourceSpan?.end);
    const score = finiteNumber(hit?.score);
    const offsetEncoding = text(sourceDocument?.offsetEncoding);
    if (!hit || !sourceDocument || !sourceText || !documentId || documentId !== sourceDocumentId
        || !chunkId || !emittedChunkText || !validOffsetEncoding(offsetEncoding) || start === undefined
        || end === undefined || sourceSpan?.space !== "COORDINATE_SPACE_CHAR_DOCUMENT"
        || !toBrowserSpan(sourceText, start, end, offsetEncoding)
        || score === undefined || score < -1 || score > 1) {
      return [];
    }
    return [{
      position,
      hit: {
        id: `${documentId}/${chunkId}`,
        documentId,
        chunkId,
        score,
        sourceDocument,
        sourceText,
        start,
        end,
        offsetEncoding,
        emittedChunkText,
        modelId: index.modelId,
        backendId: index.backendId,
        vectorSpaceId: index.vectorSpaceId,
        providerId: index.providerId,
        artifactHash: index.artifactHash,
        indexId: index.id,
        corpusTitle: index.corpusTitle,
        provenance: index.provenance,
        sourceUri: index.sourceUri,
        licenseName: index.licenseName,
        licenseUri: index.licenseUri,
        corpusArtifactHash: index.corpusArtifactHash,
        build: index.build,
        queryEmbeddingRoute,
      },
    }];
  }).sort((left, right) => right.hit.score - left.hit.score || left.position - right.position)
    .map(({ hit }) => hit);
  return { hits, truncated: envelope?.truncated === true };
}

function providerIdentity(provider: Record<string, unknown> | undefined): string | undefined {
  const custom = optionalText(provider?.custom);
  const standard = optionalText(provider?.standard);
  if ((custom && standard) || (!custom && !standard)
      || standard === "STANDARD_SEARCH_PROVIDER_UNSPECIFIED") {
    return undefined;
  }
  return custom || standard;
}

function readEmbeddingRoute(route: Record<string, unknown> | undefined): SearchEmbeddingRoute | undefined {
  const modelId = optionalText(route?.modelId);
  const backendId = optionalText(route?.backendId);
  const vectorSpaceId = optionalText(route?.vectorSpaceId);
  return modelId && backendId && vectorSpaceId ? {
    modelId,
    backendId,
    vectorSpaceId,
    artifactHash: optionalText(route?.artifactHash),
  } : undefined;
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function optionalText(value: unknown): string | undefined {
  return text(value) || undefined;
}

function nonBlankText(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value : undefined;
}

function validOffsetEncoding(value: string): boolean {
  return value === "OFFSET_ENCODING_UTF8_BYTE"
    || value === "OFFSET_ENCODING_UTF16_CODE_UNIT"
    || value === "OFFSET_ENCODING_UNICODE_CODE_POINT";
}

function finiteNumber(value: unknown): number | undefined {
  const candidate = typeof value === "string" && value.trim() ? Number(value) : value;
  return typeof candidate === "number" && Number.isFinite(candidate) ? candidate : undefined;
}

function nonNegativeInteger(value: unknown): number | undefined {
  const candidate = finiteNumber(value);
  return candidate !== undefined && Number.isSafeInteger(candidate) && candidate >= 0 ? candidate : undefined;
}

function positiveInteger(value: unknown): number | undefined {
  const candidate = nonNegativeInteger(value);
  return candidate !== undefined && candidate > 0 ? candidate : undefined;
}

function safeUri(value: unknown): string | undefined {
  const candidate = optionalText(value);
  if (!candidate) {
    return undefined;
  }
  try {
    const protocol = new URL(candidate).protocol;
    return protocol === "https:" || protocol === "http:" ? candidate : undefined;
  } catch {
    return undefined;
  }
}
