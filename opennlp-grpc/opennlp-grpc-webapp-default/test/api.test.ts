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

import { analyze, getHealth, getModelBundles, getServiceInfo, getUiExtensions } from "../src/api";

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
      "/api/v1/analyze",
    ]);
    expect(fetcher.mock.calls[4]?.[1]).toMatchObject({
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        document: { rawText: "A test." },
        profileId: "default",
        options: { offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
      }),
    });
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
