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

import type { IndexDocumentsRequest } from "./api";
import { toBrowserSpan } from "./offsets";
import { asciiLowerCase } from "./text-utils";

/** Stable browser view of one server-owned static or dynamic search index. */
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
  supportsAllHits: boolean;
  immutable: boolean;
  /** True when a checkpoint of the index exists on disk, so it survives a server restart. */
  persisted: boolean;
  corpusTitle: string;
  provenance: string;
  sourceUri?: string;
  licenseName?: string;
  licenseUri?: string;
  corpusArtifactHash?: string;
  build: SearchIndexBuild;
  /** The modalities the index executes; absent when the server did not report them. */
  components?: SearchIndexComponent[];
}

/** One search modality an index executes, and which provider instance serves it. */
export interface SearchIndexComponent {
  kind: "vector" | "keyword" | "unspecified";
  providerInstanceId: string;
}

/**
 * Whether an index can run keyword (term and phrase) clauses. An index whose components
 * were not reported is given the benefit of the doubt.
 */
export function supportsKeywordClauses(index: SearchIndex): boolean {
  return index.components === undefined
    || index.components.some((component) => component.kind === "keyword");
}

export interface SearchEmbeddingRoute {
  modelId: string;
  backendId: string;
  vectorSpaceId: string;
  artifactHash?: string;
}

/** One keyword or phrase match within a hit's indexed chunk text, UTF-16 units. */
export interface MatchedSpan {
  start: number;
  end: number;
  term: string;
}

/** Stable browser view of one source-mapped search hit. */
export interface SearchHit {
  id: string;
  documentId: string;
  chunkId: string;
  chunkGroupId: string;
  score: number;
  /** Absent for keyword-only compound queries, which embed nothing. */
  sourceDocument: Record<string, unknown>;
  sourceText: string;
  start: number;
  end: number;
  offsetEncoding: string;
  indexedChunkText: string;
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
  queryEmbeddingRoute?: SearchEmbeddingRoute;
  matchedSpans: MatchedSpan[];
}

export interface SearchResponse {
  hits: SearchHit[];
  truncated: boolean;
}

/** Request body for `POST /api/v1/search`; exactly one query form is set. */
interface SearchRequestBase {
  indexId: string;
  query?: {
    docId?: string;
    rawText: string;
  };
  compoundQuery?: Record<string, unknown>;
}

export type SearchRequest = SearchRequestBase & (
  { topK: number; allHits?: never } | { allHits: true; topK?: never }
);

export function createSearchRequest(indexId: string, query: string, topK: number): SearchRequest {
  return { indexId, query: { rawText: query }, topK };
}

/** Builds an exhaustive search request without assigning a sentinel top-k value. */
export function createAllHitsSearchRequest(indexId: string, query: string): SearchRequest {
  return { indexId, query: { rawText: query }, allHits: true };
}

/**
 * Builds the request that adds one analyzed document to a live index. A new index names
 * its vector storage; an extension of an existing index inherits the storage it was created
 * with by omitting the provider.
 */
export function createIndexDocumentsRequest(
  existingIndexId: string | undefined,
  providerStandard: string,
  document: Record<string, unknown>,
  modelId: string,
  chunkGroupIds: string[],
  displayName: string = "Workbench index",
): IndexDocumentsRequest {
  return {
    ...(existingIndexId ? { indexId: existingIndexId } : {}),
    displayName: displayName.trim() || "Workbench index",
    ...(existingIndexId ? {} : { provider: { standard: providerStandard } }),
    documents: [document],
    embedding: { modelId },
    chunkGroupIds,
  };
}

/** Builds a compound search request from a protobuf JSON QueryNode tree. */
export function createCompoundSearchRequest(
  indexId: string,
  compoundQuery: Record<string, unknown>,
  topK: number,
): SearchRequest {
  return { indexId, compoundQuery, topK };
}

/** One configured search provider instance and its declared capabilities. */
export interface SearchProviderInstance {
  instanceId: string;
  providerId: string;
  /** Lowercased capability names, e.g. "vector", "keyword", "live", "persistent". */
  capabilities: string[];
  /** Standard enum shorthand when this instance is a built-in default. */
  standard?: string;
}

/**
 * Display-name prefix of the scratch indexes the Analyze tab's heatmap builds for one
 * document; the lifecycle and live index pickers hide them.
 */
export const SCRATCH_INDEX_PREFIX = "Current document heatmap:";

/** The provider listing plus the two server-wide facts every search tab gates on. */
export interface SearchProviderListing {
  providers: SearchProviderInstance[];
  /** False when the operator disabled live indexing; every live-index call then fails. */
  dynamicIndexingEnabled: boolean;
  /** True when live indexes can be saved to disk (search.persist.root is set). */
  persistenceConfigured: boolean;
}

/**
 * Reads the whole search-providers reply. A reply from a gateway that predates the flags
 * reads as enabled and not persistable, which matches what such a server did.
 */
export function readSearchProviderListing(response: unknown): SearchProviderListing {
  const envelope = record(response);
  return {
    providers: readSearchProviderInstances(response),
    dynamicIndexingEnabled: envelope?.dynamicIndexingEnabled !== false,
    persistenceConfigured: envelope?.persistenceConfigured === true,
  };
}

/** Reads the search-providers listing JSON defensively. */
export function readSearchProviderInstances(response: unknown): SearchProviderInstance[] {
  const envelope = record(response);
  const providers = Array.isArray(envelope?.providers) ? envelope.providers : [];
  return providers.flatMap((value) => {
    const instance = record(value);
    const instanceId = text(instance?.instanceId);
    const providerId = text(instance?.providerId);
    if (!instance || !instanceId || !providerId) {
      return [];
    }
    const capabilityValues = Array.isArray(instance.capabilities) ? instance.capabilities : [];
    const capabilities = capabilityValues.flatMap((capability) => {
      const name = text(capability);
      return name.startsWith("SEARCH_PROVIDER_CAPABILITY_")
          && name !== "SEARCH_PROVIDER_CAPABILITY_UNSPECIFIED"
        ? [asciiLowerCase(name.slice("SEARCH_PROVIDER_CAPABILITY_".length))]
        : [];
    });
    const standard = optionalText(instance.standard);
    return [{
      instanceId,
      providerId,
      capabilities,
      ...(standard && standard !== "STANDARD_SEARCH_PROVIDER_UNSPECIFIED"
        ? { standard } : {}),
    }];
  });
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
    const immutable = descriptor.immutable === undefined ? false : descriptor.immutable;
    if (!providerId || !embeddingRoute || typeof immutable !== "boolean"
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
      supportsAllHits: descriptor.supportsAllHits === true,
      immutable,
      persisted: descriptor.persisted === true,
      ...(Array.isArray(descriptor.components)
        ? { components: readComponents(descriptor.components) } : {}),
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
  // Keyword-only compound queries embed nothing and carry no query route.
  const queryEmbeddingRoute = readEmbeddingRoute(record(envelope?.queryEmbeddingRoute));
  if (!index || (queryEmbeddingRoute && (queryEmbeddingRoute.modelId !== index.modelId
      || queryEmbeddingRoute.vectorSpaceId !== index.vectorSpaceId))) {
    return { hits: [], truncated: false };
  }
  const values = Array.isArray(envelope?.hits) ? envelope.hits : [];
  const sourceValues = Array.isArray(envelope?.sourceDocuments) ? envelope.sourceDocuments : [];
  const sources = new Map<string, Record<string, unknown>>();
  for (const value of sourceValues) {
    const source = record(value);
    const sourceId = nonBlankText(source?.docId);
    if (!source || !sourceId || sources.has(sourceId)) {
      return { hits: [], truncated: false };
    }
    sources.set(sourceId, source);
  }
  const hits = values.flatMap((value, position) => {
    const hit = record(value);
    const documentId = nonBlankText(hit?.documentId);
    const sourceDocument = documentId ? sources.get(documentId) : undefined;
    const sourceSpan = record(hit?.sourceSpan);
    const sourceText = nonBlankText(sourceDocument?.rawText);
    const chunkId = nonBlankText(hit?.chunkId);
    const chunkGroupId = nonBlankText(hit?.chunkGroupId);
    const indexedChunkText = nonBlankText(hit?.indexedText);
    const start = sourceSpan?.start === undefined ? 0 : nonNegativeInteger(sourceSpan.start);
    const end = nonNegativeInteger(sourceSpan?.end);
    const score = finiteNumber(hit?.score);
    const offsetEncoding = text(sourceDocument?.offsetEncoding);
    if (!hit || !sourceDocument || !sourceText || !documentId
        || !chunkId || !chunkGroupId || !indexedChunkText
        || !validOffsetEncoding(offsetEncoding) || start === undefined
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
        chunkGroupId,
        score,
        sourceDocument,
        sourceText,
        start,
        end,
        offsetEncoding,
        indexedChunkText,
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
        matchedSpans: readMatchedSpans(hit.matchedSpans, indexedChunkText),
      },
    }];
  }).sort((left, right) => right.hit.score - left.hit.score || left.position - right.position)
    .map(({ hit }) => hit);
  return { hits, truncated: envelope?.truncated === true };
}

/** One logical alias resolving to a current index id. */
export interface IndexAliasView {
  alias: string;
  indexId: string;
}

/** Reads the index-aliases listing JSON defensively. */
export function readIndexAliases(response: unknown): IndexAliasView[] {
  const envelope = record(response);
  const values = Array.isArray(envelope?.aliases) ? envelope.aliases : [];
  return values.flatMap((value) => {
    const entry = record(value);
    const alias = text(entry?.alias);
    const indexId = text(entry?.indexId);
    return alias && indexId ? [{ alias, indexId }] : [];
  });
}

/** Reads the single-index envelope returned by persist, seal, and reindex. */
export function readIndexResponse(response: unknown): SearchIndex | undefined {
  const envelope = record(response);
  return readSearchIndexes({ indexes: envelope?.index ? [envelope.index] : [] })[0];
}

/** Reads matched spans, dropping any that do not fit the indexed text. */
function readMatchedSpans(value: unknown, indexedText: string): MatchedSpan[] {
  const values = Array.isArray(value) ? value : [];
  return values.flatMap((entry) => {
    const span = record(entry);
    const start = span?.start === undefined ? 0 : nonNegativeInteger(span.start);
    const end = nonNegativeInteger(span?.end);
    const term = nonBlankText(span?.term);
    if (start === undefined || end === undefined || !term
        || start >= end || end > indexedText.length) {
      return [];
    }
    return [{ start, end, term }];
  });
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

/** Reads the components list of a descriptor; entries without a kind are unspecified. */
function readComponents(values: unknown[]): SearchIndexComponent[] {
  return values.flatMap((value) => {
    const component = record(value);
    if (!component) {
      return [];
    }
    const kind = text(component.kind);
    return [{
      kind: kind === "SEARCH_COMPONENT_KIND_VECTOR" ? "vector"
        : kind === "SEARCH_COMPONENT_KIND_KEYWORD" ? "keyword" : "unspecified",
      providerInstanceId: text(component.providerInstanceId),
    }];
  });
}

/** The three states a live index passes through, as the pickers label them. */
export type IndexStateLabel = "In memory" | "Saved to disk" | "Read-only";

/**
 * Labels an index's state from the descriptor's two flags: a read-only index is always on
 * disk, a saved one is on disk and still accepts documents, and the rest live in server
 * memory only until the process ends.
 */
export function indexStateLabel(index: Pick<SearchIndex, "immutable" | "persisted">): IndexStateLabel {
  if (index.immutable) {
    return "Read-only";
  }
  return index.persisted ? "Saved to disk" : "In memory";
}
