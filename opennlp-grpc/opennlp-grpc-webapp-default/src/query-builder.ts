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

/** One clause of the visual compound query builder. */
export type QueryClause =
  | { kind: "semantic"; text: string }
  | { kind: "term"; text: string; mode: "any" | "all" }
  | { kind: "phrase"; text: string; slop: number };

/**
 * How multiple clauses combine: every clause (AND, mean score), any clause
 * (OR, maximum score), or any clause fused by reciprocal rank for components whose
 * score scales are not comparable.
 */
export type JoinMode = "and" | "or" | "rrf";

/**
 * Builds the protobuf JSON QueryNode tree for the composed clauses. A single
 * clause is sent alone; multiple clauses nest under one join.
 *
 * Throws an Error naming the first invalid clause: blank text, or a negative
 * or non-integer phrase slop.
 */
export function buildQueryNode(
  clauses: readonly QueryClause[],
  join: JoinMode,
): Record<string, unknown> {
  if (clauses.length === 0) {
    throw new Error("Add at least one query clause.");
  }
  const nodes = clauses.map((clause, index) => clauseNode(clause, index + 1));
  if (nodes.length === 1) {
    return nodes[0] as Record<string, unknown>;
  }
  return {
    join: {
      operator: join === "and" ? "JOIN_OPERATOR_AND" : "JOIN_OPERATOR_OR",
      operands: nodes,
      ...(join === "rrf" ? { fusion: "JOIN_FUSION_RECIPROCAL_RANK" } : {}),
    },
  };
}

/** Renders one clause for a compact chip label in the builder list. */
export function clauseLabel(clause: QueryClause): string {
  if (clause.kind === "semantic") {
    return `semantic: ${clause.text}`;
  }
  if (clause.kind === "term") {
    return `term (${clause.mode}): ${clause.text}`;
  }
  return `phrase (slop ${clause.slop}): ${clause.text}`;
}

function clauseNode(clause: QueryClause, position: number): Record<string, unknown> {
  if (!clause.text.trim()) {
    throw new Error(`Clause ${position} needs text.`);
  }
  if (clause.kind === "semantic") {
    return { semantic: { document: { rawText: clause.text } } };
  }
  if (clause.kind === "term") {
    return {
      term: {
        text: clause.text,
        mode: clause.mode === "all" ? "TERM_MATCH_MODE_ALL" : "TERM_MATCH_MODE_ANY",
      },
    };
  }
  if (!Number.isInteger(clause.slop) || clause.slop < 0) {
    throw new Error(`Clause ${position} needs a slop of zero or more.`);
  }
  return { phrase: { text: clause.text, slop: clause.slop } };
}
