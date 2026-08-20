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
  readCollection,
  readCollectionEvent,
  readCollectionResponse,
  readCollections,
} from "../src/collection-adapter";

function collectionJson(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    collectionId: "legal",
    displayName: "Legal corpus",
    memberIndexIds: ["workspace-1"],
    vocabularyArtifactId: "vocabulary-1",
    modelArtifactId: "static-model-1",
    driftNewTermThreshold: 25,
    analysisChain: { chainId: "opennlp-terms-codepoint-lower", chainVersion: "1" },
    termLedger: [
      { term: "habeas corpus", occurrences: "12", inVocabulary: true },
      { term: "writ", occurrences: 7, inVocabulary: false },
      { occurrences: 3 },
    ],
    omittedLedgerTerms: 2,
    drift: {
      distinctTerms: "4",
      termOccurrences: "19",
      newTerms: "1",
      newTermOccurrences: "7",
      vocabularyCoverage: 0.63,
    },
    integrityHash: "abc123",
    ...overrides,
  };
}

describe("collection adapter", () => {
  it("reads a descriptor with string-encoded protobuf JSON counts", () => {
    const collection = readCollection(collectionJson());

    expect(collection).toMatchObject({
      id: "legal",
      displayName: "Legal corpus",
      memberIndexIds: ["workspace-1"],
      vocabularyArtifactId: "vocabulary-1",
      modelArtifactId: "static-model-1",
      driftNewTermThreshold: 25,
      analysisChainId: "opennlp-terms-codepoint-lower",
      omittedLedgerTerms: 2,
      integrityHash: "abc123",
      drift: {
        distinctTerms: 4,
        termOccurrences: 19,
        newTerms: 1,
        newTermOccurrences: 7,
        vocabularyCoverage: 0.63,
      },
    });
    expect(collection?.termLedger).toEqual([
      { term: "habeas corpus", occurrences: 12, inVocabulary: true },
      { term: "writ", occurrences: 7, inVocabulary: false },
    ]);
  });

  it("rejects descriptors without a collection id", () => {
    expect(readCollection({ displayName: "No id" })).toBeUndefined();
    expect(readCollection("not an object")).toBeUndefined();
  });

  it("reads listings and single-collection envelopes", () => {
    expect(readCollections({ collections: [collectionJson(), { broken: true }] }))
      .toHaveLength(1);
    expect(readCollectionResponse({ collection: collectionJson() })?.id).toBe("legal");
    expect(readCollectionResponse({})).toBeUndefined();
  });

  it("reads self-contained watch events and drops unknown kinds", () => {
    const persisted = readCollectionEvent({
      kind: "COLLECTION_EVENT_KIND_INDEX_PERSISTED",
      collection: collectionJson(),
      indexId: "workspace-1",
    });
    expect(persisted).toMatchObject({ kind: "index-persisted", indexId: "workspace-1" });
    expect(persisted?.collection.id).toBe("legal");

    expect(readCollectionEvent({
      kind: "COLLECTION_EVENT_KIND_SNAPSHOT",
      collection: collectionJson(),
    })?.kind).toBe("snapshot");
    expect(readCollectionEvent({
      kind: "COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED",
      collection: collectionJson(),
    })?.kind).toBe("drift");
    expect(readCollectionEvent({
      kind: "COLLECTION_EVENT_KIND_MODEL_PUBLISHED",
      collection: collectionJson(),
      modelArtifactId: "static-model-2",
    })?.modelArtifactId).toBe("static-model-2");

    expect(readCollectionEvent({
      kind: "COLLECTION_EVENT_KIND_UNSPECIFIED",
      collection: collectionJson(),
    })).toBeUndefined();
    expect(readCollectionEvent({ kind: "COLLECTION_EVENT_KIND_SNAPSHOT" })).toBeUndefined();
  });
});
