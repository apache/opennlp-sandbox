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

import type { Fetcher } from "./api";

type Decompressor = (stream: ReadableStream<Uint8Array>) => ReadableStream<Uint8Array>;

const ALICE_PATH = "./data/alice-in-wonderland.txt.gz";
const ALICE_COMPRESSED_BYTES = 53_192;
const ALICE_TEXT_BYTES = 151_064;

/** Loads the pinned public-domain Alice demo through a bounded gzip decoder. */
export async function loadAliceDemo(
  fetcher: Fetcher = fetch,
  decompress: Decompressor = gzipStream,
): Promise<string> {
  const response = await fetcher(ALICE_PATH, { headers: { accept: "application/gzip" } });
  if (!response.ok) {
    throw new Error(`Could not load the Alice demo (${response.status}).`);
  }
  const compressed = await response.arrayBuffer();
  if (compressed.byteLength !== ALICE_COMPRESSED_BYTES) {
    throw new Error("The compressed Alice demo has an unexpected size.");
  }
  const stream = new Blob([compressed]).stream() as ReadableStream<Uint8Array>;
  const decoded = await new Response(decompress(stream)).arrayBuffer();
  if (decoded.byteLength !== ALICE_TEXT_BYTES) {
    throw new Error("The decompressed Alice demo has an unexpected size.");
  }
  const text = new TextDecoder("utf-8", { fatal: true }).decode(decoded);
  if (!text.includes("Alice’s Adventures in Wonderland") || !text.includes("CHAPTER XII.")) {
    throw new Error("The decompressed Alice demo did not contain the expected text.");
  }
  return text;
}

function gzipStream(stream: ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
  const decoder = new DecompressionStream("gzip") as unknown as
    ReadableWritablePair<Uint8Array, Uint8Array>;
  return stream.pipeThrough(decoder);
}
