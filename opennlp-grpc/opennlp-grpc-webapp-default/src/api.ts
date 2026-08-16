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

export interface AnalyzeRequest {
  document: {
    docId?: string;
    rawText: string;
  };
  profileId?: string;
  options?: {
    offsetEncoding?: "OFFSET_ENCODING_UTF8_BYTE" | "OFFSET_ENCODING_UTF16_CODE_UNIT" |
      "OFFSET_ENCODING_UNICODE_CODE_POINT";
  };
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
