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

import type { SearchRequest } from "./search-adapter";
import { splitOnCharacters } from "./text-utils";

export interface AnalyzeRequest {
  document: {
    docId?: string;
    rawText: string;
  };
  profileId?: string;
  profile?: {
    steps: string[];
    normalization?: { normalizers: string[]; requireAlignment?: boolean };
    termProfile?: string;
    stopwordLanguage?: string;
    subwordModelId?: string;
    stemmer?: { algorithm: string; language?: string; hunspellDictionaryId?: string };
    wordnetLexiconId?: string;
    termVector?: {
      mode: string;
      sourceLayer: { standard: string; qualifier?: string };
    };
    dependencyParserId?: string;
    relationPatterns?: Array<{ type: string; path: string; trigger?: string }>;
    posTagFormat?: string;
    pipelineLanguage?: string;
  };
  options?: {
    includeProbabilities?: boolean;
    rankedLanguageCount?: number;
    embeddingModelId?: string;
    includeDocumentCentroid?: boolean;
    parseFormats?: string[];
    offsetEncoding?: "OFFSET_ENCODING_UTF8_BYTE" | "OFFSET_ENCODING_UTF16_CODE_UNIT" |
      "OFFSET_ENCODING_UNICODE_CODE_POINT";
  };
  chunkEmbedConfigs?: Array<{
    configId: string;
    resultSetName: string;
    chunking: {
      strategy: { standard: string };
      chunkSize?: number;
      chunkOverlap?: number;
      cleanText: boolean;
      preserveUrls: boolean;
    };
    embeddingModelIds?: string[];
  }>;
}

export interface IndexDocumentsRequest {
  indexId?: string;
  displayName: string;
  documents: Array<Record<string, unknown>>;
  embedding: { modelId: string };
  chunkGroupIds?: string[];
  /** Vector storage for a new dynamic index; omitted keeps the server default. */
  provider?: { standard: string };
}

export interface ImportDictionaryUpload {
  start: {
    format: { standard?: string; custom?: string };
    displayName: string;
    provenanceSummary: string;
    sourceUri?: string;
    licenseName?: string;
    licenseUri?: string;
  };
  /** Base64 of the complete encoded dictionary bytes, per protobuf JSON. */
  data: string;
}

export interface LearnVocabularyUpload {
  start: {
    dictionaryArtifactId?: string;
    displayName: string;
    minFrequency: number;
    maxTerms: number;
    provenanceSummary: string;
  };
  documents: Array<{ docId?: string; rawText: string }>;
}

export interface TrainStaticModelRequest {
  vocabularyArtifactId: string;
  teacherId: string;
  displayName: string;
  pcaDims?: number;
  provenanceSummary: string;
}

export interface InstallModelRequest {
  catalogId: string;
  revision: string;
  licenseName: string;
  licenseAcknowledged: boolean;
}

export interface ReindexIndexRequest {
  /** Source index id or alias. */
  indexId: string;
  embedding: { modelId: string };
  /** Vector storage for the new index; omitted keeps the source's instance. */
  provider?: { standard?: string; custom?: string };
  /** Alias swapped to the new index only after a successful build. */
  alias?: string;
}

export interface SetCollectionRequest {
  collectionId: string;
  displayName: string;
  /** Dynamic member index ids or aliases; stored resolved. */
  memberIndexIds: string[];
  dictionaryArtifactId?: string;
  vocabularyArtifactId?: string;
  modelArtifactId?: string;
  /** Out-of-vocabulary term count that triggers the drift watch event; 0 disables. */
  driftNewTermThreshold?: number;
}

export type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export async function getHealth(fetcher: Fetcher = fetch): Promise<string> {
  const response = await fetcher("/healthz", { headers: { accept: "text/plain" } });
  if (!response.ok) {
    throw await responseError(response);
  }
  return response.text();
}

export function getServiceInfo(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/service-info", undefined, fetcher);
}

export function getModelBundles(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/model-bundles", undefined, fetcher);
}

export function getUiExtensions(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/ui-extensions", undefined, fetcher);
}

export function getSearchIndexes(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/search-indexes", undefined, fetcher);
}

export function getDictionaries(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/dictionaries", undefined, fetcher);
}

/** Lists learned vocabulary artifacts that can seed a distillation or a collection watch. */
export function getVocabularies(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/vocabularies", undefined, fetcher);
}

export function searchIndex(request: SearchRequest, fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson(
    "/api/v1/search",
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(request),
    },
    fetcher,
    // A search is a read: retrying it after a dropped connection cannot duplicate anything.
    true,
  );
}

export function indexDocuments(
  request: IndexDocumentsRequest,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return requestJson(
    "/api/v1/index-documents",
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(request),
    },
    fetcher,
  );
}

export function deleteSearchIndex(indexId: string, fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson(
    "/api/v1/delete-search-index",
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ indexId }),
    },
    fetcher,
  );
}

export function getSearchProviders(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/search-providers", undefined, fetcher);
}

export function persistIndex(indexId: string, fetcher: Fetcher = fetch): Promise<unknown> {
  return postJson("/api/v1/persist-index", { indexId }, fetcher);
}

export function sealIndex(indexId: string, fetcher: Fetcher = fetch): Promise<unknown> {
  return postJson("/api/v1/seal-index", { indexId }, fetcher);
}

export function reindexIndex(
  request: ReindexIndexRequest,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return postJson("/api/v1/reindex-index", request, fetcher);
}

export function setIndexAlias(
  alias: string,
  indexId: string,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return postJson("/api/v1/set-index-alias", { alias, indexId }, fetcher);
}

export function deleteIndexAlias(alias: string, fetcher: Fetcher = fetch): Promise<unknown> {
  return postJson("/api/v1/delete-index-alias", { alias }, fetcher);
}

export function getIndexAliases(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/index-aliases", undefined, fetcher);
}

export function setCollection(
  request: SetCollectionRequest,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return postJson("/api/v1/set-collection", request, fetcher);
}

export function getCollection(collectionId: string, fetcher: Fetcher = fetch): Promise<unknown> {
  return postJson("/api/v1/get-collection", { collectionId }, fetcher);
}

export function getCollections(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/collections", undefined, fetcher);
}

export function deleteCollection(
  collectionId: string,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return postJson("/api/v1/delete-collection", { collectionId }, fetcher);
}

/**
 * Watches one collection over the gateway's NDJSON stream. Every event reaches
 * {@code onEvent} as parsed JSON; the returned promise resolves when the
 * bounded gateway watch lifetime ends, so callers reconnect for a fresh
 * snapshot. An error line or a non-2xx response rejects.
 */
export async function watchCollection(
  collectionId: string,
  onEvent: (event: Record<string, unknown>) => void,
  fetcher: Fetcher = fetch,
): Promise<void> {
  const response = await fetcher("/api/v1/watch-collection", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ collectionId }),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  for await (const line of ndjsonLines(response)) {
    const event = JSON.parse(line) as Record<string, unknown>;
    if (typeof event.code === "string" && !event.kind) {
      throw new Error(typeof event.message === "string" && event.message
        ? event.message : event.code);
    }
    onEvent(event);
  }
}

/**
 * Streams a batch analysis: posts the AnalyzeStream frame sequence (one
 * configuration frame, then one frame per document) and yields each
 * completion-ordered response line to the callback.
 */
export async function analyzeStream(
  frames: Record<string, unknown>[],
  onResponse: (response: Record<string, unknown>) => void,
  fetcher: Fetcher = fetch,
): Promise<void> {
  const response = await fetcher("/api/v1/analyze-stream", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(frames),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  for await (const line of ndjsonLines(response)) {
    const event = JSON.parse(line) as Record<string, unknown>;
    if (typeof event.code === "string" && !event.sequence && !event.ok && !event.error) {
      throw new Error(typeof event.message === "string" && event.message
        ? event.message : event.code);
    }
    onResponse(event);
  }
}

/**
 * Streams one document analysis and resolves with the terminal canonical response.
 * Every earlier event reaches {@code onEvent} immediately so the caller can render
 * completed layers while independent branches continue.
 */
export async function analyzeProgressively(
  request: AnalyzeRequest,
  onEvent: (event: Record<string, unknown>) => void,
  fetcher: Fetcher = fetch,
): Promise<Record<string, unknown>> {
  const response = await fetcher("/api/v1/analyze-progressive", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  let complete: Record<string, unknown> | undefined;
  for await (const line of ndjsonLines(response)) {
    const event = JSON.parse(line) as Record<string, unknown>;
    if (typeof event.code === "string" && !event.sequence) {
      throw new Error(typeof event.message === "string" && event.message
        ? event.message : event.code);
    }
    onEvent(event);
    if (event.complete && typeof event.complete === "object") {
      complete = event.complete as Record<string, unknown>;
    }
  }
  if (!complete) {
    throw new Error("The progressive analysis stream ended without a final response.");
  }
  return complete;
}

export function getDictionaryFormats(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/dictionary-formats", undefined, fetcher);
}

export function importDictionary(
  upload: ImportDictionaryUpload,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return postJson("/api/v1/import-dictionary", upload, fetcher);
}

export function learnVocabulary(
  upload: LearnVocabularyUpload,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return postJson("/api/v1/learn-vocabulary", upload, fetcher);
}

/** Downloads the exact TSV text of one learned vocabulary artifact. */
export async function downloadVocabularyTsv(
  artifactId: string,
  fetcher: Fetcher = fetch,
): Promise<string> {
  const response = await fetcher("/api/v1/download-vocabulary", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ artifactId }),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  return response.text();
}

export function getTeachers(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/teachers", undefined, fetcher);
}

export function getStaticModels(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/static-models", undefined, fetcher);
}

export function getModelCatalog(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/model-catalog", undefined, fetcher);
}

export function getInstalledModels(fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson("/api/v1/installed-models", undefined, fetcher);
}

export function deleteStaticModel(artifactId: string, fetcher: Fetcher = fetch): Promise<unknown> {
  return postJson("/api/v1/delete-static-model", { artifactId }, fetcher);
}

/**
 * Runs one distillation over the gateway's NDJSON stream: progress lines reach
 * {@code onProgress} as the server emits them, and the returned promise resolves with
 * the terminal model descriptor. An error line or a non-2xx response rejects.
 */
export async function trainStaticModel(
  request: TrainStaticModelRequest,
  onProgress: (message: string) => void,
  fetcher: Fetcher = fetch,
): Promise<Record<string, unknown>> {
  const response = await fetcher("/api/v1/train-static-model", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  let model: Record<string, unknown> | undefined;
  for await (const line of ndjsonLines(response)) {
    const update = JSON.parse(line) as Record<string, unknown>;
    if (typeof update.progress === "string") {
      onProgress(update.progress);
    } else if (update.model && typeof update.model === "object") {
      model = update.model as Record<string, unknown>;
    } else if (typeof update.code === "string") {
      throw new Error(typeof update.message === "string" && update.message
        ? update.message : update.code);
    }
  }
  if (!model) {
    throw new Error("The training stream ended without a model.");
  }
  return model;
}

/** Downloads one pinned catalog model and reports each server progress frame. */
export async function installModel(
  request: InstallModelRequest,
  onProgress: (progress: Record<string, unknown>) => void,
  fetcher: Fetcher = fetch,
): Promise<Record<string, unknown>> {
  const response = await fetcher("/api/v1/install-model", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  let model: Record<string, unknown> | undefined;
  for await (const line of ndjsonLines(response)) {
    const update = JSON.parse(line) as Record<string, unknown>;
    if (update.progress && typeof update.progress === "object") {
      onProgress(update.progress as Record<string, unknown>);
    } else if (update.model && typeof update.model === "object") {
      model = update.model as Record<string, unknown>;
    } else if (typeof update.code === "string") {
      throw new Error(typeof update.message === "string" && update.message
        ? update.message : update.code);
    }
  }
  if (!model) {
    throw new Error("The installation stream ended without an installed model.");
  }
  return model;
}

async function* ndjsonLines(response: Response): AsyncGenerator<string> {
  if (!response.body) {
    for (const line of splitOnCharacters(await response.text(), "\n")) {
      if (line.trim()) {
        yield line;
      }
    }
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending: string[] = [];
  for (;;) {
    const { done, value } = await reader.read();
    const decoded = done ? decoder.decode() : decoder.decode(value, { stream: true });
    let start = 0;
    let newline;
    while ((newline = decoded.indexOf("\n", start)) >= 0) {
      pending.push(decoded.slice(start, newline));
      const line = pending.join("");
      pending = [];
      if (line.trim()) {
        yield line;
      }
      start = newline + 1;
    }
    if (start < decoded.length) {
      pending.push(decoded.slice(start));
    }
    if (done) {
      const line = pending.join("");
      if (line.trim()) {
        yield line;
      }
      return;
    }
  }
}

export function analyze(request: AnalyzeRequest, fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson(
    "/api/v1/analyze",
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(request),
    },
    fetcher,
  );
}

/**
 * Analyzes one document and returns the serialized response bytes. The gateway never prints
 * the reply as JSON, so a reply too large for the browser can still become a .pb file.
 */
export async function analyzeToProtobuf(
  request: AnalyzeRequest,
  fetcher: Fetcher = fetch,
): Promise<ArrayBuffer> {
  const response = await fetcher("/api/v1/analyze-protobuf", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  return response.arrayBuffer();
}

/**
 * Turns the stored response JSON into serialized protobuf bytes through the gateway, so
 * the browser can save a .pb file without a protobuf runtime of its own.
 */
export async function encodeAnalyzeResponsePb(
  responseJson: string,
  fetcher: Fetcher = fetch,
): Promise<ArrayBuffer> {
  const response = await fetcher("/api/v1/response/encode", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: responseJson,
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  return response.arrayBuffer();
}

/** Turns a saved .pb response back into the JSON shape every view consumes. */
export function decodeAnalyzeResponsePb(
  bytes: ArrayBuffer,
  fetcher: Fetcher = fetch,
): Promise<unknown> {
  return requestJson(
    "/api/v1/response/decode",
    {
      method: "POST",
      headers: { "content-type": "application/x-protobuf" },
      body: bytes,
    },
    fetcher,
  );
}

function postJson(path: string, body: unknown, fetcher: Fetcher): Promise<unknown> {
  return requestJson(
    path,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    },
    fetcher,
  );
}

/** What the user reads when the browser could not reach the gateway at all. */
export const NETWORK_FAILURE_MESSAGE =
  "The server did not answer. Check the service status light and try again.";

/**
 * Fetches JSON, retrying once when the connection dropped before any response arrived.
 *
 * Reads (GET, or a POST the caller marks safe) are retried because the gateway closes idle
 * keep-alive connections and a browser reusing one gets a network error, not a status. A
 * mutating request is never retried; it fails with the plain network message instead.
 */
async function requestJson(
  path: string,
  init: RequestInit | undefined,
  fetcher: Fetcher,
  retrySafe: boolean = init === undefined || init.method === undefined || init.method === "GET",
): Promise<unknown> {
  const request = init ?? { headers: { accept: "application/json" } };
  let response: Response;
  try {
    response = await fetcher(path, request);
  } catch (error) {
    if (!isNetworkFailure(error)) {
      throw error;
    }
    if (!retrySafe) {
      throw new Error(NETWORK_FAILURE_MESSAGE);
    }
    try {
      response = await fetcher(path, request);
    } catch (retryError) {
      throw isNetworkFailure(retryError) ? new Error(NETWORK_FAILURE_MESSAGE) : retryError;
    }
  }
  if (!response.ok) {
    throw await responseError(response);
  }
  try {
    return await response.json();
  } catch {
    throw new Error(`The service returned invalid JSON for ${path}.`);
  }
}

/** A fetch rejection before any response is a TypeError in every browser. */
function isNetworkFailure(error: unknown): boolean {
  return error instanceof TypeError;
}

async function responseError(response: Response): Promise<Error> {
  let detail = "";
  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("application/json")) {
      const body = (await response.json()) as Record<string, unknown>;
      detail = firstString(body.message, body.error, body.detail);
    } else {
      detail = (await response.text()).trim();
    }
  } catch {
    // Preserve the HTTP status when an error body cannot be read.
  }
  const status = `${response.status}${response.statusText ? ` ${response.statusText}` : ""}`;
  return new Error(detail || `Request failed (${status}).`);
}

function firstString(...values: unknown[]): string {
  return values.find((value): value is string => typeof value === "string" && value.trim().length > 0)?.trim() ?? "";
}
