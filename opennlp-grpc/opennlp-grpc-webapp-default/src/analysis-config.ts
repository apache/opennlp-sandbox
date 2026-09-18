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
import type { DiscoveryOption } from "./discovery";

export const PIPELINE_ORDER = [
  "PIPELINE_STEP_LANGUAGE_DETECT",
  "PIPELINE_STEP_NORMALIZE",
  "PIPELINE_STEP_SENTENCE_DETECT",
  "PIPELINE_STEP_TOKENIZE",
  "PIPELINE_STEP_SUBWORD_TOKENIZE",
  "PIPELINE_STEP_NER",
  "PIPELINE_STEP_GEOCODE",
  "PIPELINE_STEP_POS_TAG",
  "PIPELINE_STEP_DEPENDENCY_PARSE",
  "PIPELINE_STEP_RELATION_EXTRACT",
  "PIPELINE_STEP_LEMMATIZE",
  "PIPELINE_STEP_STEM",
  "PIPELINE_STEP_TERM_VECTOR",
  "PIPELINE_STEP_EXPAND",
  "PIPELINE_STEP_DOC_CATEGORIZE",
  "PIPELINE_STEP_SENTIMENT",
  "PIPELINE_STEP_PARSE",
  "PIPELINE_STEP_SYNTACTIC_CHUNK",
  "PIPELINE_STEP_EMBED",
] as const;

export const FEATURE_NAMES: Readonly<Record<string, string>> = {
  PIPELINE_STEP_LANGUAGE_DETECT: "Language detection",
  PIPELINE_STEP_NORMALIZE: "Offset-aware normalization",
  PIPELINE_STEP_SENTENCE_DETECT: "Sentence detection",
  PIPELINE_STEP_TOKENIZE: "Tokenization",
  PIPELINE_STEP_SUBWORD_TOKENIZE: "Subword tokenization",
  PIPELINE_STEP_NER: "Named entities",
  PIPELINE_STEP_GEOCODE: "Entity geocoding",
  PIPELINE_STEP_POS_TAG: "Part-of-speech tags",
  PIPELINE_STEP_DEPENDENCY_PARSE: "Dependency arcs",
  PIPELINE_STEP_RELATION_EXTRACT: "Entity relations",
  PIPELINE_STEP_LEMMATIZE: "Lemmas",
  PIPELINE_STEP_STEM: "Stems",
  PIPELINE_STEP_TERM_VECTOR: "Term vectors",
  PIPELINE_STEP_EXPAND: "Lexical expansion",
  PIPELINE_STEP_DOC_CATEGORIZE: "Document categories",
  PIPELINE_STEP_SENTIMENT: "Sentence sentiment",
  PIPELINE_STEP_PARSE: "Constituency parses",
  PIPELINE_STEP_SYNTACTIC_CHUNK: "Phrase chunks (shallow parse)",
  PIPELINE_STEP_EMBED: "Document embeddings",
};

const MODEL_FREE_STEPS = [
  "PIPELINE_STEP_NORMALIZE",
  "PIPELINE_STEP_STEM",
  "PIPELINE_STEP_TERM_VECTOR",
] as const;

const OFFSET_AWARE_NORMALIZATION = [
  "NORMALIZER_STRIP_INVISIBLE",
  "NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS",
  "NORMALIZER_QUOTES",
  "NORMALIZER_DASHES",
  "NORMALIZER_DIGITS",
  "NORMALIZER_ELLIPSIS",
  "NORMALIZER_BULLETS",
];

const XRAY_STEP = "PIPELINE_STEP_NORMALIZE";
const XRAY_WHITESPACE = "NORMALIZER_WHITESPACE";
const WHITESPACE_VARIANTS = [
  "NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS",
  "NORMALIZER_WHITESPACE_PRESERVE_PARAGRAPHS",
];

/**
 * Merges the x-ray's normalization needs over whatever profile the analysis controls
 * built (an inline profile merges over a named profileId server-side). The x-ray needs
 * offset-transparent normalization with alignment; a profile that already requests
 * either whitespace variant keeps its choice, because the server rejects a request
 * carrying both.
 */
export function withXrayNormalization(
  profile: AnalyzeRequest["profile"],
): NonNullable<AnalyzeRequest["profile"]> {
  const steps = profile?.steps ?? [];
  const requested = profile?.normalization?.normalizers ?? [];
  const normalizers = new Set(["NORMALIZER_STRIP_INVISIBLE", ...requested]);
  if (!WHITESPACE_VARIANTS.some((variant) => normalizers.has(variant))) {
    normalizers.add(XRAY_WHITESPACE);
  }
  return {
    ...profile,
    steps: steps.includes(XRAY_STEP) ? steps : [XRAY_STEP, ...steps],
    normalization: { normalizers: [...normalizers], requireAlignment: true },
  };
}

export interface AnalysisCapabilities {
  profiles: DiscoveryOption[];
  bundles: DiscoveryOption[];
  embeddingModels: DiscoveryOption[];
  maxSteps: string[];
  supportedSteps: string[];
  configuredSteps: string[];
  language?: string;
  maxTextBytes?: number;
  subwordModelId?: string;
  wordnetLexiconId?: string;
  dependencyParserId?: string;
  pipelineLanguages: DiscoveryOption[];
}

export interface AnalysisSelection {
  mode: "max" | "automatic" | "profile" | "custom";
  profileId?: string;
  selectedSteps?: string[];
  sentenceChunks: boolean;
  tokenChunks: boolean;
  tokenChunkSize: number;
  tokenChunkOverlap: number;
  embeddingModelId?: string;
  pipelineLanguage?: string;
  posTagFormat?: string;
}

export function discoverAnalysisCapabilities(
  serviceValue: unknown,
  bundlesValue: unknown,
): AnalysisCapabilities {
  const service = record(serviceValue);
  const bundles = records(findArray(bundlesValue, "bundles"));
  const supported = strings(service?.supportedSteps);
  const configured = new Set<string>();
  const languages: string[] = [];
  const embeddingModels = new Map<string, DiscoveryOption>();
  const dependencyParserIds: string[] = [];

  for (const bundle of bundles) {
    for (const step of strings(bundle.supportedSteps)) {
      configured.add(step);
    }
    for (const language of strings(bundle.supportedLanguages)) {
      if (language !== "root" && !languages.includes(language)) {
        languages.push(language);
      }
    }
    for (const model of records(array(bundle.models))) {
      const componentType = string(model.componentType);
      if (componentType === "COMPONENT_TYPE_DEPENDENCY_PARSER") {
        const id = string(model.name);
        if (id && !dependencyParserIds.includes(id)) {
          dependencyParserIds.push(id);
        }
      }
      if (componentType !== "COMPONENT_TYPE_EMBEDDER") {
        continue;
      }
      const id = string(model.name);
      if (!id) {
        continue;
      }
      const dimension = integer(model.embeddingDimension);
      const backend = string(model.backendId);
      const details: string[] = [];
      if (dimension !== undefined && dimension > 0) {
        details.push(`${dimension}d`);
      }
      if (backend) {
        details.push(backend);
      }
      const label = details.length > 0 ? `${id} (${join(details, ", ")})` : id;
      embeddingModels.set(id, { id, label });
    }
  }

  for (const step of MODEL_FREE_STEPS) {
    if (supported.includes(step) && (step !== "PIPELINE_STEP_STEM" || languages.length > 0)) {
      configured.add(step);
    }
  }

  const resources = records(array(service?.configuredResources));
  const subwordModelId = selectableResource(resources, "STANDARD_RESOURCE_SUBWORD_MODEL");
  const wordnetLexiconId = selectableResource(resources, "STANDARD_RESOURCE_WORDNET_LEXICON");
  if (subwordModelId && supported.includes("PIPELINE_STEP_SUBWORD_TOKENIZE")) {
    configured.add("PIPELINE_STEP_SUBWORD_TOKENIZE");
  }
  if (wordnetLexiconId && supported.includes("PIPELINE_STEP_EXPAND")) {
    configured.add("PIPELINE_STEP_EXPAND");
  }
  if (configured.has("PIPELINE_STEP_NER") && supported.includes("PIPELINE_STEP_GEOCODE")) {
    configured.add("PIPELINE_STEP_GEOCODE");
  }
  const dependencyParserId = dependencyParserIds.length === 1
    ? dependencyParserIds[0]
    : undefined;
  if (dependencyParserId && supported.includes("PIPELINE_STEP_DEPENDENCY_PARSE")) {
    configured.add("PIPELINE_STEP_DEPENDENCY_PARSE");
  }
  if (configured.has("PIPELINE_STEP_NER")
      && configured.has("PIPELINE_STEP_DEPENDENCY_PARSE")
      && supported.includes("PIPELINE_STEP_RELATION_EXTRACT")) {
    configured.add("PIPELINE_STEP_RELATION_EXTRACT");
  }

  const maxSteps = PIPELINE_ORDER.filter((step) => supported.includes(step) && configured.has(step));
  return {
    profiles: options(strings(service?.availableProfileIds)),
    bundles: options(bundles.map((bundle) => string(bundle.bundleId))),
    pipelineLanguages: bundles
      .filter((bundle) => string(bundle.bundleId).startsWith("pipeline-"))
      .map((bundle) => {
        const language = strings(bundle.supportedLanguages)[0]
          ?? string(bundle.bundleId).slice("pipeline-".length);
        return { id: language, label: language };
      }),
    embeddingModels: [...embeddingModels.values()],
    maxSteps,
    supportedSteps: PIPELINE_ORDER.filter((step) => supported.includes(step)),
    configuredSteps: PIPELINE_ORDER.filter((step) => configured.has(step)),
    language: languages[0],
    maxTextBytes: integer(service?.maxTextBytes),
    subwordModelId,
    wordnetLexiconId,
    dependencyParserId,
  };
}

export function buildAnalysisRequest(
  text: string,
  selection: AnalysisSelection,
  capabilities: AnalysisCapabilities,
): AnalyzeRequest {
  validateTokenWindow(selection);
  const request: AnalyzeRequest = {
    document: { rawText: text },
    options: {
      includeProbabilities: true,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    },
  };

  if (selection.mode === "max" || selection.mode === "custom") {
    const customSteps = [...(selection.selectedSteps ?? [])];
    if (selection.sentenceChunks) {
      customSteps.push("PIPELINE_STEP_SENTENCE_DETECT");
    }
    if (selection.tokenChunks) {
      customSteps.push("PIPELINE_STEP_TOKENIZE");
    }
    const selectedSteps = selection.mode === "max"
      ? capabilities.maxSteps
      : dependencyClosure(customSteps, capabilities.maxSteps);
    request.profile = maximalProfile(capabilities, selectedSteps);
    if (selection.embeddingModelId && selectedSteps.includes("PIPELINE_STEP_EMBED")) {
      request.options!.embeddingModelId = selection.embeddingModelId;
      request.options!.includeDocumentCentroid = true;
    }
    if (selectedSteps.includes("PIPELINE_STEP_PARSE")) {
      request.options!.parseFormats = ["PARSE_FORMAT_STRUCTURED", "PARSE_FORMAT_BRACKETED"];
    }
    if (selection.posTagFormat && selectedSteps.includes("PIPELINE_STEP_POS_TAG")) {
      request.profile!.posTagFormat = selection.posTagFormat;
    }
    if (selection.pipelineLanguage) {
      request.profile!.pipelineLanguage = selection.pipelineLanguage;
    }
    if (selectedSteps.includes("PIPELINE_STEP_LANGUAGE_DETECT")) {
      // Five ranked predictions keep the language chips informative without bloat.
      request.options!.rankedLanguageCount = 5;
    }
  } else if (selection.mode === "profile" && selection.profileId) {
    request.profileId = selection.profileId;
  }

  const chunkConfigs = buildChunkConfigs(selection);
  if (chunkConfigs.length > 0) {
    request.chunkEmbedConfigs = chunkConfigs;
  }
  return request;
}

function maximalProfile(
  capabilities: AnalysisCapabilities,
  steps: string[],
): NonNullable<AnalyzeRequest["profile"]> {
  const profile: NonNullable<AnalyzeRequest["profile"]> = { steps };
  if (steps.includes("PIPELINE_STEP_NORMALIZE")) {
    profile.normalization = { normalizers: OFFSET_AWARE_NORMALIZATION };
  }
  if (steps.includes("PIPELINE_STEP_TOKENIZE") && capabilities.language) {
    profile.termProfile = capabilities.language;
    profile.stopwordLanguage = capabilities.language;
  }
  if (steps.includes("PIPELINE_STEP_STEM") && capabilities.language) {
    profile.stemmer = { algorithm: "STEMMER_ALGORITHM_SNOWBALL", language: capabilities.language };
  }
  if (steps.includes("PIPELINE_STEP_TERM_VECTOR")) {
    profile.termVector = {
      mode: "TERM_VECTOR_MODE_FULL",
      sourceLayer: { standard: termVectorSource(steps) },
    };
  }
  if (steps.includes("PIPELINE_STEP_SUBWORD_TOKENIZE") && capabilities.subwordModelId) {
    profile.subwordModelId = capabilities.subwordModelId;
  }
  if (steps.includes("PIPELINE_STEP_EXPAND") && capabilities.wordnetLexiconId) {
    profile.wordnetLexiconId = capabilities.wordnetLexiconId;
  }
  if (steps.includes("PIPELINE_STEP_DEPENDENCY_PARSE") && capabilities.dependencyParserId) {
    profile.dependencyParserId = capabilities.dependencyParserId;
  }
  if (steps.includes("PIPELINE_STEP_RELATION_EXTRACT")) {
    profile.relationPatterns = [
      { type: "subject-object", path: "<nsubj >obj" },
      { type: "subject-oblique", path: "<nsubj >obl" },
    ];
  }
  return profile;
}

const STEP_DEPENDENCIES: Readonly<Record<string, readonly string[]>> = {
  PIPELINE_STEP_TOKENIZE: ["PIPELINE_STEP_SENTENCE_DETECT"],
  PIPELINE_STEP_NER: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_GEOCODE: ["PIPELINE_STEP_NER"],
  PIPELINE_STEP_POS_TAG: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_DEPENDENCY_PARSE: ["PIPELINE_STEP_POS_TAG"],
  PIPELINE_STEP_RELATION_EXTRACT: ["PIPELINE_STEP_NER", "PIPELINE_STEP_DEPENDENCY_PARSE"],
  PIPELINE_STEP_LEMMATIZE: ["PIPELINE_STEP_POS_TAG"],
  PIPELINE_STEP_STEM: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_TERM_VECTOR: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_EXPAND: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_DOC_CATEGORIZE: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_SENTIMENT: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_PARSE: ["PIPELINE_STEP_TOKENIZE"],
  PIPELINE_STEP_SYNTACTIC_CHUNK: ["PIPELINE_STEP_POS_TAG"],
  PIPELINE_STEP_EMBED: ["PIPELINE_STEP_SENTENCE_DETECT"],
};

function dependencyClosure(selected: string[], available: string[]): string[] {
  const allowed = new Set(available);
  const result = new Set(selected.filter((step) => allowed.has(step)));
  const visit = (step: string): void => {
    for (const dependency of STEP_DEPENDENCIES[step] ?? []) {
      if (!allowed.has(dependency) || result.has(dependency)) {
        continue;
      }
      result.add(dependency);
      visit(dependency);
    }
  };
  for (const step of [...result]) {
    visit(step);
  }
  return PIPELINE_ORDER.filter((step) => result.has(step));
}

function buildChunkConfigs(selection: AnalysisSelection): NonNullable<AnalyzeRequest["chunkEmbedConfigs"]> {
  const result: NonNullable<AnalyzeRequest["chunkEmbedConfigs"]> = [];
  const embeddingModelIds = selection.embeddingModelId ? [selection.embeddingModelId] : undefined;
  if (selection.sentenceChunks) {
    result.push({
      configId: "sentence-chunks",
      resultSetName: "Sentence chunks",
      chunking: {
        strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
        cleanText: true,
        preserveUrls: true,
      },
      ...(embeddingModelIds ? { embeddingModelIds } : {}),
    });
  }
  if (selection.tokenChunks) {
    result.push({
      configId: "token-chunks",
      resultSetName: "Token windows",
      chunking: {
        strategy: { standard: "STANDARD_CHUNKING_STRATEGY_TOKEN" },
        chunkSize: selection.tokenChunkSize,
        chunkOverlap: selection.tokenChunkOverlap,
        cleanText: true,
        preserveUrls: true,
      },
      ...(embeddingModelIds ? { embeddingModelIds } : {}),
    });
  }
  return result;
}

function validateTokenWindow(selection: AnalysisSelection): void {
  if (!selection.tokenChunks) {
    return;
  }
  if (!Number.isInteger(selection.tokenChunkSize) || selection.tokenChunkSize < 1) {
    throw new Error("Token window must be a positive whole number.");
  }
  if (!Number.isInteger(selection.tokenChunkOverlap) || selection.tokenChunkOverlap < 0) {
    throw new Error("Token overlap must be a non-negative whole number.");
  }
  if (selection.tokenChunkOverlap >= selection.tokenChunkSize) {
    throw new Error("Token overlap must be smaller than the token window.");
  }
}

function termVectorSource(steps: string[]): string {
  if (steps.includes("PIPELINE_STEP_STEM")) {
    return "STANDARD_LAYER_STEMS";
  }
  if (steps.includes("PIPELINE_STEP_LEMMATIZE")) {
    return "STANDARD_LAYER_LEMMAS";
  }
  return "STANDARD_LAYER_TOKENS";
}

function selectableResource(resources: Record<string, unknown>[], type: string): string | undefined {
  const matches = resources.filter((resource) => {
    const identity = record(resource.identity);
    return identity !== undefined && string(identity.standard) === type;
  });
  const selected = matches.find((resource) => resource.isDefault === true) ?? (matches.length === 1 ? matches[0] : undefined);
  return selected ? string(selected.resourceId) || undefined : undefined;
}

function options(values: string[]): DiscoveryOption[] {
  return values.filter((value) => value.length > 0).map((value) => ({ id: value, label: value }));
}

function findArray(value: unknown, key: string): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  const object = record(value);
  return object ? array(object[key]) : [];
}

function records(values: unknown[]): Record<string, unknown>[] {
  const result: Record<string, unknown>[] = [];
  for (const value of values) {
    const object = record(value);
    if (object) {
      result.push(object);
    }
  }
  return result;
}

function strings(value: unknown): string[] {
  return array(value).map(string).filter((item) => item.length > 0);
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function string(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function integer(value: unknown): number | undefined {
  return typeof value === "number" && Number.isSafeInteger(value) ? value : undefined;
}

function join(values: string[], separator: string): string {
  let result = "";
  for (const value of values) {
    result += result ? separator + value : value;
  }
  return result;
}
