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
  };
  options?: {
    includeProbabilities?: boolean;
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
    dictionaryArtifactId: string;
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

export function searchIndex(request: SearchRequest, fetcher: Fetcher = fetch): Promise<unknown> {
  return requestJson(
    "/api/v1/search",
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(request),
    },
    fetcher,
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

async function* ndjsonLines(response: Response): AsyncGenerator<string> {
  if (!response.body) {
    for (const line of (await response.text()).split("\n")) {
      if (line.trim()) {
        yield line;
      }
    }
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffered = "";
  for (;;) {
    const { done, value } = await reader.read();
    buffered += done ? decoder.decode() : decoder.decode(value, { stream: true });
    let newline;
    while ((newline = buffered.indexOf("\n")) >= 0) {
      const line = buffered.slice(0, newline);
      buffered = buffered.slice(newline + 1);
      if (line.trim()) {
        yield line;
      }
    }
    if (done) {
      if (buffered.trim()) {
        yield buffered;
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

async function requestJson(path: string, init: RequestInit | undefined, fetcher: Fetcher): Promise<unknown> {
  const response = await fetcher(path, init ?? { headers: { accept: "application/json" } });
  if (!response.ok) {
    throw await responseError(response);
  }
  try {
    return await response.json();
  } catch {
    throw new Error(`The service returned invalid JSON for ${path}.`);
  }
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
