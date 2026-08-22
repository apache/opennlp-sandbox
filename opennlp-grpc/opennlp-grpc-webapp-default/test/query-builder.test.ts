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

import { buildQueryNode, clauseLabel } from "../src/query-builder";

describe("compound query builder", () => {
  it("sends a single clause alone without a join", () => {
    expect(buildQueryNode([{ kind: "semantic", text: "habeas corpus petitions" }], "and"))
      .toEqual({ semantic: { document: { rawText: "habeas corpus petitions" } } });
    expect(buildQueryNode([{ kind: "term", text: "writ", mode: "any" }], "or"))
      .toEqual({ term: { text: "writ", mode: "TERM_MATCH_MODE_ANY" } });
    expect(buildQueryNode([{ kind: "phrase", text: "must issue", slop: 1 }], "and"))
      .toEqual({ phrase: { text: "must issue", slop: 1 } });
  });

  it("nests multiple clauses under one join with the selected algebra", () => {
    const clauses = [
      { kind: "semantic", text: "release from custody" } as const,
      { kind: "term", text: "habeas corpus", mode: "all" } as const,
    ];

    expect(buildQueryNode(clauses, "and")).toEqual({
      join: {
        operator: "JOIN_OPERATOR_AND",
        operands: [
          { semantic: { document: { rawText: "release from custody" } } },
          { term: { text: "habeas corpus", mode: "TERM_MATCH_MODE_ALL" } },
        ],
      },
    });
    expect(buildQueryNode(clauses, "or")).toMatchObject({
      join: { operator: "JOIN_OPERATOR_OR" },
    });
    expect(buildQueryNode(clauses, "rrf")).toMatchObject({
      join: {
        operator: "JOIN_OPERATOR_OR",
        fusion: "JOIN_FUSION_RECIPROCAL_RANK",
      },
    });
  });

  it("rejects empty builders and invalid clauses by position", () => {
    expect(() => buildQueryNode([], "and")).toThrow("Add at least one query clause.");
    expect(() => buildQueryNode([
      { kind: "term", text: "writ", mode: "any" },
      { kind: "semantic", text: "   " },
    ], "and")).toThrow("Clause 2 needs text.");
    expect(() => buildQueryNode([
      { kind: "phrase", text: "must issue", slop: -1 },
    ], "and")).toThrow("Clause 1 needs a slop of zero or more.");
    expect(() => buildQueryNode([
      { kind: "phrase", text: "must issue", slop: 1.5 },
    ], "and")).toThrow("Clause 1 needs a slop of zero or more.");
  });

  it("labels clauses for the builder chip list", () => {
    expect(clauseLabel({ kind: "semantic", text: "custody" })).toBe("semantic: custody");
    expect(clauseLabel({ kind: "term", text: "writ", mode: "all" })).toBe("term (all): writ");
    expect(clauseLabel({ kind: "phrase", text: "must issue", slop: 2 }))
      .toBe("phrase (slop 2): must issue");
  });
});
