// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

// Analyze documents, build a TurboQuant index, and search it through gRPC.
// Node.js twin of the Python quickstart: the protos load at runtime through
// @grpc/proto-loader, so no code generation step is needed.
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import grpc from "@grpc/grpc-js";
import protoLoader from "@grpc/proto-loader";

const PROTO_ROOT = join(
  dirname(fileURLToPath(import.meta.url)), "..", "..",
  "opennlp-grpc-api", "src", "main", "proto");

const CORPUS = [
  ["habeas",
    "The writ of habeas corpus protects a prisoner from unlawful detention. "
    + "A court may order the custodian to release the prisoner."],
  ["appeal",
    "The appellate court reviews the trial record for reversible error. "
    + "A timely notice preserves the right to appeal."],
  ["zoning",
    "A city may regulate rooftop apiaries through its zoning code. "
    + "The applicant requested a variance for three beehives."],
];

const BASE_STEPS = [
  "PIPELINE_STEP_LANGUAGE_DETECT",
  "PIPELINE_STEP_SENTENCE_DETECT",
  "PIPELINE_STEP_TOKENIZE",
  "PIPELINE_STEP_POS_TAG",
  "PIPELINE_STEP_LEMMATIZE",
  "PIPELINE_STEP_STEM",
  "PIPELINE_STEP_TERM_VECTOR",
];

function parseArgs(argv) {
  const args = { target: "localhost:7071", embeddingModel: null, cleanup: false };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--target") { args.target = argv[++i]; }
    else if (arg === "--embedding-model") { args.embeddingModel = argv[++i]; }
    else if (arg === "--cleanup") { args.cleanup = true; }
    else { throw new Error(`unknown argument: ${arg}`); }
  }
  return args;
}

function loadServices() {
  const definition = protoLoader.loadSync(
    [
      "org/apache/opennlp/grpc/v1/opennlp_service.proto",
      "org/apache/opennlp/grpc/v1/opennlp_search.proto",
    ],
    { includeDirs: [PROTO_ROOT], enums: String, defaults: true, oneofs: true });
  return grpc.loadPackageDefinition(definition).org.apache.opennlp.grpc.v1;
}

// Wraps a callback-style unary stub call as a promise with a deadline.
function call(stub, method, request, timeoutMs) {
  return new Promise((resolve, reject) => {
    stub[method](request, { deadline: Date.now() + timeoutMs },
      (error, response) => (error ? reject(error) : resolve(response)));
  });
}

async function selectEmbeddingModel(analysis, requested) {
  const response = await call(analysis, "listModelBundles", {}, 10_000);
  const models = [...new Set(
    (response.bundles ?? [])
      .flatMap((bundle) => bundle.models ?? [])
      .filter((model) => model.embeddingDimension > 0)
      .map((model) => model.name))].sort();
  if (requested) {
    if (!models.includes(requested)) {
      throw new Error(`embedding model '${requested}' is not configured; `
        + `available: ${models.join(", ") || "none"}`);
    }
    return requested;
  }
  if (models.length === 1) { return models[0]; }
  if (models.length === 0) {
    throw new Error("the server has no embedding model; configure one before running search");
  }
  throw new Error("the server has several embedding models; pass --embedding-model "
    + `with one of: ${models.join(", ")}`);
}

function analysisProfile() {
  return {
    steps: BASE_STEPS,
    stemmer: { algorithm: "STEMMER_ALGORITHM_SNOWBALL", language: "en" },
    termVector: {
      mode: "TERM_VECTOR_MODE_FULL",
      sourceLayer: { standard: "STANDARD_LAYER_LEMMAS" },
    },
  };
}

function chunkConfigs(modelId) {
  return [
    {
      configId: "sentences",
      resultSetName: "Sentence chunks",
      embeddingModelIds: [modelId],
      chunking: { strategy: { standard: "STANDARD_CHUNKING_STRATEGY_SENTENCE" } },
    },
    {
      configId: "token-windows",
      resultSetName: "Eight-token windows",
      embeddingModelIds: [modelId],
      chunking: {
        strategy: { standard: "STANDARD_CHUNKING_STRATEGY_TOKEN" },
        chunkSize: 8,
        chunkOverlap: 2,
      },
    },
  ];
}

async function analyzeDocuments(analysis, modelId) {
  const profile = analysisProfile();
  const chunks = chunkConfigs(modelId);
  const analyzed = [];
  for (const [docId, text] of CORPUS) {
    const response = await call(analysis, "analyzeDocument", {
      document: { docId, rawText: text },
      profile,
      options: { includeProbabilities: true },
      chunkEmbedConfigs: chunks,
    }, 60_000);
    analyzed.push(response.document);
  }
  return analyzed;
}

function printAnalysis(documents) {
  console.log("\nDocument analysis");
  for (const document of documents) {
    const analytics = document.analytics ?? {};
    const layerIds = (document.layers?.layers ?? []).map((layer) => layer.id).join(", ");
    console.log(`  ${document.docId}: ${analytics.totalSentences} sentences, `
      + `${analytics.totalTokens} tokens, ${analytics.uniqueLemmaCount} unique lemmas`);
    const firstTokens = (document.sentences?.[0]?.tokens ?? []).slice(0, 8)
      .map((token) => `${token.text}/${token.posTag}/${token.lemma}`).join(" ");
    console.log(`    first tokens: ${firstTokens}`);
    console.log(`    layers: ${layerIds}`);
  }
}

async function run(target, requestedModel, cleanup) {
  const services = loadServices();
  const channel = grpc.credentials.createInsecure();
  const analysis = new services.OpenNlpAnalysisService(target, channel);
  const search = new services.OpenNlpSearchService(target, channel);
  try {
    const info = await call(analysis, "getServiceInfo", {}, 10_000);
    console.log(`Connected to OpenNLP ${info.opennlpVersion}, API ${info.apiVersion}, `
      + `service ${info.serviceVersion}`);
    console.log(`Server text limit: ${Number(info.maxTextBytes).toLocaleString("en-US")} UTF-8 bytes`);

    const modelId = await selectEmbeddingModel(analysis, requestedModel);
    console.log(`Embedding model: ${modelId}`);
    const documents = await analyzeDocuments(analysis, modelId);
    console.log(`Analyzed ${documents.length} documents`);
    printAnalysis(documents);

    const created = await call(search, "indexDocuments", {
      displayName: "Node.js quickstart corpus",
      documents,
      embedding: { modelId },
      provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" },
    }, 60_000);
    const indexId = created.index.indexId;
    console.log(`\nTurboQuant index ${indexId}: ${created.indexedDocuments} documents, `
      + `${created.indexedChunks} chunks`);
    if (!created.index.supportsAllHits) {
      throw new Error("the TurboQuant index did not advertise exhaustive search");
    }

    try {
      const response = await call(search, "searchIndex", {
        indexId,
        query: {
          docId: "query-1",
          rawText: "Which court remedy protects a prisoner from unlawful custody?",
        },
        allHits: true,
      }, 60_000);
      console.log(`\nSearch results (${response.hits.length} exhaustive hits)`);
      response.hits.forEach((hit, index) => {
        const score = (hit.score >= 0 ? "+" : "") + hit.score.toFixed(4);
        console.log(`  ${String(index + 1).padStart(2)}. ${score}  `
          + `${hit.documentId}/${hit.chunkGroupId}: ${hit.indexedText}`);
      });
      if (response.truncated) {
        console.log("  Results were truncated by the server response-byte limit.");
      }
    } finally {
      if (cleanup) {
        const deleted = await call(search, "deleteSearchIndex", { indexId }, 10_000);
        if (!deleted.deleted) {
          throw new Error(`temporary index ${indexId} was not deleted`);
        }
        console.log(`\nDeleted temporary index ${indexId}`);
      }
    }
  } finally {
    analysis.close();
    search.close();
  }
}

const args = parseArgs(process.argv);
run(args.target, args.embeddingModel, args.cleanup).catch((error) => {
  console.error(error.code !== undefined
    ? `gRPC ${grpc.status[error.code] ?? error.code}: ${error.details || "request failed"}`
    : String(error.message ?? error));
  process.exitCode = 1;
});
