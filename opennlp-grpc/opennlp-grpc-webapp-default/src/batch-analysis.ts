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

import type { AnalyzeRequest } from "./api";
import { splitBlankLineDocuments } from "./text-utils";

/** One rendered outcome of a streamed batch document. */
export interface BatchResultView {
  sequence: number;
  ok: boolean;
  /** "N sentences, M tokens" for a success, or the failure description. */
  summary: string;
  arrival: number;
}

/**
 * Splits pasted batch input into documents on blank lines, mirroring the
 * trainer's corpus convention.
 */
export function splitBatchDocuments(text: string): string[] {
  return splitBlankLineDocuments(text);
}

/**
 * Builds the AnalyzeStream frame sequence for a batch: the configuration frame
 * carries the request's document-independent parts, then one document frame per
 * text with a 1-based sequence.
 */
export function buildStreamFrames(
  documents: string[],
  request: AnalyzeRequest,
): Record<string, unknown>[] {
  const configuration: Record<string, unknown> = {};
  if (request.profile) {
    configuration.profile = request.profile;
  }
  if (request.profileId) {
    configuration.profileId = request.profileId;
  }
  if (request.options) {
    configuration.options = request.options;
  }
  if (request.chunkEmbedConfigs) {
    configuration.chunkEmbedConfigs = request.chunkEmbedConfigs;
  }
  return [
    { configuration },
    ...documents.map((rawText, index) => ({
      document: {
        sequence: String(index + 1),
        document: { docId: `batch-${index + 1}`, rawText },
      },
    })),
  ];
}

/**
 * Reads one streamed AnalyzeStreamResponse line into a rendered outcome.
 *
 * @param response The parsed NDJSON line.
 * @param arrival The 1-based order this response arrived in, which the stream
 *     defines by completion, not by submission.
 */
export function readStreamResponse(
  response: Record<string, unknown>,
  arrival: number,
): BatchResultView {
  const sequence = Number(response.sequence ?? 0);
  const error = asRecord(response.error);
  if (error) {
    const code = typeof error.code === "string" ? error.code : "error";
    const message = typeof error.message === "string" && error.message
      ? error.message : "the document failed";
    return { sequence, ok: false, summary: `${code}: ${message}`, arrival };
  }
  const document = asRecord(asRecord(response.ok)?.document);
  const analytics = asRecord(document?.analytics);
  const sentences = Number(analytics?.totalSentences ?? 0);
  const tokens = Number(analytics?.totalTokens ?? 0);
  return {
    sequence,
    ok: true,
    summary: `${sentences} ${sentences === 1 ? "sentence" : "sentences"}, `
      + `${tokens} ${tokens === 1 ? "token" : "tokens"}`,
    arrival,
  };
}

/** Returns the value as a record, or {@code undefined}. */
function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}
