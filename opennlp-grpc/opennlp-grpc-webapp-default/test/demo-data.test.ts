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

import { loadAliceDemo, loadPrideAndPrejudiceDemo } from "../src/demo-data";

describe("compressed demo data", () => {
  it("loads the pinned gzip resource through a bounded decompression path", async () => {
    const prefix = new TextEncoder().encode(
      "Alice’s Adventures in Wonderland\nCHAPTER XII.\n",
    );
    const decoded = new Uint8Array(151_064).fill("x".charCodeAt(0));
    decoded.set(prefix);
    const text = new TextDecoder().decode(decoded);
    const fetcher = vi.fn().mockResolvedValue(new Response(new Uint8Array(53_192), {
      status: 200,
    }));

    const result = await loadAliceDemo(fetcher,
      () => new Blob([decoded]).stream() as ReadableStream<Uint8Array>);

    expect(fetcher).toHaveBeenCalledWith("./data/alice-in-wonderland.txt.gz", {
      headers: { accept: "application/gzip" },
    });
    expect(result).toBe(text);
  });

  it("loads the pinned Pride and Prejudice resource the same way", async () => {
    const prefix = new TextEncoder().encode(
      "It is a truth universally acknowledged\nCHAPTER LXI.\n",
    );
    const decoded = new Uint8Array(694_478).fill("x".charCodeAt(0));
    decoded.set(prefix);
    const fetcher = vi.fn().mockResolvedValue(new Response(new Uint8Array(241_846), {
      status: 200,
    }));
    const result = await loadPrideAndPrejudiceDemo(fetcher,
      () => new Blob([decoded]).stream() as ReadableStream<Uint8Array>);
    expect(fetcher).toHaveBeenCalledWith("./data/pride-and-prejudice.txt.gz", {
      headers: { accept: "application/gzip" },
    });
    expect(result).toBe(new TextDecoder().decode(decoded));
  });

  it("rejects a Pride and Prejudice artifact missing its closing chapter", async () => {
    const decoded = new Uint8Array(694_478).fill("x".charCodeAt(0));
    decoded.set(new TextEncoder().encode("It is a truth universally acknowledged\n"));
    const fetcher = vi.fn().mockResolvedValue(new Response(new Uint8Array(241_846), { status: 200 }));
    await expect(loadPrideAndPrejudiceDemo(fetcher,
      () => new Blob([decoded]).stream() as ReadableStream<Uint8Array>))
      .rejects.toThrow("expected text");
  });

  it("rejects an unexpected decompressed artifact", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(new Uint8Array(53_192), { status: 200 }));

    await expect(loadAliceDemo(fetcher,
      () => new Blob(["too short"]).stream() as ReadableStream<Uint8Array>))
      .rejects.toThrow("unexpected size");
  });
});
