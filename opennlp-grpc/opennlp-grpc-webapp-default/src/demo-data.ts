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

/** One bundled public-domain novel: its gzip asset and the pinned sizes and markers proving integrity. */
interface NovelDemo {
  readonly title: string;
  readonly path: string;
  readonly compressedBytes: number;
  readonly textBytes: number;
  readonly markers: readonly string[];
}

const ALICE: NovelDemo = {
  title: "Alice",
  path: "./data/alice-in-wonderland.txt.gz",
  compressedBytes: 53_192,
  textBytes: 151_064,
  markers: ["Alice’s Adventures in Wonderland", "CHAPTER XII."],
};

const PRIDE_AND_PREJUDICE: NovelDemo = {
  title: "Pride and Prejudice",
  path: "./data/pride-and-prejudice.txt.gz",
  compressedBytes: 241_846,
  textBytes: 694_478,
  markers: ["It is a truth universally acknowledged", "CHAPTER LXI."],
};

/**
 * Loads the bundled Alice’s Adventures in Wonderland text.
 *
 * @param fetcher The fetch implementation, injectable for tests.
 * @param decompress The gzip decompressor, injectable for tests.
 * @returns The decoded novel text.
 */
export async function loadAliceDemo(
  fetcher: Fetcher = fetch,
  decompress: Decompressor = gzipStream,
): Promise<string> {
  return loadNovelDemo(ALICE, fetcher, decompress);
}

/**
 * Loads the bundled Pride and Prejudice text.
 *
 * @param fetcher The fetch implementation, injectable for tests.
 * @param decompress The gzip decompressor, injectable for tests.
 * @returns The decoded novel text.
 */
export async function loadPrideAndPrejudiceDemo(
  fetcher: Fetcher = fetch,
  decompress: Decompressor = gzipStream,
): Promise<string> {
  return loadNovelDemo(PRIDE_AND_PREJUDICE, fetcher, decompress);
}

/** Fetches, bounds, decompresses, and verifies one bundled novel. */
async function loadNovelDemo(
  novel: NovelDemo,
  fetcher: Fetcher,
  decompress: Decompressor,
): Promise<string> {
  const response = await fetcher(novel.path, { headers: { accept: "application/gzip" } });
  if (!response.ok) {
    throw new Error(`Could not load the ${novel.title} demo (${response.status}).`);
  }
  const compressed = await response.arrayBuffer();
  if (compressed.byteLength !== novel.compressedBytes) {
    throw new Error(`The compressed ${novel.title} demo has an unexpected size.`);
  }
  const stream = new Blob([compressed]).stream() as ReadableStream<Uint8Array>;
  const decoded = await new Response(decompress(stream)).arrayBuffer();
  if (decoded.byteLength !== novel.textBytes) {
    throw new Error(`The decompressed ${novel.title} demo has an unexpected size.`);
  }
  const text = new TextDecoder("utf-8", { fatal: true }).decode(decoded);
  if (!novel.markers.every((marker) => text.includes(marker))) {
    throw new Error(`The decompressed ${novel.title} demo did not contain the expected text.`);
  }
  return text;
}

function gzipStream(stream: ReadableStream<Uint8Array>): ReadableStream<Uint8Array> {
  const decoder = new DecompressionStream("gzip") as unknown as
    ReadableWritablePair<Uint8Array, Uint8Array>;
  return stream.pipeThrough(decoder);
}
