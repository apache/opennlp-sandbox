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

/** Drift of indexed member content against the current vocabulary artifact. */
export interface CollectionDrift {
  distinctTerms: number;
  termOccurrences: number;
  newTerms: number;
  newTermOccurrences: number;
  /** Fraction of occurrences hitting vocabulary terms, in [0, 1]. */
  vocabularyCoverage: number;
}

/** One indexed term and its live occurrence count across member indexes. */
export interface TermStatistic {
  term: string;
  occurrences: number;
  inVocabulary: boolean;
}

/** Stable browser view of one collection descriptor. */
export interface CollectionView {
  id: string;
  displayName: string;
  memberIndexIds: string[];
  dictionaryArtifactId?: string;
  vocabularyArtifactId?: string;
  modelArtifactId?: string;
  driftNewTermThreshold: number;
  analysisChainId: string;
  termStatistics: TermStatistic[];
  omittedTermCount: number;
  drift: CollectionDrift;
  integrityHash?: string;
}

/** One self-contained watch event with its complete descriptor snapshot. */
export interface CollectionEventView {
  kind: "snapshot" | "drift" | "index-persisted" | "model-published";
  collection: CollectionView;
  indexId?: string;
  modelArtifactId?: string;
}

/** Reads one collection descriptor JSON defensively. */
export function readCollection(value: unknown): CollectionView | undefined {
  const descriptor = record(value);
  const id = text(descriptor?.collectionId);
  if (!descriptor || !id) {
    return undefined;
  }
  const drift = record(descriptor.drift);
  const chain = record(descriptor.analysisChain);
  return {
    id,
    displayName: text(descriptor.displayName) || id,
    memberIndexIds: stringList(descriptor.memberIndexIds),
    dictionaryArtifactId: optionalText(descriptor.dictionaryArtifactId),
    vocabularyArtifactId: optionalText(descriptor.vocabularyArtifactId),
    modelArtifactId: optionalText(descriptor.modelArtifactId),
    driftNewTermThreshold: count(descriptor.driftNewTermThreshold),
    analysisChainId: text(chain?.chainId),
    termStatistics: readTermStatistics(descriptor.termStatistics),
    omittedTermCount: count(descriptor.omittedTermCount),
    drift: {
      distinctTerms: count(drift?.distinctTerms),
      termOccurrences: count(drift?.termOccurrences),
      newTerms: count(drift?.newTerms),
      newTermOccurrences: count(drift?.newTermOccurrences),
      vocabularyCoverage: fraction(drift?.vocabularyCoverage),
    },
    integrityHash: optionalText(descriptor.integrityHash),
  };
}

/** Reads the collections listing JSON defensively. */
export function readCollections(value: unknown): CollectionView[] {
  const envelope = record(value);
  const values = Array.isArray(envelope?.collections) ? envelope.collections : [];
  return values.flatMap((entry) => {
    const collection = readCollection(entry);
    return collection ? [collection] : [];
  });
}

/** Reads the get-collection or set-collection response envelope. */
export function readCollectionResponse(value: unknown): CollectionView | undefined {
  return readCollection(record(value)?.collection);
}

/** Reads one NDJSON watch event; unknown kinds and malformed events are dropped. */
export function readCollectionEvent(value: unknown): CollectionEventView | undefined {
  const event = record(value);
  const collection = readCollection(event?.collection);
  const kind = eventKind(text(event?.kind));
  if (!event || !collection || !kind) {
    return undefined;
  }
  return {
    kind,
    collection,
    indexId: optionalText(event.indexId),
    modelArtifactId: optionalText(event.modelArtifactId),
  };
}

function eventKind(value: string): CollectionEventView["kind"] | undefined {
  switch (value) {
    case "COLLECTION_EVENT_KIND_SNAPSHOT":
      return "snapshot";
    case "COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED":
      return "drift";
    case "COLLECTION_EVENT_KIND_INDEX_PERSISTED":
      return "index-persisted";
    case "COLLECTION_EVENT_KIND_MODEL_PUBLISHED":
      return "model-published";
    default:
      return undefined;
  }
}

function readTermStatistics(value: unknown): TermStatistic[] {
  const values = Array.isArray(value) ? value : [];
  return values.flatMap((entry) => {
    const statistic = record(entry);
    const term = text(statistic?.term);
    if (!term) {
      return [];
    }
    return [{
      term,
      occurrences: count(statistic?.occurrences),
      inVocabulary: statistic?.inVocabulary === true,
    }];
  });
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function optionalText(value: unknown): string | undefined {
  return text(value) || undefined;
}

function stringList(value: unknown): string[] {
  const values = Array.isArray(value) ? value : [];
  return values.flatMap((entry) => (typeof entry === "string" && entry.trim() ? [entry] : []));
}

/** Reads a count that protobuf JSON may print as a decimal string. */
function count(value: unknown): number {
  const candidate = typeof value === "string" && value.trim() ? Number(value) : value;
  return typeof candidate === "number" && Number.isFinite(candidate)
      && Number.isSafeInteger(candidate) && candidate >= 0
    ? candidate : 0;
}

function fraction(value: unknown): number {
  const candidate = typeof value === "string" && value.trim() ? Number(value) : value;
  return typeof candidate === "number" && Number.isFinite(candidate)
      && candidate >= 0 && candidate <= 1
    ? candidate : 0;
}
