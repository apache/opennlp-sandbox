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

import "./style.css";

import {
  analyze,
  analyzeProgressively,
  analyzeToProtobuf,
  analyzeStream,
  decodeAnalyzeResponsePb,
  deleteCollection,
  deleteIndexAlias,
  deleteStaticModel,
  downloadVocabularyTsv,
  encodeAnalyzeResponsePb,
  getCollection,
  getCollections,
  getDictionaries,
  getVocabularies,
  getDictionaryFormats,
  getHealth,
  getIndexAliases,
  getInstalledModels,
  getModelCatalog,
  getModelBundles,
  getSearchIndexes,
  getSearchProviders,
  getServiceInfo,
  getStaticModels,
  getTeachers,
  getUiExtensions,
  importDictionary,
  installModel,
  indexDocuments,
  learnVocabulary,
  deleteSearchIndex,
  persistIndex,
  reindexIndex,
  searchIndex,
  sealIndex,
  setCollection,
  setIndexAlias,
  trainStaticModel,
  watchCollection,
  type AnalyzeRequest,
} from "./api";
import {
  buildStreamFrames,
  readStreamResponse,
  splitBatchDocuments,
} from "./batch-analysis";
import { readCollectionEvent, readCollectionResponse, readCollections } from "./collection-adapter";
import { LifecycleWorkbench } from "./lifecycle-workbench";
import {
  buildAnalysisRequest,
  withXrayNormalization,
  type AnalysisCapabilities,
} from "./analysis-config";
import { AnalysisControls } from "./analysis-controls";
import { AnnotationDrawer } from "./annotation-drawer";
import { ChunkProjectionView } from "./chunk-projection-view";
import { CorpusWorkflowWorkbench } from "./corpus-workflow";
import {
  combinedAnnotationSegments,
  countRawDocumentShape,
  documentAnnotationChips,
  isDefaultOverlayLayer,
  layerAccent,
  readDocumentShape,
  analysisCompletionMessage,
  summarizeDocumentShape,
  type AnnotationEntry,
  type AnnotationLayerView,
  type AnnotationView,
  type CombinedAnnotationSegment,
  type DocumentShapeView,
} from "./document-shape";
import {
  documentWindow,
  pageForDocumentOffset,
} from "./document-window";
import { readNormalizationXray, renderNormalizationXray } from "./normalization-xray";
import {
  applyProgressiveEvent,
  displayPipelineStep,
  emptyProgressiveAnalysis,
  type ProgressiveAnalysisState,
} from "./progressive-analysis";
import { ProgressiveRenderQueue } from "./progressive-render-queue";
import { isTermVectorLayer, renderTermVectorStack } from "./term-vector-stack";
import { SemanticWorkbench, type ResultViewName } from "./semantic-workbench";
import { initThemeToggle } from "./theme-toggle";
import {
  ModelDataWorkbench,
  readInstalledModel,
  readInstalledModels,
  readModelCatalog,
  readModelInstallProgress,
} from "./model-data-workbench";
import {
  readIndexAliases,
  readIndexResponse,
  readSearchIndexes,
  readSearchProviderInstances,
  readSearchProviderListing,
  readSearchResponse,
} from "./search-adapter";
import { ServerSearchWorkbench } from "./server-search-workbench";
import { asciiLowerCase, collapseWhitespace, formatInteger } from "./text-utils";
import {
  activeUiExtension,
  extensionInitials,
  readUiExtensions,
  type UiExtension,
} from "./ui-extensions";
import {
  analysisFailureMessage,
  errorMessage,
  flashButtonLabel,
  requiredElement,
} from "./ui-utils";
import {
  readDictionaryFormats,
  readDictionaries,
  readVocabularies,
  readImportedDictionary,
  readLearnedVocabulary,
  readStaticModels,
  readTeachers,
  readTrainedModel,
  VocabularyTrainerWorkbench,
} from "./vocabulary-trainer";
import { tabTargetIndex, WorkbenchNavigation } from "./workbench-navigation";
import { loadAliceDemo, loadPrideAndPrejudiceDemo } from "./demo-data";
import {
  jsonPresentation,
  LARGE_COPY_MESSAGE,
  LARGE_PB_MESSAGE,
  SERVER_PB_MESSAGE,
} from "./json-response";

const sampleText =
  "Apache OpenNLP helps developers build applications that process natural language. " +
  "The project provides tokenization, sentence detection, part-of-speech tagging, named entity recognition, and more.";

const form = requiredElement<HTMLFormElement>("analysis-form");
const textArea = requiredElement<HTMLTextAreaElement>("analysis-text");
const analyzeButton = requiredElement<HTMLButtonElement>("analyze-button");
const sampleButton = requiredElement<HTMLButtonElement>("sample-button");
const aliceSampleButton = requiredElement<HTMLButtonElement>("alice-sample-button");
const prideSampleButton = requiredElement<HTMLButtonElement>("pride-sample-button");
const copyButton = requiredElement<HTMLButtonElement>("copy-button");
const downloadButton = requiredElement<HTMLButtonElement>("download-button");
const downloadPbButton = requiredElement<HTMLButtonElement>("download-pb-button");
const loadResponseButton = requiredElement<HTMLButtonElement>("load-response-button");
const loadResponseInput = requiredElement<HTMLInputElement>("load-response-input");
const responseOutput = requiredElement<HTMLElement>("response-output");
const formStatus = requiredElement<HTMLElement>("form-status");
const serviceStatus = requiredElement<HTMLElement>("service-status");
const serviceDescription = requiredElement<HTMLElement>("service-description");
const serviceName = requiredElement<HTMLElement>("service-name");
const profileCount = requiredElement<HTMLElement>("profile-count");
const modelCount = requiredElement<HTMLElement>("model-count");
const pipelineLanguageCount = requiredElement<HTMLElement>("pipeline-language-count");
const languageSummary = requiredElement<HTMLElement>("analysis-language-summary");
const routedPipelineBadge = requiredElement<HTMLElement>("routed-pipeline-badge");
const rankedLanguageChips = requiredElement<HTMLElement>("ranked-language-chips");
const batchText = requiredElement<HTMLTextAreaElement>("batch-text");
const batchButton = requiredElement<HTMLButtonElement>("batch-analyze-button");
const batchStatus = requiredElement<HTMLElement>("batch-status");
const batchResults = requiredElement<HTMLOListElement>("batch-results");
const characterCount = requiredElement<HTMLElement>("character-count");
const layerList = requiredElement<HTMLElement>("layer-list");
const layerSummary = requiredElement<HTMLElement>("layer-summary");
const annotatedText = requiredElement<HTMLElement>("annotated-text");
const documentWindowControls = requiredElement<HTMLElement>("document-window-controls");
const documentWindowPosition = requiredElement<HTMLInputElement>("document-window-position");
const documentWindowLabel = requiredElement<HTMLElement>("document-window-label");
const xrayToggle = requiredElement<HTMLInputElement>("normalization-xray-toggle");
const normalizationXray = requiredElement<HTMLElement>("normalization-xray");
const documentView = requiredElement<HTMLElement>("document-view");
const chunksView = requiredElement<HTMLElement>("chunks-view");
const heatmapView = requiredElement<HTMLElement>("heatmap-view");
const graphView = requiredElement<HTMLElement>("graph-view");
const jsonView = requiredElement<HTMLElement>("json-view");
const resultTabs = Array.from(document.querySelectorAll<HTMLButtonElement>("[data-result-tab]"));
const analysisResultPanel = requiredElement<HTMLElement>("analysis-result-panel");
const layerFilter = requiredElement<HTMLInputElement>("layer-filter");
const resultLayerCount = requiredElement<HTMLElement>("result-layer-count");
const resultAnnotationCount = requiredElement<HTMLElement>("result-annotation-count");
const resultOffsetEncoding = requiredElement<HTMLElement>("result-offset-encoding");
const toolNavigation = requiredElement<HTMLElement>("tool-navigation");
const toolNavigationStatus = requiredElement<HTMLElement>("tool-navigation-status");

initThemeToggle(requiredElement<HTMLButtonElement>("theme-toggle"));

let serviceAvailable = false;
let busy = false;
let currentJson = "";
let currentResponse: unknown;
/** The request behind the current response, so the server can re-run it for a large .pb. */
let currentRequest: AnalyzeRequest | undefined;
let currentShape: DocumentShapeView | undefined;
let currentLayer: AnnotationLayerView | undefined;
let currentCombinedSegments: CombinedAnnotationSegment[] = [];
let currentHighlightSegments: CombinedAnnotationSegment[] = [];
let currentOverlayKind: "all" | "highlights" = "all";
let workflowCapabilities: AnalysisCapabilities | undefined;

const analysisControls = new AnalysisControls(updateFormState);
const annotationDrawer = new AnnotationDrawer();
const catalogEmbeddingModels = new Map<string, string>();
const trainedEmbeddingModels = new Map<string, string>();
const modelDataWorkbench = new ModelDataWorkbench({
  listCatalog: async () => readModelCatalog(await getModelCatalog()),
  listInstalled: async () => readInstalledModels(await getInstalledModels()),
  install: async (request, onProgress) => readInstalledModel(
    await installModel(request, (progress) => onProgress(readModelInstallProgress(progress))),
  ),
}, {
  onEmbeddingModelInstalled: (modelId, displayName) => {
    catalogEmbeddingModels.set(modelId, `${displayName} (catalog)`);
    publishRuntimeEmbeddingModels();
  },
  onTeacherInstalled: () => void vocabularyTrainer.initialize(),
  onCatalogLoaded: (fixers) => analysisControls.setFeatureFixers(fixers),
});
const chunkProjectionView = new ChunkProjectionView((group, chunk, trigger) => {
  annotationDrawer.showChunk(group, chunk, trigger);
});
const workbenchNavigation = new WorkbenchNavigation();
workbenchNavigation.onFocus("models", (step) => modelDataWorkbench.focus(step));

const vocabularyTrainer = new VocabularyTrainerWorkbench({
  listDictionaryFormats: async () => readDictionaryFormats(await getDictionaryFormats()),
  listDictionaries: async () => readDictionaries(await getDictionaries()),
  listVocabularies: async () => readVocabularies(await getVocabularies()),
  importDictionary: async (upload) => readImportedDictionary(await importDictionary(upload)),
  learnVocabulary: async (upload) => readLearnedVocabulary(await learnVocabulary(upload)),
  downloadVocabulary: (artifactId) => downloadVocabularyTsv(artifactId),
  listTeachers: async () => readTeachers(await getTeachers()),
  trainStaticModel: async (request, onProgress) =>
    readTrainedModel(await trainStaticModel(request, onProgress)),
  listStaticModels: async () => readStaticModels(await getStaticModels()),
  deleteStaticModel: async (artifactId) => {
    await deleteStaticModel(artifactId);
    return true;
  },
}, {
  onModelsChanged: (models) => {
    trainedEmbeddingModels.clear();
    for (const model of models) {
      trainedEmbeddingModels.set(model.artifactId, `${model.displayName} (trained)`);
    }
    publishRuntimeEmbeddingModels();
  },
  onUseInAnalyze: (model) => {
    const selected = analysisControls.selectEmbeddingModel(model.artifactId);
    workbenchNavigation.show("analysis");
    setFormStatus(selected
      ? `'${model.displayName}' is selected as the embedding model. Analyze text to use it.`
      : `'${model.displayName}' is not offered as an embedding model on this server.`, !selected);
  },
});
void vocabularyTrainer.initialize();
void modelDataWorkbench.initialize();

function publishRuntimeEmbeddingModels(): void {
  const merged = new Map([...catalogEmbeddingModels, ...trainedEmbeddingModels]);
  analysisControls.setTrainedEmbeddingModels(Array.from(merged, ([id, label]) => ({ id, label })));
}

const semanticWorkbench = new SemanticWorkbench({
  listIndexes: async () => readSearchIndexes(await getSearchIndexes()),
  index: async (request) => {
    const response = await indexDocuments(request) as Record<string, unknown>;
    const index = readSearchIndexes({ indexes: response.index ? [response.index] : [] })[0];
    if (!index) {
      throw new Error("The server returned an invalid live index descriptor.");
    }
    return index;
  },
  search: async (request) => readSearchResponse(await searchIndex(request)),
  deleteIndex: async (indexId) => { await deleteSearchIndex(indexId); },
  confirmDelete: (label) => window.confirm(`Delete the live index '${label}' on the server? `
    + "Its indexed chunks are removed; the documents on this page are kept."),
  onWorkspacesChanged: () => void lifecycleWorkbench.initialize(),
  onIndexed: (message, error) => {
    setFormStatus(message, error);
    if (!error) {
      const jump = document.createElement("button");
      jump.type = "button";
      jump.className = "link-button";
      jump.dataset.workbenchJump = "session-search";
      jump.textContent = "Search it on Live index search";
      formStatus.append(" ", jump);
    }
  },
  openDocument: (hit) => {
    workbenchNavigation.show("analysis");
    const shape = readDocumentShape(hit.sourceDocument);
    textArea.value = shape.rawText;
    updateFormState();
    renderDocumentShape(shape);
    normalizationXray.hidden = true;
    const response = { document: hit.sourceDocument };
    storeResponse(response, shape);
    chunkProjectionView.render({ document: hit.sourceDocument });
    semanticWorkbench.setDocument(hit.documentId, shape, response);
    selectResultTab("document");
  },
  inspectChunk: (hit, shape, trigger) => annotationDrawer.showSearchHit(hit, shape, trigger),
  inspectSpan: (shape, start, end, text, trigger) =>
    annotationDrawer.showDocumentSpan(shape, start, end, text, trigger),
  selectAnnotation: selectAnnotationFromGraph,
});

const lifecycleWorkbench = new LifecycleWorkbench({
  listIndexes: async () => readSearchIndexes(await getSearchIndexes()),
  listProviders: async () => readSearchProviderListing(await getSearchProviders()),
  listAliases: async () => readIndexAliases(await getIndexAliases()),
  persist: async (indexId) => readIndexResponse(await persistIndex(indexId)),
  seal: async (indexId) => readIndexResponse(await sealIndex(indexId)),
  reindex: async (request) => readIndexResponse(await reindexIndex(request)),
  setAlias: async (alias, indexId) => { await setIndexAlias(alias, indexId); },
  deleteAlias: async (alias) => { await deleteIndexAlias(alias); },
  listStaticModels: async () => readStaticModels(await getStaticModels()),
  listDictionaries: async () => readDictionaries(await getDictionaries()),
  listVocabularies: async () => readVocabularies(await getVocabularies()),
  listCollections: async () => readCollections(await getCollections()),
  getCollection: async (collectionId) => readCollectionResponse(await getCollection(collectionId)),
  setCollection: async (request) => readCollectionResponse(await setCollection(request)),
  deleteCollection: async (collectionId) => {
    const response = await deleteCollection(collectionId) as Record<string, unknown>;
    return response.deleted === true;
  },
  watchCollection: (collectionId, onEvent) =>
    watchCollection(collectionId, (event) => onEvent(readCollectionEvent(event))),
});
void lifecycleWorkbench.initialize();
void getSearchProviders().then(
  (listing) => semanticWorkbench.setAvailability(
    readSearchProviderListing(listing).dynamicIndexingEnabled),
  () => undefined);

const serverSearchWorkbench = new ServerSearchWorkbench({
  listIndexes: async () => readSearchIndexes(await getSearchIndexes()),
  search: async (request) => readSearchResponse(await searchIndex(request)),
  analyzeSource: async (hit) => readDocumentShape(await analyze({
    document: {
      docId: hit.documentId,
      rawText: hit.sourceText,
    },
    options: { offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
  })),
});

const corpusWorkflow = new CorpusWorkflowWorkbench({
  listDictionaries: async () => readDictionaries(await getDictionaries()),
  listTeachers: async () => readTeachers(await getTeachers()),
  listProviders: async () => readSearchProviderInstances(await getSearchProviders()),
  analyze,
  learnVocabulary: async (upload) => readLearnedVocabulary(await learnVocabulary(upload)),
  trainStaticModel: async (request, onProgress) =>
    readTrainedModel(await trainStaticModel(request, onProgress)),
  index: async (request) => {
    const response = await indexDocuments(request) as Record<string, unknown>;
    const index = readSearchIndexes({ indexes: response.index ? [response.index] : [] })[0];
    if (!index) {
      throw new Error("The server returned an invalid built index descriptor.");
    }
    return index;
  },
  search: async (request) => readSearchResponse(await searchIndex(request)),
}, {
  createAnalysisRequest: (document, embeddingModelId) => {
    if (!workflowCapabilities) {
      throw new Error("Analysis capabilities are still loading.");
    }
    const request = buildAnalysisRequest(document.rawText, {
      mode: "max",
      sentenceChunks: Boolean(embeddingModelId),
      tokenChunks: false,
      tokenChunkSize: 96,
      tokenChunkOverlap: 12,
      embeddingModelId,
    }, workflowCapabilities);
    request.document.docId = document.docId;
    return request;
  },
  onModelTrained: (model) => {
    trainedEmbeddingModels.set(model.artifactId, `${model.displayName} (trained)`);
    publishRuntimeEmbeddingModels();
  },
  defaultEmbeddingModel: () => {
    const configured = workflowCapabilities?.embeddingModels[0];
    if (configured) {
      return { id: configured.id, label: configured.label };
    }
    const runtime = [...catalogEmbeddingModels, ...trainedEmbeddingModels][0];
    return runtime ? { id: runtime[0], label: runtime[1] } : undefined;
  },
  onOpenAnalysis: (response, shape) => {
    textArea.value = shape.rawText;
    updateFormState();
    storeResponse(response, shape);
    chunkProjectionView.render(response);
    renderDocumentShape(shape);
    renderXray(response);
    semanticWorkbench.setDocument("Built index document", shape, response);
    selectResultTab("document");
    workbenchNavigation.show("analysis");
    revealAnalysisResult();
  },
  onIndexChanged: () => {
    void serverSearchWorkbench.initialize();
    void semanticWorkbench.initializeWorkspaces();
    void lifecycleWorkbench.initialize();
  },
});
void corpusWorkflow.initialize();
document.getElementById("workflow-sample-button")?.addEventListener("click", () => {
  void loadAliceDemo().then((novel) => {
    const corpus = requiredElement<HTMLTextAreaElement>("workflow-corpus");
    corpus.value = sampleDocuments(novel, 6);
    corpus.dispatchEvent(new Event("input", { bubbles: true }));
  }, (error) => setFormStatus(errorMessage(error, "The sample could not be loaded."), true));
});

textArea.addEventListener("input", updateFormState);
sampleButton.addEventListener("click", () => {
  textArea.value = sampleText;
  updateFormState();
  textArea.focus();
});
aliceSampleButton.addEventListener("click", () => void loadAliceSample());
prideSampleButton.addEventListener("click", () => void loadPrideSample());
form.addEventListener("submit", submitAnalysis);
batchText.addEventListener("input", () => {
  batchButton.disabled = busy || !serviceAvailable
      || splitBatchDocuments(batchText.value).length === 0;
});
batchButton.addEventListener("click", () => void submitBatch());
copyButton.addEventListener("click", copyResponse);
downloadButton.addEventListener("click", downloadResponse);
downloadPbButton.addEventListener("click", () => void downloadResponsePb());
loadResponseButton.addEventListener("click", () => loadResponseInput.click());
loadResponseInput.addEventListener("change", () => {
  const file = loadResponseInput.files?.[0];
  if (file) {
    void loadLocalResponse(file);
  }
});
layerFilter.addEventListener("input", filterLayerButtons);
documentWindowPosition.addEventListener("input", renderCurrentDocumentWindow);
for (const tab of resultTabs) {
  tab.addEventListener("click", () => selectResultTab(resultViewName(tab.dataset.resultTab)));
  tab.addEventListener("keydown", navigateResultTabs);
}

void initialize();

async function initialize(): Promise<void> {
  void initializeToolNavigation();
  setServiceState("loading", "Connecting");
  setFormStatus("Checking service capabilities and model packs.");

  try {
    await getHealth();
  } catch (error) {
    serviceAvailable = false;
    setServiceState("unavailable", "Unavailable");
    serviceName.textContent = "Offline";
    serviceDescription.textContent = "The web interface is running, but the analysis service could not be reached.";
    setFormStatus(errorMessage(error, "The analysis service is unavailable."), true);
    updateFormState();
    return;
  }

  serviceAvailable = true;
  setServiceState("ready", "Connected");
  void serverSearchWorkbench.initialize();
  void semanticWorkbench.initializeWorkspaces().catch(() => {
    // The picker keeps its "new live index" default when discovery is unavailable.
  });
  const [infoResult, bundlesResult] = await Promise.allSettled([getServiceInfo(), getModelBundles()]);
  const serviceInfo = infoResult.status === "fulfilled" ? infoResult.value : undefined;
  const bundlesInfo = bundlesResult.status === "fulfilled" ? bundlesResult.value : undefined;
  const capabilities = analysisControls.configure(serviceInfo, bundlesInfo);
  workflowCapabilities = capabilities;
  corpusWorkflow.refreshMode();
  modelDataWorkbench.configure(capabilities);
  const profiles = capabilities.profiles;
  const bundles = capabilities.bundles;
  profileCount.textContent = String(profiles.length);
  modelCount.textContent = String(bundles.length);
  const pipelineLanguages = capabilities.pipelineLanguages.map((pipeline) => pipeline.id);
  pipelineLanguageCount.textContent =
      [capabilities.language ?? "en", ...pipelineLanguages].join(", ");
  serviceName.textContent = discoverServiceName(serviceInfo);

  const discoveryErrors = [infoResult, bundlesResult].filter((result) => result.status === "rejected");
  if (discoveryErrors.length > 0) {
    serviceDescription.textContent = "Connected. Some discovery information is not currently available.";
    setFormStatus("Connected with limited discovery. Automatic configuration is still available.");
  } else {
    serviceDescription.textContent = "Connected and ready. Available profiles and models were loaded from the service.";
    setFormStatus("Ready. Enter text or load the sample to begin.");
  }
  updateFormState();
}

async function initializeToolNavigation(): Promise<void> {
  try {
    const extensions = readUiExtensions(await getUiExtensions());
    if (extensions.length === 0) {
      toolNavigationStatus.textContent = "No UI extensions were discovered. Showing the default tool.";
      return;
    }
    renderToolNavigation(extensions);
    toolNavigationStatus.textContent = `${extensions.length} UI `
      + `${extensions.length === 1 ? "extension" : "extensions"} available.`;
  } catch {
    toolNavigationStatus.textContent = "UI extension discovery is unavailable. Showing the default tool.";
  }
}

function renderToolNavigation(extensions: UiExtension[]): void {
  const activeId = activeUiExtension(extensions, window.location.pathname);
  toolNavigation.hidden = extensions.length <= 1;
  toolNavigation.replaceChildren(...extensions.map((extension) => {
    const link = document.createElement("a");
    link.href = extension.mountPath;
    if (extension.id === activeId) {
      link.className = "is-active";
      link.setAttribute("aria-current", "page");
    }

    const icon = document.createElement("span");
    icon.className = "tool-icon";
    icon.setAttribute("aria-hidden", "true");
    icon.textContent = extensionInitials(extension.title);

    const label = document.createElement("span");
    const title = document.createElement("strong");
    title.textContent = extension.title;
    const mount = document.createElement("small");
    mount.textContent = extension.mountPath === "/" ? "Default extension" : extension.mountPath;
    label.append(title, mount);
    link.append(icon, label);
    return link;
  }));
}

/**
 * Streams every pasted batch document through one AnalyzeStream call under the
 * current configuration, rendering results in completion order as they arrive.
 */
async function submitBatch(): Promise<void> {
  const documents = splitBatchDocuments(batchText.value);
  if (documents.length === 0 || busy || !serviceAvailable) {
    return;
  }
  batchResults.replaceChildren();
  batchButton.disabled = true;
  batchStatus.textContent = `Streaming ${documents.length} `
      + `${documents.length === 1 ? "document" : "documents"}…`;
  let arrivals = 0;
  try {
    const request = createAnalysisRequest("batch", false);
    await analyzeStream(buildStreamFrames(documents, request), (response) => {
      arrivals++;
      const view = readStreamResponse(response, arrivals);
      const item = document.createElement("li");
      item.className = view.ok ? "batch-result" : "batch-result is-error";
      item.textContent = `Document ${view.sequence}: ${view.summary}`;
      batchResults.append(item);
    });
    batchStatus.textContent = `Analyzed ${arrivals} of ${documents.length} `
        + `${documents.length === 1 ? "document" : "documents"} in completion order.`;
  } catch (error) {
    batchStatus.textContent = errorMessage(error, "The batch stream failed.");
  } finally {
    batchButton.disabled = splitBatchDocuments(batchText.value).length === 0;
  }
}

async function submitAnalysis(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const text = textArea.value.trim();
  if (!text || busy || !serviceAvailable) {
    return;
  }
  const request = createAnalysisRequest(text);
  const textBytes = new TextEncoder().encode(text).length;
  const limit = workflowCapabilities?.maxTextBytes;
  if (limit && textBytes > limit) {
    setFormStatus(`This document is ${mebibytes(textBytes)} MiB; the server accepts at most `
      + `${mebibytes(limit)} MiB per request (server.max_text_bytes). Split it or use batch analysis.`, true);
    return;
  }
  if (textBytes > LARGE_EMBEDDING_WARNING_BYTES && requestsEmbeddings(request)
      && !window.confirm(`This document is ${mebibytes(textBytes)} MiB and embeddings are on. `
        + "The reply can reach hundreds of megabytes and take a minute; the JSON view and Copy "
        + "switch off past the browser's limit, and Download .pb re-runs the analysis on the "
        + "server. Analyze anyway?")) {
    return;
  }

  setBusy(true);
  setFormStatus("Starting progressive analysis…");
  responseOutput.textContent = "Waiting for the first analysis layers…";
  currentJson = "";
  currentResponse = undefined;
  currentRequest = request;
  copyButton.disabled = true;
  downloadButton.disabled = true;
  downloadPbButton.disabled = true;
  const renderQueue = new ProgressiveRenderQueue(
    (state) => renderProgressiveState(state, request));
  try {
    let progressive = emptyProgressiveAnalysis();
    let revealed = false;
    const response = await analyzeProgressively(request, (streamEvent) => {
      progressive = applyProgressiveEvent(progressive, streamEvent);
      if (progressive.complete) {
        return;
      }
      renderQueue.schedule(progressive);
      if (!revealed) {
        selectResultTab("document");
        revealAnalysisResult();
        revealed = true;
      }
    });
    renderQueue.cancel();
    const shape = readDocumentShape(response);
    storeResponse(response, shape);
    currentRequest = request;
    chunkProjectionView.render(response);
    renderDocumentShape(shape);
    renderXray(response);
    semanticWorkbench.setDocument(text, shape, response);
    selectResultTab("document");
    if (progressive.failures.length > 0) {
      setFormStatus(`Analysis finished, but ${progressive.failures.length === 1 ? "one step" : `${progressive.failures.length} steps`} `
        + `failed: ${progressive.failures.join(" ")}`, true);
    } else {
      setFormStatus(analysisCompletionMessage(summarizeDocumentShape(shape)));
    }
    revealAnalysisResult();
  } catch (error) {
    currentJson = "";
    currentResponse = undefined;
    currentRequest = undefined;
    copyButton.disabled = true;
    downloadButton.disabled = true;
    downloadPbButton.disabled = true;
    normalizationXray.hidden = true;
    responseOutput.textContent = "The analysis request did not complete.";
    setFormStatus(analysisFailureMessage(error), true);
  } finally {
    renderQueue.cancel();
    setBusy(false);
  }
}

/**
 * Annotations beyond which intermediate frames are not drawn: rebuilding the overlay for
 * every event on a novel-sized document takes longer than the stream itself and stalls the
 * reader, so only the counts update until the final response arrives.
 */
const PROGRESSIVE_RENDER_ANNOTATION_LIMIT = 20_000;

/** Renders the currently available layer set without serializing a partial JSON reply. */
function renderProgressiveState(
  state: ProgressiveAnalysisState,
  request: AnalyzeRequest,
): void {
  const changed = new Set(state.updatedLayerIds);
  currentResponse = state.response;
  currentRequest = request;
  const counts = countRawDocumentShape(state.response);
  if (counts.annotationCount <= PROGRESSIVE_RENDER_ANNOTATION_LIMIT) {
    const shape = readDocumentShape(state.response);
    renderDocumentShape(shape);
  } else {
    resultLayerCount.textContent = String(counts.layerCount);
    resultAnnotationCount.textContent = String(counts.annotationCount);
  }
  if (state.sequence === 1 || changed.has("opennlp:chunk-groups")) {
    chunkProjectionView.render(state.response);
  }
  if (state.sequence === 1 || changed.has("opennlp:normalization")) {
    renderXray(state.response);
  }
  if (state.sequence === 1 || changed.has("opennlp:language")) {
    renderLanguageSummary(state.response);
  }
  responseOutput.textContent = `Streaming progressive results: ${counts.layerCount} `
    + `${counts.layerCount === 1 ? "layer" : "layers"} ready.`;
  if (state.failures.length > 0) {
    setFormStatus(state.failures[state.failures.length - 1]!, true);
  } else if (state.lastStep) {
    setFormStatus(`${displayPipelineStep(state.lastStep)} ready; other analysis continues.`);
  } else {
    setFormStatus("Document accepted; analysis branches are running.");
  }
}

/**
 * Brings the result panel into view after an analysis, so the answer is not
 * left below the fold. jsdom has no scrollIntoView, hence the guard.
 */
function revealAnalysisResult(): void {
  if (typeof analysisResultPanel.scrollIntoView === "function") {
    analysisResultPanel.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

async function loadAliceSample(): Promise<void> {
  aliceSampleButton.disabled = true;
  setFormStatus("Loading the compressed public-domain Alice demo.");
  try {
    textArea.value = await loadAliceDemo();
    updateFormState();
    textArea.focus();
    setFormStatus("Alice’s Adventures in Wonderland loaded. All configured features are ready to run.");
  } catch (error) {
    setFormStatus(errorMessage(error, "Could not load the Alice demo."), true);
  } finally {
    aliceSampleButton.disabled = false;
  }
}

async function loadPrideSample(): Promise<void> {
  prideSampleButton.disabled = true;
  setFormStatus("Loading the compressed public-domain Pride and Prejudice demo.");
  try {
    textArea.value = await loadPrideAndPrejudiceDemo();
    updateFormState();
    textArea.focus();
    setFormStatus("Pride and Prejudice loaded. All configured features are ready to run.");
  } catch (error) {
    setFormStatus(errorMessage(error, "Could not load the Pride and Prejudice demo."), true);
  } finally {
    prideSampleButton.disabled = false;
  }
}

function renderDocumentShape(shape: DocumentShapeView): void {
  currentShape = shape;
  currentLayer = undefined;
  currentCombinedSegments = [];
  currentHighlightSegments = [];
  layerList.replaceChildren();
  annotatedText.replaceChildren();
  annotationDrawer.reset();
  layerSummary.textContent = `${shape.layers.length} ${shape.layers.length === 1 ? "layer" : "layers"}`;
  const summary = summarizeDocumentShape(shape);
  resultLayerCount.textContent = String(summary.layerCount);
  resultAnnotationCount.textContent = String(summary.annotationCount);
  resultOffsetEncoding.textContent = summary.offsetEncodingLabel;
  configureDocumentWindow(shape.rawText.length);
  layerFilter.value = "";
  layerFilter.disabled = shape.layers.length === 0;

  if (!shape.rawText) {
    annotatedText.textContent = "The response did not contain document text.";
    return;
  }
  if (shape.layers.length === 0) {
    annotatedText.textContent = shape.rawText;
    annotationDrawer.showMessage("This analysis returned no annotation layers.");
    return;
  }

  // A calmer default view exists only when entities or sentences are a proper
  // subset of the layers; otherwise Highlights and All would be the same view.
  const highlightLayers = shape.layers.filter(isDefaultOverlayLayer);
  const offerHighlights = highlightLayers.length > 0 && highlightLayers.length < shape.layers.length;
  if (offerHighlights) {
    const highlightsButton = document.createElement("button");
    highlightsButton.type = "button";
    highlightsButton.className = "layer-button";
    highlightsButton.dataset.layerKind = "highlights";
    highlightsButton.dataset.searchText = "highlights entities sentences";
    highlightsButton.dataset.accent = "blue";
    highlightsButton.setAttribute("aria-pressed", "false");
    const highlightsName = document.createElement("span");
    highlightsName.textContent = "Highlights";
    const highlightsCount = document.createElement("small");
    highlightsCount.textContent = String(highlightLayers
      .reduce((total, layer) => total + layer.annotations.length, 0));
    highlightsButton.append(highlightsName, highlightsCount);
    highlightsButton.title = "Entities and sentences only; select All annotations for every layer";
    highlightsButton.addEventListener("click", () => selectHighlightLayers(shape));
    layerList.append(highlightsButton);
  }

  const allButton = document.createElement("button");
  allButton.type = "button";
  allButton.className = "layer-button";
  allButton.dataset.layerKind = "all";
  allButton.dataset.searchText = "all annotations combined";
  allButton.dataset.accent = "blue";
  allButton.setAttribute("aria-pressed", "false");
  const allName = document.createElement("span");
  allName.textContent = "All annotations";
  const allCount = document.createElement("small");
  allCount.textContent = String(summary.annotationCount);
  allButton.append(allName, allCount);
  allButton.title = "Every returned annotation layer combined";
  allButton.addEventListener("click", () => selectAllLayers(shape));
  layerList.append(allButton);

  for (const layer of shape.layers) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "layer-button";
    button.dataset.layerKind = "layer";
    button.dataset.layerId = layer.id;
    button.dataset.searchText = asciiLowerCase(`${layer.id} ${layer.title} ${layer.valueType}`);
    button.dataset.accent = layerAccent(layer);
    button.setAttribute("aria-pressed", "false");
    const name = document.createElement("span");
    name.textContent = layer.title;
    const count = document.createElement("small");
    count.textContent = String(layer.annotations.length);
    button.append(name, count);
    button.title = `${layer.id}, ${layer.valueType}`;
    if (layer.annotations.length === 0) {
      button.dataset.empty = "true";
      button.title += ", no annotations in this response";
    }
    button.addEventListener("click", () => selectLayer(shape, layer));
    layerList.append(button);
  }
  if (offerHighlights) {
    selectHighlightLayers(shape);
  } else {
    selectAllLayers(shape);
  }
}

function selectLayer(shape: DocumentShapeView, layer: AnnotationLayerView): void {
  currentLayer = layer;
  for (const button of layerList.querySelectorAll<HTMLButtonElement>(".layer-button")) {
    button.setAttribute("aria-pressed", String(button.dataset.layerId === layer.id));
  }
  annotationDrawer.describeLayer(layer);
  annotatedText.replaceChildren();
  annotatedText.dataset.accent = layerAccent(layer);
  annotatedText.setAttribute("aria-label", `${layer.title} annotations over document text`);

  const view = currentDocumentWindow(shape.rawText.length);
  const positional = layer.annotations
    .filter((annotation) => hasUsableSpan(annotation)
      && annotation.end! > view.start && annotation.start! < view.end)
    .sort((left, right) => left.start! - right.start! || left.end! - right.end!);
  let cursor = view.start;
  for (const annotation of positional) {
    const start = Math.max(cursor, view.start, Math.min(annotation.start!, view.end));
    const end = Math.max(start, Math.min(annotation.end!, view.end));
    if (end <= cursor) {
      continue;
    }
    appendText(shape.rawText.slice(cursor, start));
    const marker = document.createElement("button");
    marker.type = "button";
    marker.className = "annotation-marker";
    marker.dataset.accent = layerAccent(layer);
    marker.textContent = shape.rawText.slice(start, end);
    marker.title = annotation.label;
    marker.setAttribute("aria-label", `${marker.textContent}: ${annotation.label}`);
    marker.addEventListener("click", () => annotationDrawer.showAnnotation(layer, annotation, marker));
    annotatedText.append(marker);
    cursor = end;
  }
  appendText(shape.rawText.slice(cursor, view.end));

  if (positional.length === 0) {
    annotatedText.textContent = shape.rawText.slice(view.start, view.end);
    annotationDrawer.describeLayer(layer, layer.annotations.some(hasUsableSpan)
      ? "This layer has no selectable text spans in the current document window."
      : "This document-scoped layer has no selectable text spans.");
  }
  if (isTermVectorLayer(layer) && layer.annotations.length > 0) {
    annotatedText.prepend(renderTermVectorStack(layer, layerAccent(layer),
      (trigger) => annotationDrawer.showTermVectorList(layer, trigger)));
    annotationDrawer.describeLayer(layer,
      "All term vectors combine into the stacked bar above the text; select it for the ranked term list.");
  }
}

function selectAllLayers(shape: DocumentShapeView): void {
  if (currentCombinedSegments.length === 0) {
    currentCombinedSegments = combinedAnnotationSegments(shape);
  }
  renderCombinedOverlay(shape, "all", currentCombinedSegments,
    "All typed annotations over document text");
}

/** Renders the calm default overlay: entity and sentence layers only. */
function selectHighlightLayers(shape: DocumentShapeView): void {
  if (currentHighlightSegments.length === 0) {
    currentHighlightSegments = combinedAnnotationSegments({
      ...shape,
      layers: shape.layers.filter(isDefaultOverlayLayer),
    });
  }
  renderCombinedOverlay(shape, "highlights", currentHighlightSegments,
    "Entity and sentence annotations over document text");
}

function renderCombinedOverlay(
  shape: DocumentShapeView,
  kind: "all" | "highlights",
  segments: CombinedAnnotationSegment[],
  ariaLabel: string,
): void {
  currentLayer = undefined;
  currentOverlayKind = kind;
  for (const button of layerList.querySelectorAll<HTMLButtonElement>(".layer-button")) {
    button.setAttribute("aria-pressed", String(button.dataset.layerKind === kind));
  }
  annotatedText.replaceChildren();
  annotatedText.dataset.accent = "blue";
  annotatedText.setAttribute("aria-label", ariaLabel);

  const documentEntries = documentAnnotationChips(shape)
    .filter((entry) => !isTermVectorLayer(entry.layer));
  const termVectorLayers = shape.layers
    .filter((layer) => isTermVectorLayer(layer) && layer.annotations.length > 0);
  if (documentEntries.length > 0 || termVectorLayers.length > 0) {
    const scoped = document.createElement("section");
    scoped.className = "document-annotation-strip";
    const heading = document.createElement("strong");
    heading.textContent = "Document-wide results";
    const chips = document.createElement("div");
    for (const entry of documentEntries) {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "document-annotation-chip";
      chip.dataset.accent = layerAccent(entry.layer);
      chip.textContent = `${entry.layer.title}: ${entry.annotation.label}`;
      if (entry.totalCount > 1) {
        const distribution = document.createElement("small");
        distribution.textContent = `top of ${entry.totalCount}`;
        chip.append(" ", distribution);
        chip.addEventListener("click",
          () => annotationDrawer.showCategoryDistribution(entry.layer, chip));
      } else {
        chip.addEventListener("click",
          () => annotationDrawer.showAnnotation(entry.layer, entry.annotation, chip));
      }
      chips.append(chip);
    }
    scoped.append(heading, chips);
    for (const layer of termVectorLayers) {
      scoped.append(renderTermVectorStack(layer, layerAccent(layer),
        (trigger) => annotationDrawer.showTermVectorList(layer, trigger)));
    }
    annotatedText.append(scoped);
  }

  const view = currentDocumentWindow(shape.rawText.length);
  let cursor = view.start;
  for (const segment of segments) {
    if (segment.end <= view.start || segment.start >= view.end) {
      continue;
    }
    const start = Math.max(segment.start, view.start);
    const end = Math.min(segment.end, view.end);
    appendText(shape.rawText.slice(cursor, start));
    const text = shape.rawText.slice(start, end);
    if (!text.trim()) {
      appendText(text);
    } else {
      const marker = document.createElement("button");
      marker.type = "button";
      marker.className = "annotation-marker annotation-marker-combined";
      marker.dataset.accent = combinedAccent(segment.entries);
      marker.textContent = text;
      const layerNames = [...new Set(segment.entries.map((entry) => entry.layer.title))];
      marker.title = layerNames.join(", ");
      marker.setAttribute("aria-label", `${text}: ${segment.entries.length} typed annotations`);
      marker.addEventListener("click", () => annotationDrawer.showAnnotations(
        text, start, end, segment.entries, marker,
      ));
      annotatedText.append(marker);
    }
    cursor = end;
  }
  appendText(shape.rawText.slice(cursor, view.end));
}

function combinedAccent(entries: AnnotationEntry[]): ReturnType<typeof layerAccent> {
  const accents = new Set(entries.map((entry) => layerAccent(entry.layer)));
  for (const accent of ["violet", "green", "rose", "amber", "cyan", "blue"] as const) {
    if (accents.has(accent)) {
      return accent;
    }
  }
  return "blue";
}

function selectAnnotationFromGraph(layerId: string, annotationIndex: number): void {
  if (!currentShape) {
    return;
  }
  const layer = currentShape.layers.find((candidate) => candidate.id === layerId);
  const annotation = layer?.annotations[annotationIndex];
  if (!layer || !annotation) {
    return;
  }
  if (annotation.start !== undefined) {
    documentWindowPosition.value = String(pageForDocumentOffset(annotation.start));
    updateDocumentWindowLabel(currentDocumentWindow(currentShape.rawText.length));
  }
  selectResultTab("document");
  selectLayer(currentShape, layer);
  annotationDrawer.showAnnotation(layer, annotation);
}

function configureDocumentWindow(textLength: number): void {
  const view = documentWindow(textLength, 0);
  documentWindowPosition.value = "0";
  documentWindowPosition.max = String(view.pageCount - 1);
  documentWindowControls.hidden = view.pageCount === 1;
  updateDocumentWindowLabel(view);
}

function currentDocumentWindow(textLength: number): ReturnType<typeof documentWindow> {
  return documentWindow(textLength, documentWindowPosition.valueAsNumber);
}

function updateDocumentWindowLabel(view: ReturnType<typeof documentWindow>): void {
  documentWindowLabel.textContent = view.pageCount === 1
    ? `Complete document, ${formatInteger(view.end)} characters`
    : `Characters ${formatInteger(view.start + 1)} to ${formatInteger(view.end)} of `
      + `${formatInteger(currentShape?.rawText.length ?? view.end)}, window ${view.page + 1} of ${view.pageCount}`;
}

function renderCurrentDocumentWindow(): void {
  if (!currentShape) {
    return;
  }
  const view = currentDocumentWindow(currentShape.rawText.length);
  updateDocumentWindowLabel(view);
  annotatedText.scrollTop = 0;
  if (currentLayer) {
    selectLayer(currentShape, currentLayer);
  } else if (currentOverlayKind === "highlights") {
    selectHighlightLayers(currentShape);
  } else {
    selectAllLayers(currentShape);
  }
}

function filterLayerButtons(): void {
  if (!currentShape) {
    return;
  }
  const query = asciiLowerCase(layerFilter.value.trim());
  const buttons = Array.from(layerList.querySelectorAll<HTMLButtonElement>(".layer-button"));
  const layerButtons = buttons.filter((button) => button.dataset.layerKind === "layer");
  let visibleCount = 0;
  for (const button of buttons) {
    const visible = !query || button.dataset.searchText?.includes(query) === true;
    button.hidden = !visible;
    if (visible && button.dataset.layerKind === "layer") {
      visibleCount++;
    }
  }
  layerSummary.textContent = query
    ? `${visibleCount} of ${layerButtons.length} layers`
    : `${layerButtons.length} ${layerButtons.length === 1 ? "layer" : "layers"}`;

  const selected = buttons.find((button) => button.getAttribute("aria-pressed") === "true");
  if (selected?.hidden) {
    const next = buttons.find((button) => !button.hidden);
    if (next?.dataset.layerKind === "all") {
      selectAllLayers(currentShape);
    } else if (next?.dataset.layerKind === "highlights") {
      selectHighlightLayers(currentShape);
    } else {
      const layer = currentShape.layers.find((candidate) => candidate.id === next?.dataset.layerId);
      if (layer) {
        selectLayer(currentShape, layer);
      }
    }
  }
}

function appendText(value: string): void {
  if (value) {
    annotatedText.append(document.createTextNode(value));
  }
}

function hasUsableSpan(annotation: AnnotationView): boolean {
  return annotation.start !== undefined && annotation.end !== undefined && annotation.end > annotation.start;
}

function selectResultTab(tabName: ResultViewName): void {
  documentView.hidden = tabName !== "document";
  chunksView.hidden = tabName !== "chunks";
  heatmapView.hidden = tabName !== "heatmap";
  graphView.hidden = tabName !== "graph";
  jsonView.hidden = tabName !== "json";
  for (const tab of resultTabs) {
    const selected = tab.dataset.resultTab === tabName;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  }
  semanticWorkbench.show(tabName);
}

function navigateResultTabs(event: KeyboardEvent): void {
  const currentIndex = resultTabs.indexOf(event.currentTarget as HTMLButtonElement);
  const targetIndex = tabTargetIndex(event.key, currentIndex, resultTabs.length);
  if (targetIndex === undefined) {
    return;
  }
  event.preventDefault();
  const next = resultTabs[targetIndex];
  if (next) {
    selectResultTab(resultViewName(next.dataset.resultTab));
    next.focus();
  }
}

function resultViewName(value: string | undefined): ResultViewName {
  return value === "chunks" || value === "heatmap" || value === "graph" || value === "json"
    ? value
    : "document";
}

function renderXray(response: unknown): void {
  const view = readNormalizationXray(response);
  normalizationXray.hidden = !view;
  if (view) {
    renderNormalizationXray(normalizationXray, view);
  }
}

function createAnalysisRequest(text: string, includeChunks = true): AnalyzeRequest {
  const request = analysisControls.request(text, includeChunks);
  if (xrayToggle.checked) {
    request.profile = withXrayNormalization(request.profile);
  }
  return request;
}

async function copyResponse(): Promise<void> {
  if (currentResponse === undefined) {
    return;
  }
  if (!currentJson) {
    setFormStatus(LARGE_COPY_MESSAGE, true);
    return;
  }
  try {
    await navigator.clipboard.writeText(storedJson());
    flashButtonLabel(copyButton, "Copied");
  } catch {
    setFormStatus("Copy failed. Select the response text and copy it manually.", true);
    responseOutput.focus();
  }
}

function downloadResponse(): void {
  if (currentResponse === undefined) {
    return;
  }
  saveBlob(new Blob([storedJson()], { type: "application/json" }), "opennlp-analysis.json");
}

/**
 * Saves the response as serialized protobuf. A response the browser holds as JSON is
 * transcoded by the gateway byte for byte; one past the browser's limit is re-run on the
 * server, which streams the bytes without ever printing JSON.
 */
async function downloadResponsePb(): Promise<void> {
  if (currentResponse === undefined) {
    return;
  }
  if (!currentJson && !currentRequest) {
    setFormStatus(LARGE_PB_MESSAGE, true);
    return;
  }
  downloadPbButton.disabled = true;
  try {
    if (currentJson) {
      const bytes = await encodeAnalyzeResponsePb(storedJson());
      saveBlob(new Blob([bytes], { type: "application/x-protobuf" }), "opennlp-analysis.pb");
    } else if (currentRequest) {
      setFormStatus(SERVER_PB_MESSAGE);
      const bytes = await analyzeToProtobuf(currentRequest);
      saveBlob(new Blob([bytes], { type: "application/x-protobuf" }), "opennlp-analysis.pb");
      setFormStatus(`Saved opennlp-analysis.pb (${mebibytes(bytes.byteLength)} MiB) from a `
        + "server-side re-run of the same request.");
    }
  } catch (error) {
    setFormStatus(errorMessage(error, "The .pb download did not complete."), true);
  } finally {
    downloadPbButton.disabled = false;
  }
}

/** Hands one blob to the browser's save flow under the given file name. */
function saveBlob(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

/** Loads a previously saved response, .pb through the gateway and .json directly. */
async function loadLocalResponse(file: File): Promise<void> {
  try {
    const response = file.name.endsWith(".pb")
      ? await decodeAnalyzeResponsePb(await file.arrayBuffer())
      : JSON.parse(await file.text()) as unknown;
    presentLoadedResponse(response, file.name);
  } catch (error) {
    setFormStatus(errorMessage(error, `Could not load ${file.name}.`), true);
  } finally {
    loadResponseInput.value = "";
  }
}

/** Presents a loaded response through the same views a live analysis uses. */
function presentLoadedResponse(response: unknown, name: string): void {
  // A file has no request behind it, so a large one cannot be re-run for a .pb.
  currentRequest = undefined;
  const shape = readDocumentShape(response);
  textArea.value = shape.rawText;
  updateFormState();
  storeResponse(response, shape);
  chunkProjectionView.render(response);
  renderDocumentShape(shape);
  renderXray(response);
  semanticWorkbench.setDocument(shape.rawText, shape, response);
  selectResultTab("document");
  setFormStatus(`Loaded ${name}.`);
}

/**
 * Shows the detected-language chips (ranked, best first) and which classic pipeline
 * served the request: the routing diagnostic when the server emitted one, otherwise
 * the default models.
 */
function renderLanguageSummary(response: unknown): void {
  const envelope = asRecordOrEmpty(response);
  const document_ = asRecordOrEmpty(envelope.document);
  rankedLanguageChips.replaceChildren();
  const ranked = Array.isArray(document_.rankedLanguages) ? document_.rankedLanguages : [];
  const detected = typeof document_.detectedLanguage === "string"
    ? document_.detectedLanguage : undefined;
  const predictions = ranked.length > 0
    ? ranked
    : detected
      ? [{ language: detected, confidence: document_.languageConfidence }]
      : [];
  for (const value of predictions) {
    const prediction = asRecordOrEmpty(value);
    if (typeof prediction.language !== "string") {
      continue;
    }
    const chip = window.document.createElement("span");
    chip.className = "language-chip";
    const confidence = typeof prediction.confidence === "number"
      ? ` ${(prediction.confidence * 100).toFixed(1)}%` : "";
    chip.textContent = `${prediction.language}${confidence}`;
    rankedLanguageChips.append(chip);
  }
  const routing = routingDiagnostic(envelope);
  routedPipelineBadge.textContent = routing
    ?? (predictions.length > 0 ? "Default models" : "");
  routedPipelineBadge.hidden = !routedPipelineBadge.textContent;
  languageSummary.hidden =
      predictions.length === 0 && routedPipelineBadge.hidden;
}

/** Returns the server's classic-pipeline routing diagnostic, when one was emitted. */
function routingDiagnostic(envelope: Record<string, unknown>): string | undefined {
  const diagnostics = Array.isArray(envelope.diagnostics) ? envelope.diagnostics : [];
  for (const value of diagnostics) {
    const message = asRecordOrEmpty(value).message;
    if (typeof message === "string" && message.startsWith("Classic pipeline ")) {
      return message;
    }
  }
  return undefined;
}

/** Returns the value as a record, or an empty one. */
function asRecordOrEmpty(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function storeResponse(response: unknown, shape: DocumentShapeView): void {
  currentResponse = response;
  renderLanguageSummary(response);
  const summary = summarizeDocumentShape(shape);
  const presentation = jsonPresentation(response, shape.rawText.length, summary.annotationCount);
  currentJson = presentation.inline ? presentation.text : "";
  responseOutput.textContent = presentation.text;
  copyButton.disabled = false;
  downloadButton.disabled = false;
  downloadPbButton.disabled = false;
}

/** Above this input size an analysis with embeddings asks before it runs. */
const LARGE_EMBEDDING_WARNING_BYTES = 256 * 1024;

/** Whether a request asks for document or chunk embeddings. */
function requestsEmbeddings(request: AnalyzeRequest): boolean {
  return Boolean(request.options?.embeddingModelId)
    || (request.chunkEmbedConfigs ?? []).some((config) => (config.embeddingModelIds?.length ?? 0) > 0);
}

/** Mebibytes with one decimal, for a size a person reads. */
function mebibytes(bytes: number): string {
  return (bytes / (1024 * 1024)).toFixed(1);
}

function storedJson(): string {
  return currentJson || JSON.stringify(currentResponse);
}

function setBusy(value: boolean): void {
  busy = value;
  form.setAttribute("aria-busy", String(value));
  analyzeButton.querySelector<HTMLElement>("[data-button-label]")!.textContent = value ? "Analyzing" : "Analyze text";
  updateFormState();
}

function updateFormState(): void {
  const count = textArea.value.length;
  characterCount.textContent = `${formatInteger(count)} ${count === 1 ? "character" : "characters"}`;
  analyzeButton.disabled = busy || !serviceAvailable || textArea.value.trim().length === 0
    || !analysisControls.valid;
}

function setServiceState(state: "loading" | "ready" | "unavailable", label: string): void {
  serviceStatus.className = `service-status is-${state}`;
  serviceStatus.querySelector<HTMLElement>("[data-status-label]")!.textContent = label;
}

function setFormStatus(message: string, error = false): void {
  formStatus.textContent = message;
  formStatus.classList.toggle("is-error", error);
}

function discoverServiceName(value: unknown): string {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    return "OpenNLP gRPC";
  }
  const record = value as Record<string, unknown>;
  const version = typeof record.opennlpVersion === "string" ? record.opennlpVersion.trim() : "";
  if (version) {
    return `OpenNLP ${version}`;
  }
  for (const candidate of [record.name, record.serviceName, record.apiVersion]) {
    if (typeof candidate === "string" && candidate.trim()) {
      return candidate.trim();
    }
  }
  return "OpenNLP gRPC";
}

/**
 * The first paragraphs of a novel as blank-line separated documents, enough to build a
 * small index in seconds without pasting anything.
 */
function sampleDocuments(novel: string, paragraphs: number): string {
  const chosen: string[] = [];
  let start = 0;
  while (chosen.length < paragraphs && start < novel.length) {
    let end = novel.indexOf("\n\n", start);
    if (end < 0) {
      end = novel.length;
    }
    const paragraph = collapseWhitespace(novel.slice(start, end));
    if (paragraph.length > 120) {
      chosen.push(paragraph);
    }
    start = end + 2;
  }
  return chosen.join("\n\n");
}
