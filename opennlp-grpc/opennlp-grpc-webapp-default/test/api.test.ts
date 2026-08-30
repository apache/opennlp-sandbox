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

import { describe, expect, it, vi } from "vitest";

import {
  analyze,
  analyzeProgressively,
  analyzeToProtobuf,
  decodeAnalyzeResponsePb,
  deleteStaticModel,
  downloadVocabularyTsv,
  encodeAnalyzeResponsePb,
  getDictionaryFormats,
  getHealth,
  getModelBundles,
  getInstalledModels,
  getModelCatalog,
  getSearchIndexes,
  getServiceInfo,
  getUiExtensions,
  getStaticModels,
  getTeachers,
  importDictionary,
  installModel,
  indexDocuments,
  learnVocabulary,
  deleteSearchIndex,
  searchIndex,
  trainStaticModel,
  NETWORK_FAILURE_MESSAGE,
} from "../src/api";

describe("API client", () => {
  it("uses the same-origin service endpoints", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const body = url.endsWith("healthz") ? "ok" : JSON.stringify({ url });
      return new Response(body, {
        status: 200,
        headers: { "content-type": url.endsWith("healthz") ? "text/plain" : "application/json" },
      });
    });

    await expect(getHealth(fetcher)).resolves.toBe("ok");
    await getServiceInfo(fetcher);
    await getModelBundles(fetcher);
    await getUiExtensions(fetcher);
    await getSearchIndexes(fetcher);
    await searchIndex({ indexId: "guides", query: { rawText: "OpenNLP" }, topK: 3 }, fetcher);
    await indexDocuments({
      displayName: "Workbench",
      documents: [{ docId: "doc-1", rawText: "OpenNLP" }],
      embedding: { modelId: "demo" },
    }, fetcher);
    await deleteSearchIndex("workspace-1", fetcher);
    await analyze(
      {
        document: { rawText: "A test." },
        profileId: "default",
        options: { offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
      },
      fetcher,
    );

    expect(fetcher.mock.calls.map(([url]) => url)).toEqual([
      "/healthz",
      "/api/v1/service-info",
      "/api/v1/model-bundles",
      "/api/v1/ui-extensions",
      "/api/v1/search-indexes",
      "/api/v1/search",
      "/api/v1/index-documents",
      "/api/v1/delete-search-index",
      "/api/v1/analyze",
    ]);
    expect(fetcher.mock.calls[5]?.[1]).toMatchObject({
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ indexId: "guides", query: { rawText: "OpenNLP" }, topK: 3 }),
    });
    expect(fetcher.mock.calls[6]?.[1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({
        displayName: "Workbench",
        documents: [{ docId: "doc-1", rawText: "OpenNLP" }],
        embedding: { modelId: "demo" },
      }),
    });
    expect(fetcher.mock.calls[7]?.[1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ indexId: "workspace-1" }),
    });
    expect(fetcher.mock.calls[8]?.[1]).toMatchObject({
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        document: { rawText: "A test." },
        profileId: "default",
        options: { offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
      }),
    });
  });

  it("retries a read once when the connection dropped, then explains a second failure", async () => {
    let calls = 0;
    const flaky = vi.fn(async () => {
      calls++;
      if (calls === 1) {
        throw new TypeError("Failed to fetch");
      }
      return new Response(JSON.stringify({ indexes: [] }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    });
    await expect(getSearchIndexes(flaky)).resolves.toEqual({ indexes: [] });
    expect(flaky).toHaveBeenCalledTimes(2);

    const dead = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    });
    await expect(searchIndex({ indexId: "x", query: { rawText: "q" }, topK: 1 }, dead))
      .rejects.toThrow(NETWORK_FAILURE_MESSAGE);
    expect(dead).toHaveBeenCalledTimes(2);
  });

  it("never retries a mutating request after a network failure", async () => {
    const dead = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    });
    await expect(deleteSearchIndex("x", dead)).rejects.toThrow(NETWORK_FAILURE_MESSAGE);
    expect(dead).toHaveBeenCalledTimes(1);
  });

  it("surfaces a useful server error", async () => {
    const fetcher = vi.fn(async () =>
      new Response(JSON.stringify({ message: "No compatible model" }), {
        status: 422,
        statusText: "Unprocessable Content",
        headers: { "content-type": "application/json" },
      }),
    );

    await expect(getModelBundles(fetcher)).rejects.toThrow("No compatible model");
  });
});

describe("server-side protobuf analysis", () => {
  it("posts the request JSON and returns the serialized response bytes", async () => {
    const bytes = new Uint8Array([10, 2, 8, 1]);
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe("/api/v1/analyze-protobuf");
      expect(init?.method).toBe("POST");
      expect(init?.body).toBe('{"document":{"rawText":"Hello"}}');
      return new Response(bytes, {
        status: 200,
        headers: { "content-type": "application/x-protobuf" },
      });
    });

    const encoded = await analyzeToProtobuf({ document: { rawText: "Hello" } }, fetcher);

    expect(new Uint8Array(encoded)).toEqual(bytes);
  });

  it("surfaces the gateway error when the server-side analysis fails", async () => {
    const fetcher = vi.fn(async () => new Response(
      JSON.stringify({ code: "RESOURCE_EXHAUSTED", message: "Document too large" }),
      { status: 429, headers: { "content-type": "application/json" } }));

    await expect(analyzeToProtobuf({ document: { rawText: "Hello" } }, fetcher))
      .rejects.toThrow("Document too large");
  });
});

describe("progressive analysis", () => {
  it("delivers NDJSON events as each network chunk completes", async () => {
    const encoder = new TextEncoder();
    const chunks = [
      '{"sequence":"1","started":{"document":{"rawText":"Hello"}}}\n'
        + '{"sequence":"2","layersReady":{"step":"PIPELINE_STEP_TOKENIZE",',
      '"layers":[{"id":"opennlp:tokens"}]}}\n'
        + '{"sequence":"3","complete":{"document":{"rawText":"Hello"}}}\n',
    ];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe("/api/v1/analyze-progressive");
      expect(init?.body).toBe('{"document":{"rawText":"Hello"}}');
      return new Response(new ReadableStream({
        start(controller) {
          for (const chunk of chunks) {
            controller.enqueue(encoder.encode(chunk));
          }
          controller.close();
        },
      }), { status: 200 });
    });
    const events: Record<string, unknown>[] = [];

    const response = await analyzeProgressively(
      { document: { rawText: "Hello" } },
      (event) => events.push(event),
      fetcher,
    );

    expect(events).toHaveLength(3);
    expect(events[1]).toHaveProperty("layersReady.layers.0.id", "opennlp:tokens");
    expect(response).toEqual({ document: { rawText: "Hello" } });
  });
});

describe("saved response transcoding", () => {
  it("encodes the stored response JSON into protobuf bytes", async () => {
    const bytes = new Uint8Array([10, 2, 8, 1]);
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe("/api/v1/response/encode");
      expect(init?.method).toBe("POST");
      expect(init?.body).toBe('{"document":{}}');
      return new Response(bytes, {
        status: 200,
        headers: { "content-type": "application/x-protobuf" },
      });
    });

    const encoded = await encodeAnalyzeResponsePb('{"document":{}}', fetcher);
    expect(new Uint8Array(encoded)).toEqual(bytes);
  });

  it("decodes protobuf bytes back into the response JSON", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(String(input)).toBe("/api/v1/response/decode");
      expect(init?.method).toBe("POST");
      return new Response(JSON.stringify({ document: { docId: "one" } }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    });

    const decoded = await decodeAnalyzeResponsePb(new Uint8Array([10, 2, 8, 1]).buffer, fetcher);
    expect(decoded).toEqual({ document: { docId: "one" } });
  });

  it("surfaces the gateway error message when transcoding fails", async () => {
    const fetcher = vi.fn(async () => new Response(
      JSON.stringify({ code: "INVALID_ARGUMENT", message: "Malformed protobuf response bytes" }),
      { status: 400, headers: { "content-type": "application/json" } },
    ));

    await expect(decodeAnalyzeResponsePb(new ArrayBuffer(3), fetcher))
      .rejects.toThrow("Malformed protobuf response bytes");
  });
});

describe("trainer API client", () => {
  const jsonFetcher = vi.fn(async (input: RequestInfo | URL) =>
    new Response(JSON.stringify({ url: String(input) }), {
      status: 200,
      headers: { "content-type": "application/json" },
    }));

  it("uses the vocabulary and training endpoints", async () => {
    await getDictionaryFormats(jsonFetcher);
    await importDictionary({
      start: {
        format: { standard: "STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES" },
        displayName: "d",
        provenanceSummary: "p",
      },
      data: "aGk=",
    }, jsonFetcher);
    await learnVocabulary({
      start: {
        dictionaryArtifactId: "dictionary-1",
        displayName: "v",
        minFrequency: 1,
        maxTerms: 10,
        provenanceSummary: "p",
      },
      documents: [{ rawText: "Liberty matters." }],
    }, jsonFetcher);
    await getTeachers(jsonFetcher);
    await getStaticModels(jsonFetcher);
    await getModelCatalog(jsonFetcher);
    await getInstalledModels(jsonFetcher);
    await deleteStaticModel("static-model-1", jsonFetcher);
    const urls = jsonFetcher.mock.calls.map((call) => String(call[0]));
    expect(urls).toEqual([
      "/api/v1/dictionary-formats",
      "/api/v1/import-dictionary",
      "/api/v1/learn-vocabulary",
      "/api/v1/teachers",
      "/api/v1/static-models",
      "/api/v1/model-catalog",
      "/api/v1/installed-models",
      "/api/v1/delete-static-model",
    ]);
  });

  it("streams model download progress and resolves with the installed descriptor", async () => {
    const body = "{\"progress\":{\"stage\":\"INSTALL_MODEL_STAGE_DOWNLOADING\","
      + "\"message\":\"Downloading\",\"completedBytes\":\"10\",\"totalBytes\":\"20\"}}\n"
      + "{\"model\":{\"catalog\":{\"catalogId\":\"potion-base-8m\"},"
      + "\"artifactHash\":\"abc\",\"loaded\":true}}\n";
    const fetcher = vi.fn(async () => new Response(body, { status: 200 }));
    const progress: unknown[] = [];

    const installed = await installModel({
      catalogId: "potion-base-8m",
      revision: "revision-1",
      licenseName: "MIT",
      licenseAcknowledged: true,
    }, (update) => progress.push(update), fetcher);

    expect(progress).toHaveLength(1);
    expect(installed.catalog).toEqual({ catalogId: "potion-base-8m" });
  });

  it("downloads vocabulary TSV text", async () => {
    const fetcher = vi.fn(async () => new Response("liberty\t3\tcorpus\n", {
      status: 200,
      headers: { "content-type": "text/tab-separated-values; charset=utf-8" },
    }));

    await expect(downloadVocabularyTsv("vocabulary-1", fetcher))
      .resolves.toBe("liberty\t3\tcorpus\n");
  });

  it("streams training progress and resolves with the terminal model", async () => {
    const body = [
      "{\"progress\":\"resolving teacher\"}",
      "{\"progress\":\"distilling\"}",
      "{\"model\":{\"artifactId\":\"static-model-1\"}}",
    ].join("\n") + "\n";
    const fetcher = vi.fn(async () => new Response(body, {
      status: 200,
      headers: { "content-type": "application/x-ndjson; charset=utf-8" },
    }));
    const progress: string[] = [];

    const model = await trainStaticModel({
      vocabularyArtifactId: "vocabulary-1",
      teacherId: "mini",
      displayName: "m",
      provenanceSummary: "p",
    }, (message) => progress.push(message), fetcher);

    expect(progress).toEqual(["resolving teacher", "distilling"]);
    expect(model.artifactId).toBe("static-model-1");
  });

  it("rejects when the training stream ends with an error line", async () => {
    const body = "{\"progress\":\"resolving teacher\"}\n"
      + "{\"code\":\"INTERNAL\",\"message\":\"teacher crashed\"}\n";
    const fetcher = vi.fn(async () => new Response(body, {
      status: 200,
      headers: { "content-type": "application/x-ndjson; charset=utf-8" },
    }));

    await expect(trainStaticModel({
      vocabularyArtifactId: "vocabulary-1",
      teacherId: "mini",
      displayName: "m",
      provenanceSummary: "p",
    }, () => undefined, fetcher)).rejects.toThrow("teacher crashed");
  });

  it("rejects a pre-stream training failure with the gateway message", async () => {
    const fetcher = vi.fn(async () => new Response(
      JSON.stringify({ code: "NOT_FOUND", message: "Unknown vocabulary artifact" }),
      { status: 404, headers: { "content-type": "application/json" } },
    ));

    await expect(trainStaticModel({
      vocabularyArtifactId: "vocabulary-x",
      teacherId: "mini",
      displayName: "m",
      provenanceSummary: "p",
    }, () => undefined, fetcher)).rejects.toThrow("Unknown vocabulary artifact");
  });
});
