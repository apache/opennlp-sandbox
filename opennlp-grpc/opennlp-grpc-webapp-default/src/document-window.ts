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

const WINDOW_SIZE = 16_000;
const MAX_COMPLETE_GRAPH_ANNOTATIONS = 5_000;

export interface DocumentWindow {
  start: number;
  end: number;
  page: number;
  pageCount: number;
}

/** Selects a bounded character window without discarding the complete document response. */
export function documentWindow(textLength: number, requestedPage: number): DocumentWindow {
  const length = Math.max(0, Math.floor(textLength));
  const pageCount = Math.max(1, Math.ceil(length / WINDOW_SIZE));
  const page = Math.max(0, Math.min(Math.floor(requestedPage), pageCount - 1));
  const start = page * WINDOW_SIZE;
  return { start, end: Math.min(length, start + WINDOW_SIZE), page, pageCount };
}

/** Maps an original document offset to its buffered projection page. */
export function pageForDocumentOffset(offset: number): number {
  return Math.max(0, Math.floor(offset / WINDOW_SIZE));
}

/** Prevents an explicit graph expansion from allocating an unbounded browser chart. */
export function supportsCompleteGraph(annotationCount: number): boolean {
  return annotationCount <= MAX_COMPLETE_GRAPH_ANNOTATIONS;
}
