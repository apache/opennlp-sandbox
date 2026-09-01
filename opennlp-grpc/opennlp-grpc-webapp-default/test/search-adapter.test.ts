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
  createAllHitsSearchRequest,
  createCompoundSearchRequest,
  createIndexDocumentsRequest,
  createSearchRequest,
  indexStateLabel,
  readSearchIndexes,
  readSearchProviderInstances,
  readSearchProviderListing,
  readSearchResponse,
  supportsKeywordClauses,
} from "../src/search-adapter";

function indexDescriptor(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    indexId: "apache-guides",
    displayName: "Apache guides",
    provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" },
    embeddingRoute: {
      modelId: "static-mini",
      backendId: "static",
      vectorSpaceId: "mini-v1",
      artifactHash: "sha256:abc",
    },
    dimension: 384,
    metric: "SEARCH_METRIC_COSINE",
    size: 42,
    maxTopK: 25,
    maxQueryBytes: 8192,
    maxResponseBytes: 1048576,
    build: {
      bundleFormatVersion: 1,
      bundleArtifactHash: "sha256:bundle",
      builderId: "opennlp-index-builder",
      builderVersion: "1.0",
      preparationConfigHash: "sha256:config",
    },
    immutable: true,
    corpus: {
      title: "Apache documentation",
      provenanceSummary: "Released documentation corpus",
      sourceUri: "https://example.invalid/corpus",
      licenseName: "Apache-2.0",
      licenseUri: "https://www.apache.org/licenses/LICENSE-2.0",
      artifactHash: "sha256:corpus",
    },
    ...overrides,
  };
}

function searchHit(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    documentId: "doc-a",
    chunkId: "chunk-a",
    score: 0.8,
    sourceDocument: {
      docId: "doc-a",
      rawText: "OpenNLP works.",
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    },
    sourceSpan: { start: 0, end: 7, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
    indexedText: "OpenNLP",
    chunkGroupId: "sentence-chunks",
    ...overrides,
  };
}

function searchResponse(hits: Record<string, unknown>[], overrides: Record<string, unknown> = {}) {
  const sources = new Map<string, unknown>();
  const compactHits = hits.map((hit) => {
    const source = hit.sourceDocument as Record<string, unknown> | undefined;
    if (source && typeof source.docId === "string" && !sources.has(source.docId)) {
      sources.set(source.docId, source);
    }
    const compact = { ...hit };
    delete compact.sourceDocument;
    return compact;
  });
  return {
    index: indexDescriptor(),
    queryEmbeddingRoute: {
      modelId: "static-mini",
      backendId: "static",
      vectorSpaceId: "mini-v1",
      artifactHash: "sha256:query",
    },
    sourceDocuments: [...sources.values()],
    hits: compactHits,
    ...overrides,
  };
}

describe("server search API adapter", () => {
  it("reads protobuf JSON index descriptors", () => {
    expect(readSearchIndexes({ indexes: [indexDescriptor()] })).toEqual([expect.objectContaining({
      id: "apache-guides",
      label: "Apache guides",
      modelId: "static-mini",
      backendId: "static",
      vectorSpaceId: "mini-v1",
      dimension: 384,
      size: 42,
      maxTopK: 25,
      maxQueryBytes: 8192,
      maxResponseBytes: 1048576,
      supportsAllHits: false,
      immutable: true,
      persisted: false,
      corpusTitle: "Apache documentation",
      licenseName: "Apache-2.0",
      corpusArtifactHash: "sha256:corpus",
      build: expect.objectContaining({
        bundleFormatVersion: 1,
        builderId: "opennlp-index-builder",
        preparationConfigHash: "sha256:config",
      }),
    })]);
  });

  it("accepts mutable server workspace descriptors whose default false flag is omitted", () => {
    const descriptor = indexDescriptor();
    delete descriptor.immutable;

    expect(readSearchIndexes({ indexes: [descriptor] })[0]?.immutable).toBe(false);
  });

  it("rejects descriptors without typed search semantics", () => {
    const invalid = [
      indexDescriptor({ provider: {} }),
      indexDescriptor({ provider: { standard: "STANDARD_SEARCH_PROVIDER_UNSPECIFIED" } }),
      indexDescriptor({ metric: "SEARCH_METRIC_UNSPECIFIED" }),
    ];

    expect(readSearchIndexes({ indexes: invalid })).toEqual([]);
  });

  it("reads the server-wide live indexing and persistence flags beside the providers", () => {
    const listing = readSearchProviderListing({
      providers: [{ instanceId: "flat_float", providerId: "flat_float", capabilities: [] }],
      dynamicIndexingEnabled: false,
      persistenceConfigured: true,
    });
    expect(listing.providers.map((provider) => provider.instanceId)).toEqual(["flat_float"]);
    expect(listing.dynamicIndexingEnabled).toBe(false);
    expect(listing.persistenceConfigured).toBe(true);
    // An older gateway omits both flags: live indexing on, nothing saved to disk.
    expect(readSearchProviderListing({ providers: [] }))
      .toMatchObject({ dynamicIndexingEnabled: true, persistenceConfigured: false });
  });

  it("reads the components an index executes and whether keyword clauses can run", () => {
    const [index] = readSearchIndexes({ indexes: [{
      indexId: "bundle-1",
      displayName: "Bundle",
      provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" },
      embeddingRoute: { modelId: "m", backendId: "static", vectorSpaceId: "s" },
      metric: "SEARCH_METRIC_COSINE",
      immutable: true,
      components: [
        { kind: "SEARCH_COMPONENT_KIND_VECTOR", providerInstanceId: "turbo_quant" },
        { kind: "SEARCH_COMPONENT_KIND_KEYWORD", providerInstanceId: "terms" },
      ],
    }] });
    expect(index?.components).toEqual([
      { kind: "vector", providerInstanceId: "turbo_quant" },
      { kind: "keyword", providerInstanceId: "terms" },
    ]);
    expect(supportsKeywordClauses(index!)).toBe(true);
    expect(supportsKeywordClauses({ ...index!, components: [index!.components![0]!] })).toBe(false);
    expect(supportsKeywordClauses({ ...index!, components: undefined })).toBe(true);
  });

  it("names a new live index from the field, falling back to the workbench default", () => {
    const named = createIndexDocumentsRequest(undefined, "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT",
      { docId: "d" }, "m", [], "  Case notes ");
    expect(named.displayName).toBe("Case notes");
    const unnamed = createIndexDocumentsRequest("live-1", "STANDARD_SEARCH_PROVIDER_FLAT_FLOAT",
      { docId: "d" }, "m", [], "   ");
    expect(unnamed.displayName).toBe("Workbench index");
    expect(unnamed.indexId).toBe("live-1");
    expect(unnamed.provider).toBeUndefined();
  });

  it("creates a protobuf JSON search request with a document-shaped query", () => {
    expect(createSearchRequest("apache-guides", "Where is OpenNLP used?", 7)).toEqual({
      indexId: "apache-guides",
      query: { rawText: "Where is OpenNLP used?" },
      topK: 7,
    });
  });

  it("creates an explicit exhaustive result request without a magic top-k value", () => {
    expect(createAllHitsSearchRequest("apache-guides", "Where is OpenNLP used?")).toEqual({
      indexId: "apache-guides",
      query: { rawText: "Where is OpenNLP used?" },
      allHits: true,
    });
  });

  it("retains configured and actual routes while allowing backend fallback in the same vector space", () => {
    const result = readSearchResponse(searchResponse([searchHit()], {
      queryEmbeddingRoute: {
        modelId: "static-mini",
        backendId: "onnx-fallback",
        vectorSpaceId: "mini-v1",
        artifactHash: "sha256:fallback",
      },
    }));

    expect(result.hits[0]).toMatchObject({
      modelId: "static-mini",
      backendId: "static",
      vectorSpaceId: "mini-v1",
      queryEmbeddingRoute: {
        modelId: "static-mini",
        backendId: "onnx-fallback",
        vectorSpaceId: "mini-v1",
        artifactHash: "sha256:fallback",
      },
    });
  });

  it("normalizes and stably orders valid server hits by score", () => {
    const result = readSearchResponse(searchResponse([
      searchHit({
        documentId: "doc-b",
        chunkId: "chunk-b",
        score: 0.25,
        sourceDocument: {
          docId: "doc-b",
          rawText: "Alpha beta gamma.",
          offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        },
        sourceSpan: { start: "6", end: "10", space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
        indexedText: "beta",
      }),
      searchHit({ score: 1 }),
      searchHit({
        documentId: "doc-c",
        chunkId: "chunk-c",
        score: 0.25,
        sourceDocument: {
          docId: "doc-c",
          rawText: "Delta epsilon.",
          offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        },
        sourceSpan: { start: 0, end: 5, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
        indexedText: "Delta",
      }),
    ]));

    expect(result.hits.map((hit) => hit.id)).toEqual(["doc-a/chunk-a", "doc-b/chunk-b", "doc-c/chunk-c"]);
    expect(result.hits[0]).toMatchObject({
      score: 1,
      sourceText: "OpenNLP works.",
      start: 0,
      end: 7,
      chunkGroupId: "sentence-chunks",
    });
  });

  it("accepts a protobuf JSON source span whose zero start is omitted", () => {
    const result = readSearchResponse(searchResponse([searchHit({
      sourceSpan: { end: 7, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
    })]));

    expect(result.hits).toHaveLength(1);
    expect(result.hits[0]).toMatchObject({ start: 0, end: 7, indexedChunkText: "OpenNLP" });
  });

  it("rejects missing, unspecified, and unsupported offset encodings", () => {
    for (const offsetEncoding of [undefined, "OFFSET_ENCODING_UNSPECIFIED", "OFFSET_ENCODING_FUTURE"]) {
      const sourceDocument = { docId: "doc-a", rawText: "OpenNLP works.", offsetEncoding };
      expect(readSearchResponse(searchResponse([searchHit({ sourceDocument })])).hits).toEqual([]);
    }
  });

  it("requires the hit and source document identities to be present and equal", () => {
    expect(readSearchResponse(searchResponse([searchHit({ documentId: "" })])).hits).toEqual([]);
    expect(readSearchResponse(searchResponse([searchHit({
      sourceDocument: { rawText: "OpenNLP works.", offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
    })])).hits).toEqual([]);
    expect(readSearchResponse(searchResponse([searchHit({
      sourceDocument: {
        docId: "different",
        rawText: "OpenNLP works.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      },
    })])).hits).toEqual([]);
  });

  it("rejects nonfinite and out-of-range cosine scores", () => {
    for (const score of [-1.01, 1.01, "NaN", "Infinity", "-Infinity"]) {
      expect(readSearchResponse(searchResponse([searchHit({ score })])).hits).toEqual([]);
    }
  });

  it("rejects blank indexed text and mismatched query vector spaces", () => {
    expect(readSearchResponse(searchResponse([searchHit({ indexedText: " \t" })])).hits).toEqual([]);
    expect(readSearchResponse(searchResponse([searchHit()], {
      queryEmbeddingRoute: { modelId: "static-mini", backendId: "fallback", vectorSpaceId: "other-space" },
    })).hits).toEqual([]);
  });

  it("preserves authoritative source and indexed whitespace", () => {
    const result = readSearchResponse(searchResponse([searchHit({
      sourceDocument: {
        docId: "doc-a",
        rawText: "  OpenNLP works.",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      },
      sourceSpan: { start: 2, end: 9, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
      indexedText: " OpenNLP ",
    })]));

    expect(result.hits[0]).toMatchObject({ sourceText: "  OpenNLP works.", indexedChunkText: " OpenNLP " });
  });

  it("rejects malformed hits that cannot map back to a source span", () => {
    expect(readSearchResponse(searchResponse([searchHit({
      sourceDocument: {
        docId: "doc-a",
        rawText: "short",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
      },
      sourceSpan: { start: 3, end: 99, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
    })])).hits).toEqual([]);
  });

  it("rejects UTF-8 offsets that land inside a multi-byte code point", () => {
    expect(readSearchResponse(searchResponse([searchHit({
      sourceDocument: { docId: "doc-a", rawText: "A😀 end", offsetEncoding: "OFFSET_ENCODING_UTF8_BYTE" },
      sourceSpan: { start: 2, end: 5, space: "COORDINATE_SPACE_CHAR_DOCUMENT" },
      indexedText: "broken",
    })])).hits).toEqual([]);
  });

  it("rejects source spans outside the document character coordinate space", () => {
    expect(readSearchResponse(searchResponse([searchHit({
      sourceSpan: { start: 0, end: 3, space: "COORDINATE_SPACE_TOKEN_SENTENCE" },
    })])).hits).toEqual([]);
  });

  it("retains the server response truncation signal independently of hits", () => {
    expect(readSearchResponse(searchResponse([], { truncated: true }))).toEqual({ hits: [], truncated: true });
  });

  it("creates a compound search request from a QueryNode tree", () => {
    const node = { term: { text: "habeas corpus", mode: "TERM_MATCH_MODE_ALL" } };

    expect(createCompoundSearchRequest("apache-guides", node, 5)).toEqual({
      indexId: "apache-guides",
      compoundQuery: node,
      topK: 5,
    });
  });

  it("reads matched spans and drops any outside the indexed text", () => {
    const result = readSearchResponse(searchResponse([searchHit({
      matchedSpans: [
        { start: 0, end: 4, term: "open" },
        { end: 7, term: "opennlp" },
        { start: 3, end: 2, term: "backwards" },
        { start: 0, end: 99, term: "overrun" },
        { start: 0, end: 4, term: " " },
      ],
    })]));

    expect(result.hits[0]?.matchedSpans).toEqual([
      { start: 0, end: 4, term: "open" },
      { start: 0, end: 7, term: "opennlp" },
    ]);
  });

  it("accepts keyword-only compound responses that carry no query embedding route", () => {
    const result = readSearchResponse(searchResponse([
      searchHit({ matchedSpans: [{ start: 0, end: 7, term: "opennlp" }] }),
    ], { queryEmbeddingRoute: undefined }));

    expect(result.hits).toHaveLength(1);
    expect(result.hits[0]?.queryEmbeddingRoute).toBeUndefined();
  });

  it("reads provider instances with lowercased capabilities", () => {
    const providers = readSearchProviderInstances({
      providers: [
        {
          instanceId: "turbo_quant",
          providerId: "turbo_quant",
          capabilities: [
            "SEARCH_PROVIDER_CAPABILITY_VECTOR",
            "SEARCH_PROVIDER_CAPABILITY_LIVE",
            "SEARCH_PROVIDER_CAPABILITY_PERSISTENT",
            "SEARCH_PROVIDER_CAPABILITY_UNSPECIFIED",
          ],
          standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
        },
        { instanceId: "", providerId: "broken" },
      ],
    });

    expect(providers).toEqual([{
      instanceId: "turbo_quant",
      providerId: "turbo_quant",
      capabilities: ["vector", "live", "persistent"],
      standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT",
    }]);
  });
});

describe("indexStateLabel", () => {
  it("names the three live index states from the descriptor flags", () => {
    expect(indexStateLabel({ immutable: false, persisted: false })).toBe("In memory");
    expect(indexStateLabel({ immutable: false, persisted: true })).toBe("Saved to disk");
    expect(indexStateLabel({ immutable: true, persisted: true })).toBe("Read-only");
    // A read-only index is always on disk; the flag pair the server never emits still reads right.
    expect(indexStateLabel({ immutable: true, persisted: false })).toBe("Read-only");
  });

  it("maps persisted from the descriptor and defaults it to false", () => {
    expect(readSearchIndexes({ indexes: [indexDescriptor({ persisted: true })] })[0]?.persisted)
      .toBe(true);
    expect(readSearchIndexes({ indexes: [indexDescriptor()] })[0]?.persisted).toBe(false);
  });
});
