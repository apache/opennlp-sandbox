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
  buildStreamFrames,
  readStreamResponse,
  splitBatchDocuments,
} from "../src/batch-analysis";

describe("batch analysis stream frames", () => {
  it("splits blank-line-delimited input into trimmed documents", () => {
    expect(splitBatchDocuments("First doc.\n\n  \t\nSecond doc.\r\n\r\nThird doc.\n"))
      .toEqual(["First doc.", "Second doc.", "Third doc."]);
    expect(splitBatchDocuments("  \n\t\n")).toEqual([]);
  });

  it("builds one configuration frame and one sequenced frame per document", () => {
    const frames = buildStreamFrames(["Hello.", "World."], {
      document: { rawText: "ignored" },
      profile: { steps: ["PIPELINE_STEP_SENTENCE_DETECT"] },
      options: { includeProbabilities: true },
    });

    expect(frames).toEqual([
      {
        configuration: {
          profile: { steps: ["PIPELINE_STEP_SENTENCE_DETECT"] },
          options: { includeProbabilities: true },
        },
      },
      { document: { sequence: "1", document: { docId: "batch-1", rawText: "Hello." } } },
      { document: { sequence: "2", document: { docId: "batch-2", rawText: "World." } } },
    ]);
  });

  it("summarizes a successful response from its analytics", () => {
    const view = readStreamResponse({
      sequence: "2",
      ok: { document: { analytics: { totalSentences: 2, totalTokens: 9 } } },
    }, 1);

    expect(view).toEqual({
      sequence: 2, ok: true, summary: "2 sentences, 9 tokens", arrival: 1,
    });
  });

  it("summarizes a per-document failure without ending the batch", () => {
    const view = readStreamResponse({
      sequence: "3",
      error: { code: "GRPC_STATUS_CODE_INVALID_ARGUMENT", message: "raw_text must not be blank" },
    }, 2);

    expect(view.ok).toBe(false);
    expect(view.sequence).toBe(3);
    expect(view.summary).toContain("raw_text must not be blank");
  });
});
