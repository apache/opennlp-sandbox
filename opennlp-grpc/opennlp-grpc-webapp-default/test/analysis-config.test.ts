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

import { buildAnalysisRequest, discoverAnalysisCapabilities, withXrayNormalization } from "../src/analysis-config";

const serviceInfo = {
  availableProfileIds: ["en-basic", "en-ner"],
  supportedSteps: [
    "PIPELINE_STEP_LANGUAGE_DETECT",
    "PIPELINE_STEP_NORMALIZE",
    "PIPELINE_STEP_SENTENCE_DETECT",
    "PIPELINE_STEP_TOKENIZE",
    "PIPELINE_STEP_NER",
    "PIPELINE_STEP_GEOCODE",
    "PIPELINE_STEP_SENTIMENT",
    "PIPELINE_STEP_POS_TAG",
    "PIPELINE_STEP_DEPENDENCY_PARSE",
    "PIPELINE_STEP_RELATION_EXTRACT",
    "PIPELINE_STEP_LEMMATIZE",
    "PIPELINE_STEP_STEM",
    "PIPELINE_STEP_TERM_VECTOR",
    "PIPELINE_STEP_EMBED",
    "PIPELINE_STEP_CHUNK",
    "PIPELINE_STEP_SUBWORD_TOKENIZE",
    "PIPELINE_STEP_EXPAND",
  ],
  configuredResources: [
    {
      identity: { standard: "STANDARD_RESOURCE_SUBWORD_MODEL" },
      resourceId: "legal-sp",
      isDefault: true,
    },
    {
      identity: { standard: "STANDARD_RESOURCE_WORDNET_LEXICON" },
      resourceId: "wordnet",
      isDefault: true,
    },
  ],
  maxTextBytes: 1048576,
};

const bundlesInfo = {
  bundles: [
    {
      bundleId: "en-basic",
      supportedLanguages: ["en"],
      supportedSteps: [
        "PIPELINE_STEP_LANGUAGE_DETECT",
        "PIPELINE_STEP_SENTENCE_DETECT",
        "PIPELINE_STEP_TOKENIZE",
        "PIPELINE_STEP_POS_TAG",
        "PIPELINE_STEP_LEMMATIZE",
        "PIPELINE_STEP_EMBED",
      ],
      models: [
        {
          name: "legal-mini",
          componentType: "COMPONENT_TYPE_EMBEDDER",
          embeddingDimension: 384,
          backendId: "static",
        },
      ],
    },
    {
      bundleId: "en-ner",
      supportedLanguages: ["en"],
      supportedSteps: [
        "PIPELINE_STEP_SENTENCE_DETECT",
        "PIPELINE_STEP_TOKENIZE",
        "PIPELINE_STEP_NER",
      ],
      models: [],
    },
    {
      bundleId: "en-dependency",
      supportedLanguages: ["en"],
      supportedSteps: [
        "PIPELINE_STEP_SENTENCE_DETECT",
        "PIPELINE_STEP_TOKENIZE",
        "PIPELINE_STEP_POS_TAG",
        "PIPELINE_STEP_DEPENDENCY_PARSE",
      ],
      models: [{
        name: "english-dependency",
        componentType: "COMPONENT_TYPE_DEPENDENCY_PARSER",
      }],
    },
  ],
};

describe("analysis capability planning", () => {
  it("builds a richest-safe feature set from configured bundles and resources", () => {
    const capabilities = discoverAnalysisCapabilities(serviceInfo, bundlesInfo);

    expect(capabilities.language).toBe("en");
    expect(capabilities.maxTextBytes).toBe(1048576);
    expect(capabilities.embeddingModels).toEqual([{ id: "legal-mini", label: "legal-mini (384d, static)" }]);
    expect(capabilities.maxSteps).toEqual([
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
      "PIPELINE_STEP_EMBED",
    ]);
    expect(capabilities.supportedSteps).toContain("PIPELINE_STEP_SENTIMENT");
    expect(capabilities.configuredSteps).not.toContain("PIPELINE_STEP_SENTIMENT");
    expect(capabilities.subwordModelId).toBe("legal-sp");
    expect(capabilities.wordnetLexiconId).toBe("wordnet");
  });

  it("derives configured language pipelines from pipeline bundles", () => {
    const capabilities = discoverAnalysisCapabilities(serviceInfo, {
      bundles: [
        { bundleId: "en-basic", supportedLanguages: ["en"], supportedSteps: [] },
        { bundleId: "pipeline-de", supportedLanguages: ["de"], supportedSteps: [] },
        { bundleId: "pipeline-fr", supportedLanguages: ["fr"], supportedSteps: [] },
      ],
    });

    expect(capabilities.pipelineLanguages).toEqual([
      { id: "de", label: "de" },
      { id: "fr", label: "fr" },
    ]);
  });

  it("carries the pipeline language, tag set, and ranked-language request", () => {
    const request = buildAnalysisRequest(
      "Die Katzen schlafen.",
      {
        mode: "max",
        sentenceChunks: false,
        tokenChunks: false,
        tokenChunkSize: 96,
        tokenChunkOverlap: 12,
        pipelineLanguage: "de",
        posTagFormat: "POS_TAG_FORMAT_UD",
      },
      discoverAnalysisCapabilities(serviceInfo, bundlesInfo),
    );

    expect(request.profile?.pipelineLanguage).toBe("de");
    expect(request.profile?.posTagFormat).toBe("POS_TAG_FORMAT_UD");
    expect(request.options?.rankedLanguageCount).toBe(5);
  });

  it("turns every safe configured feature on and requests both chunk views", () => {
    const request = buildAnalysisRequest(
      "OpenNLP makes documents inspectable.",
      {
        mode: "max",
        sentenceChunks: true,
        tokenChunks: true,
        tokenChunkSize: 96,
        tokenChunkOverlap: 12,
        embeddingModelId: "legal-mini",
      },
      discoverAnalysisCapabilities(serviceInfo, bundlesInfo),
    );

    expect(request.profile?.steps).toContain("PIPELINE_STEP_NER");
    expect(request.profile?.normalization).toEqual({
      normalizers: [
        "NORMALIZER_STRIP_INVISIBLE",
        "NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS",
        "NORMALIZER_QUOTES",
        "NORMALIZER_DASHES",
        "NORMALIZER_DIGITS",
        "NORMALIZER_ELLIPSIS",
        "NORMALIZER_BULLETS",
      ],
    });
    expect(request.profile?.stemmer).toEqual({ algorithm: "STEMMER_ALGORITHM_SNOWBALL", language: "en" });
    expect(request.profile?.termProfile).toBe("en");
    expect(request.profile?.stopwordLanguage).toBe("en");
    expect(request.profile?.subwordModelId).toBe("legal-sp");
    expect(request.profile?.wordnetLexiconId).toBe("wordnet");
    expect(request.profile?.dependencyParserId).toBe("english-dependency");
    expect(request.profile?.relationPatterns).toEqual([
      { type: "subject-object", path: "<nsubj >obj" },
      { type: "subject-oblique", path: "<nsubj >obl" },
    ]);
    expect(request.options).toMatchObject({
      includeProbabilities: true,
      embeddingModelId: "legal-mini",
      includeDocumentCentroid: true,
      offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
    });
    expect(request.chunkEmbedConfigs).toEqual([
      {
        configId: "sentence-chunks",
        resultSetName: "Sentence chunks",
        chunking: {
          strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
          cleanText: true,
          preserveUrls: true,
        },
        embeddingModelIds: ["legal-mini"],
      },
      {
        configId: "token-chunks",
        resultSetName: "Token windows",
        chunking: {
          strategy: { standard: "STANDARD_CHUNKING_STRATEGY_TOKEN" },
          chunkSize: 96,
          chunkOverlap: 12,
          cleanText: true,
          preserveUrls: true,
        },
        embeddingModelIds: ["legal-mini"],
      },
    ]);
  });

  it("supports named profiles and either chunk strategy independently", () => {
    const capabilities = discoverAnalysisCapabilities(serviceInfo, bundlesInfo);
    const request = buildAnalysisRequest(
      "One sentence.",
      {
        mode: "profile",
        profileId: "en-ner",
        sentenceChunks: false,
        tokenChunks: true,
        tokenChunkSize: 32,
        tokenChunkOverlap: 0,
      },
      capabilities,
    );

    expect(request.profile).toBeUndefined();
    expect(request.profileId).toBe("en-ner");
    expect(request.chunkEmbedConfigs).toHaveLength(1);
    expect(request.chunkEmbedConfigs?.[0]?.configId).toBe("token-chunks");
    expect(request.chunkEmbedConfigs?.[0]?.embeddingModelIds).toBeUndefined();
  });

  it("expands custom feature dependencies in canonical pipeline order", () => {
    const capabilities = discoverAnalysisCapabilities(serviceInfo, bundlesInfo);
    const request = buildAnalysisRequest(
      "Alice visited Boston.",
      {
        mode: "custom",
        selectedSteps: ["PIPELINE_STEP_NER", "PIPELINE_STEP_LEMMATIZE"],
        sentenceChunks: false,
        tokenChunks: false,
        tokenChunkSize: 32,
        tokenChunkOverlap: 0,
      },
      capabilities,
    );

    expect(request.profile?.steps).toEqual([
      "PIPELINE_STEP_SENTENCE_DETECT",
      "PIPELINE_STEP_TOKENIZE",
      "PIPELINE_STEP_NER",
      "PIPELINE_STEP_POS_TAG",
      "PIPELINE_STEP_LEMMATIZE",
    ]);
  });

  it("does not attach token configuration to a token-free custom profile", () => {
    const request = buildAnalysisRequest(
      "Normalized text",
      {
        mode: "custom",
        selectedSteps: ["PIPELINE_STEP_NORMALIZE"],
        sentenceChunks: false,
        tokenChunks: false,
        tokenChunkSize: 32,
        tokenChunkOverlap: 0,
      },
      discoverAnalysisCapabilities(serviceInfo, bundlesInfo),
    );

    expect(request.profile?.termProfile).toBeUndefined();
    expect(request.profile?.stopwordLanguage).toBeUndefined();
  });

  it("adds chunking backbone steps to a custom profile", () => {
    const request = buildAnalysisRequest(
      "Chunk this document",
      {
        mode: "custom",
        selectedSteps: [],
        sentenceChunks: true,
        tokenChunks: true,
        tokenChunkSize: 32,
        tokenChunkOverlap: 4,
      },
      discoverAnalysisCapabilities(serviceInfo, bundlesInfo),
    );

    expect(request.profile?.steps).toEqual([
      "PIPELINE_STEP_SENTENCE_DETECT",
      "PIPELINE_STEP_TOKENIZE",
    ]);
  });

  it("rejects invalid token windows before sending a request", () => {
    const capabilities = discoverAnalysisCapabilities(serviceInfo, bundlesInfo);
    expect(() => buildAnalysisRequest(
      "Text",
      {
        mode: "max",
        sentenceChunks: false,
        tokenChunks: true,
        tokenChunkSize: 8,
        tokenChunkOverlap: 8,
      },
      capabilities,
    )).toThrow("Token overlap must be smaller than the token window");
  });
});

describe("x-ray normalization merge", () => {
  it("keeps the profile's whitespace variant instead of requesting both", () => {
    const merged = withXrayNormalization({
      steps: ["PIPELINE_STEP_NORMALIZE", "PIPELINE_STEP_TOKENIZE"],
      normalization: { normalizers: [
        "NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS",
        "NORMALIZER_QUOTES",
      ] },
    });
    const normalizers = merged.normalization?.normalizers ?? [];
    expect(normalizers).toContain("NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS");
    expect(normalizers).not.toContain("NORMALIZER_WHITESPACE");
    expect(normalizers).toContain("NORMALIZER_STRIP_INVISIBLE");
    expect(normalizers).toContain("NORMALIZER_QUOTES");
    expect(merged.normalization?.requireAlignment).toBe(true);
  });

  it("keeps a requested paragraph-preserving whitespace variant", () => {
    const merged = withXrayNormalization({
      steps: ["PIPELINE_STEP_NORMALIZE"],
      normalization: { normalizers: ["NORMALIZER_WHITESPACE_PRESERVE_PARAGRAPHS"] },
    });
    const normalizers = merged.normalization?.normalizers ?? [];
    expect(normalizers).toContain("NORMALIZER_WHITESPACE_PRESERVE_PARAGRAPHS");
    expect(normalizers).not.toContain("NORMALIZER_WHITESPACE");
  });

  it("adds the whitespace default when the profile requests neither variant", () => {
    const merged = withXrayNormalization({ steps: [] });
    expect(merged.steps[0]).toBe("PIPELINE_STEP_NORMALIZE");
    expect(merged.normalization?.normalizers).toContain("NORMALIZER_WHITESPACE");
  });

  it("does not duplicate an already requested normalize step", () => {
    const merged = withXrayNormalization({ steps: ["PIPELINE_STEP_NORMALIZE"] });
    expect(merged.steps.filter((step) => step === "PIPELINE_STEP_NORMALIZE")).toHaveLength(1);
  });
});
