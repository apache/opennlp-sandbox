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
  decodeAnalyzeResponsePb,
  deleteCollection,
  deleteIndexAlias,
  deleteStaticModel,
  downloadVocabularyTsv,
  encodeAnalyzeResponsePb,
  getCollection,
  getCollections,
  getDictionaryFormats,
  getHealth,
  getIndexAliases,
  getModelBundles,
  getSearchIndexes,
  getSearchProviders,
  getServiceInfo,
  getStaticModels,
  getTeachers,
  getUiExtensions,
  importDictionary,
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
import { readCollectionEvent, readCollectionResponse, readCollections } from "./collection-adapter";
import { LifecycleWorkbench } from "./lifecycle-workbench";
import { withXrayNormalization } from "./analysis-config";
import { AnalysisControls } from "./analysis-controls";
import { AnnotationDrawer } from "./annotation-drawer";
import { ChunkProjectionView } from "./chunk-projection-view";
import {
  combinedAnnotationSegments,
  documentScopedAnnotations,
  layerAccent,
  readDocumentShape,
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
import { isTermVectorLayer, renderTermVectorStack } from "./term-vector-stack";
import { SemanticWorkbench, type ResultViewName } from "./semantic-workbench";
import { ModelDataWorkbench } from "./model-data-workbench";
import {
  readIndexAliases,
  readIndexResponse,
  readSearchIndexes,
  readSearchProviderInstances,
  readSearchResponse,
} from "./search-adapter";
import { ServerSearchWorkbench } from "./server-search-workbench";
import { asciiLowerCase, formatInteger } from "./text-utils";
import {
  activeUiExtension,
  extensionInitials,
  readUiExtensions,
  type UiExtension,
} from "./ui-extensions";
import { errorMessage, requiredElement } from "./ui-utils";
import {
  readDictionaryFormats,
  readImportedDictionary,
  readLearnedVocabulary,
  readStaticModels,
  readTeachers,
  readTrainedModel,
  VocabularyTrainerWorkbench,
} from "./vocabulary-trainer";
import { WorkbenchNavigation } from "./workbench-navigation";
import { loadAliceDemo } from "./demo-data";
import { jsonPresentation } from "./json-response";

const sampleText =
  "Apache OpenNLP helps developers build applications that process natural language. " +
  "The project provides tokenization, sentence detection, part-of-speech tagging, named entity recognition, and more.";

const form = requiredElement<HTMLFormElement>("analysis-form");
const textArea = requiredElement<HTMLTextAreaElement>("analysis-text");
const analyzeButton = requiredElement<HTMLButtonElement>("analyze-button");
const sampleButton = requiredElement<HTMLButtonElement>("sample-button");
const aliceSampleButton = requiredElement<HTMLButtonElement>("alice-sample-button");
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
const layerFilter = requiredElement<HTMLInputElement>("layer-filter");
const resultLayerCount = requiredElement<HTMLElement>("result-layer-count");
const resultAnnotationCount = requiredElement<HTMLElement>("result-annotation-count");
const resultOffsetEncoding = requiredElement<HTMLElement>("result-offset-encoding");
const toolNavigation = requiredElement<HTMLElement>("tool-navigation");
const toolNavigationStatus = requiredElement<HTMLElement>("tool-navigation-status");

let serviceAvailable = false;
let busy = false;
let currentJson = "";
let currentResponse: unknown;
let currentShape: DocumentShapeView | undefined;
let currentLayer: AnnotationLayerView | undefined;
let currentCombinedSegments: CombinedAnnotationSegment[] = [];

const analysisControls = new AnalysisControls(updateFormState);
const annotationDrawer = new AnnotationDrawer();
const modelDataWorkbench = new ModelDataWorkbench();
const chunkProjectionView = new ChunkProjectionView((group, chunk, trigger) => {
  annotationDrawer.showChunk(group, chunk, trigger);
});
new WorkbenchNavigation();

const vocabularyTrainer = new VocabularyTrainerWorkbench({
  listDictionaryFormats: async () => readDictionaryFormats(await getDictionaryFormats()),
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
  onModelsChanged: (models) => analysisControls.setTrainedEmbeddingModels(
    models.map((model) => ({
      id: model.artifactId,
      label: `${model.displayName} (trained)`,
    }))),
});
void vocabularyTrainer.initialize();

const semanticWorkbench = new SemanticWorkbench({
  index: async (request) => {
    const response = await indexDocuments(request) as Record<string, unknown>;
    const index = readSearchIndexes({ indexes: response.index ? [response.index] : [] })[0];
    if (!index) {
      throw new Error("The server returned an invalid dynamic index descriptor.");
    }
    return index;
  },
  search: async (request) => readSearchResponse(await searchIndex(request)),
  deleteIndex: async (indexId) => { await deleteSearchIndex(indexId); },
  openDocument: (hit) => {
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
  listProviders: async () => readSearchProviderInstances(await getSearchProviders()),
  listAliases: async () => readIndexAliases(await getIndexAliases()),
  persist: async (indexId) => readIndexResponse(await persistIndex(indexId)),
  seal: async (indexId) => readIndexResponse(await sealIndex(indexId)),
  reindex: async (request) => readIndexResponse(await reindexIndex(request)),
  setAlias: async (alias, indexId) => { await setIndexAlias(alias, indexId); },
  deleteAlias: async (alias) => { await deleteIndexAlias(alias); },
  listStaticModels: async () => readStaticModels(await getStaticModels()),
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

textArea.addEventListener("input", updateFormState);
sampleButton.addEventListener("click", () => {
  textArea.value = sampleText;
  updateFormState();
  textArea.focus();
});
aliceSampleButton.addEventListener("click", () => void loadAliceSample());
form.addEventListener("submit", submitAnalysis);
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
  setFormStatus("Checking service capabilities and model bundles.");

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
  const [infoResult, bundlesResult] = await Promise.allSettled([getServiceInfo(), getModelBundles()]);
  const serviceInfo = infoResult.status === "fulfilled" ? infoResult.value : undefined;
  const bundlesInfo = bundlesResult.status === "fulfilled" ? bundlesResult.value : undefined;
  const capabilities = analysisControls.configure(serviceInfo, bundlesInfo);
  modelDataWorkbench.configure(capabilities);
  const profiles = capabilities.profiles;
  const bundles = capabilities.bundles;
  profileCount.textContent = String(profiles.length);
  modelCount.textContent = String(bundles.length);
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

async function submitAnalysis(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const text = textArea.value.trim();
  if (!text || busy || !serviceAvailable) {
    return;
  }

  setBusy(true);
  setFormStatus("Analyzing text…");
  responseOutput.textContent = "Waiting for the service response…";
  try {
    const response = await analyze(createAnalysisRequest(text));
    const shape = readDocumentShape(response);
    storeResponse(response, shape);
    chunkProjectionView.render(response);
    renderDocumentShape(shape);
    renderXray(response);
    semanticWorkbench.setDocument(text, shape, response);
    selectResultTab("document");
    setFormStatus("Analysis complete.");
  } catch (error) {
    currentJson = "";
    currentResponse = undefined;
    copyButton.disabled = true;
    downloadButton.disabled = true;
    downloadPbButton.disabled = true;
    normalizationXray.hidden = true;
    responseOutput.textContent = "The analysis request did not complete.";
    setFormStatus(errorMessage(error, "Analysis failed. Please try again."), true);
  } finally {
    setBusy(false);
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

function renderDocumentShape(shape: DocumentShapeView): void {
  currentShape = shape;
  currentLayer = undefined;
  currentCombinedSegments = [];
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
    annotationDrawer.showMessage("This analysis returned no document-shape layers.");
    return;
  }

  const allButton = document.createElement("button");
  allButton.type = "button";
  allButton.className = "layer-button";
  allButton.dataset.layerKind = "all";
  allButton.dataset.searchText = "all annotations combined";
  allButton.dataset.accent = "blue";
  allButton.setAttribute("aria-pressed", "true");
  const allName = document.createElement("span");
  allName.textContent = "All annotations";
  const allCount = document.createElement("small");
  allCount.textContent = String(summary.annotationCount);
  allButton.append(allName, allCount);
  allButton.title = "Combined projection of every returned annotation layer";
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
    button.addEventListener("click", () => selectLayer(shape, layer));
    layerList.append(button);
  }
  selectAllLayers(shape);
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
  currentLayer = undefined;
  for (const button of layerList.querySelectorAll<HTMLButtonElement>(".layer-button")) {
    button.setAttribute("aria-pressed", String(button.dataset.layerKind === "all"));
  }
  annotatedText.replaceChildren();
  annotatedText.dataset.accent = "blue";
  annotatedText.setAttribute("aria-label", "All typed annotations over document text");

  const documentEntries = documentScopedAnnotations(shape)
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
      chip.addEventListener("click", () => annotationDrawer.showAnnotation(entry.layer, entry.annotation, chip));
      chips.append(chip);
    }
    scoped.append(heading, chips);
    for (const layer of termVectorLayers) {
      scoped.append(renderTermVectorStack(layer, layerAccent(layer),
        (trigger) => annotationDrawer.showTermVectorList(layer, trigger)));
    }
    annotatedText.append(scoped);
  }

  if (currentCombinedSegments.length === 0) {
    currentCombinedSegments = combinedAnnotationSegments(shape);
  }
  const view = currentDocumentWindow(shape.rawText.length);
  let cursor = view.start;
  for (const segment of currentCombinedSegments) {
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
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
    return;
  }
  event.preventDefault();
  const currentIndex = resultTabs.indexOf(event.currentTarget as HTMLButtonElement);
  const direction = event.key === "ArrowRight" ? 1 : -1;
  const next = resultTabs[(currentIndex + direction + resultTabs.length) % resultTabs.length];
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
  try {
    await navigator.clipboard.writeText(storedJson());
    copyButton.textContent = "Copied";
    window.setTimeout(() => {
      copyButton.textContent = "Copy JSON";
    }, 1500);
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

/** Saves the stored response as serialized protobuf, transcoded by the gateway. */
async function downloadResponsePb(): Promise<void> {
  if (currentResponse === undefined) {
    return;
  }
  try {
    const bytes = await encodeAnalyzeResponsePb(storedJson());
    saveBlob(new Blob([bytes], { type: "application/x-protobuf" }), "opennlp-analysis.pb");
  } catch (error) {
    setFormStatus(errorMessage(error, "The .pb download did not complete."), true);
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

function storeResponse(response: unknown, shape: DocumentShapeView): void {
  currentResponse = response;
  const summary = summarizeDocumentShape(shape);
  const presentation = jsonPresentation(response, shape.rawText.length, summary.annotationCount);
  currentJson = presentation.inline ? presentation.text : "";
  responseOutput.textContent = presentation.text;
  copyButton.disabled = false;
  downloadButton.disabled = false;
  downloadPbButton.disabled = false;
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
