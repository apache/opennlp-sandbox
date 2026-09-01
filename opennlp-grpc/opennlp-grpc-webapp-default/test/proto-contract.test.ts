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

import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

/**
 * Pins the front end's hand-written wire vocabulary to the protos the gateway parses with
 * a strict JSON parser: a misspelled enum literal or request key is a runtime 400 in the
 * browser and nothing else catches it. This test reads the proto sources directly so the
 * front end needs no protobuf runtime.
 */

const PROTO_DIR = join(__dirname, "..", "..", "opennlp-grpc-api", "src", "main", "proto",
  "org", "apache", "opennlp", "grpc", "v1");

interface ProtoVocabulary {
  enumValues: Set<string>;
  fieldJsonNames: Set<string>;
}

/** Reads every enum value and every field JSON name out of the proto sources. */
function readProtoVocabulary(): ProtoVocabulary {
  const enumValues = new Set<string>();
  const fieldJsonNames = new Set<string>();
  for (const file of readdirSync(PROTO_DIR).filter((name) => name.endsWith(".proto")).sort()) {
    let inEnum = false;
    for (const rawLine of readFileSync(join(PROTO_DIR, file), "utf8").split("\n")) {
      const line = stripComment(rawLine).trim();
      if (line.startsWith("enum ")) {
        inEnum = true;
        continue;
      }
      if (line === "}") {
        inEnum = false;
        continue;
      }
      const assignment = line.indexOf(" = ");
      if (assignment < 0 || !line.endsWith(";")) {
        continue;
      }
      const declaration = line.slice(0, assignment).trim();
      if (inEnum) {
        enumValues.add(declaration);
        continue;
      }
      const words = declaration.split(/\s+/);
      const name = words[words.length - 1];
      if (name && words.length >= 2) {
        fieldJsonNames.add(lowerCamel(name));
      }
    }
  }
  return { enumValues, fieldJsonNames };
}

function stripComment(line: string): string {
  const comment = line.indexOf("//");
  return comment < 0 ? line : line.slice(0, comment);
}

/** Protobuf JSON names: snake_case to lowerCamelCase. */
function lowerCamel(name: string): string {
  let out = "";
  let upperNext = false;
  for (const char of name) {
    if (char === "_") {
      upperNext = true;
      continue;
    }
    out += upperNext ? char.toUpperCase() : char;
    upperNext = false;
  }
  return out;
}

const SRC_DIR = join(__dirname, "..", "src");

/** Every double-quoted SCREAMING_SNAKE literal in the sources, with the file that holds it. */
function enumLiteralsInSources(): Map<string, string[]> {
  const literals = new Map<string, string[]>();
  for (const file of readdirSync(SRC_DIR).filter((name) => name.endsWith(".ts")).sort()) {
    const source = readFileSync(join(SRC_DIR, file), "utf8");
    for (const literal of quotedLiterals(source)) {
      if (looksLikeEnumValue(literal)) {
        literals.set(literal, [...(literals.get(literal) ?? []), file]);
      }
    }
  }
  return literals;
}

/** Yields the contents of every double-quoted string in the source. */
function* quotedLiterals(source: string): Generator<string> {
  let index = 0;
  while (index < source.length) {
    if (source[index] === "\"") {
      let end = index + 1;
      while (end < source.length && source[end] !== "\"" && source[end] !== "\n") {
        if (source[end] === "\\") {
          end++;
        }
        end++;
      }
      if (source[end] === "\"") {
        yield source.slice(index + 1, end);
      }
      index = end + 1;
    } else {
      index++;
    }
  }
}

/** An enum value name: upper-case words joined by underscores, at least two underscores. */
function looksLikeEnumValue(literal: string): boolean {
  let underscores = 0;
  for (const char of literal) {
    if (char === "_") {
      underscores++;
    } else if (!(char >= "A" && char <= "Z") && !(char >= "0" && char <= "9")) {
      return false;
    }
  }
  return underscores >= 2 && literal.length > 3;
}

/**
 * Keys of the request interfaces in api.ts, including nested object literals. Those
 * interfaces describe what the gateway parses into protos, so each key must be a field.
 */
function requestInterfaceKeys(): Map<string, string> {
  const keys = new Map<string, string>();
  const source = readFileSync(join(SRC_DIR, "api.ts"), "utf8");
  let currentInterface: string | undefined;
  for (const rawLine of source.split("\n")) {
    const line = rawLine.trim();
    if (line.startsWith("export interface ")) {
      currentInterface = line.split(" ")[2];
      continue;
    }
    if (currentInterface === undefined) {
      continue;
    }
    if (line === "}") {
      currentInterface = undefined;
      continue;
    }
    for (const key of keysOnLine(line)) {
      keys.set(key, currentInterface);
    }
  }
  return keys;
}

/** The property names declared on one interface line, e.g. "embedding: { modelId: string }". */
function keysOnLine(line: string): string[] {
  const found: string[] = [];
  let index = 0;
  while (index < line.length) {
    const start = index;
    while (index < line.length && isIdentifierChar(line[index]!)) {
      index++;
    }
    const word = line.slice(start, index);
    if (word && (line[index] === ":" || (line[index] === "?" && line[index + 1] === ":"))) {
      found.push(word);
    }
    index = index === start ? index + 1 : index;
    while (index < line.length && !isIdentifierChar(line[index]!)) {
      index++;
    }
  }
  return found;
}

function isIdentifierChar(char: string): boolean {
  return (char >= "a" && char <= "z") || (char >= "A" && char <= "Z")
    || (char >= "0" && char <= "9") || char === "_";
}

/** Request keys the gateway defines itself rather than mapping onto a proto field. */
const GATEWAY_OWNED_KEYS = new Set<string>([
  // Streaming uploads wrap a proto "start" frame and the chunked file body.
  "start",
  "file",
]);

/**
 * A literal ending in an underscore is an enum prefix the source strips from wire values;
 * it is declared when at least one enum value starts with it.
 */
function declaresEnum(vocabulary: ProtoVocabulary, literal: string): boolean {
  if (vocabulary.enumValues.has(literal)) {
    return true;
  }
  if (!literal.endsWith("_")) {
    return false;
  }
  for (const value of vocabulary.enumValues) {
    if (value.startsWith(literal)) {
      return true;
    }
  }
  return false;
}

describe("wire vocabulary contract", () => {
  const vocabulary = readProtoVocabulary();

  it("reads a non-trivial vocabulary out of the proto sources", () => {
    expect(vocabulary.enumValues.has("PIPELINE_STEP_SENTIMENT")).toBe(true);
    expect(vocabulary.fieldJsonNames.has("vectorSpaceId")).toBe(true);
    expect(vocabulary.enumValues.size).toBeGreaterThan(100);
    expect(vocabulary.fieldJsonNames.size).toBeGreaterThan(300);
  });

  it("uses only enum values the protos declare", () => {
    const unknown = [...enumLiteralsInSources()]
      .filter(([literal]) => !declaresEnum(vocabulary, literal))
      .map(([literal, files]) => `${literal} (${files.join(", ")})`);
    expect(unknown, "enum literals with no proto declaration").toEqual([]);
    expect(enumLiteralsInSources().size).toBeGreaterThan(40);
  });

  it("names request fields the protos declare", () => {
    const unknown = [...requestInterfaceKeys()]
      .filter(([key]) => !GATEWAY_OWNED_KEYS.has(key) && !vocabulary.fieldJsonNames.has(key))
      .map(([key, owner]) => `${owner}.${key}`);
    expect(unknown, "request keys with no proto field").toEqual([]);
  });
});
