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
  getHealth,
  getModelBundles,
  getSearchIndexes,
  getServiceInfo,
  getUiExtensions,
  indexDocuments,
  deleteSearchIndex,
  searchIndex,
  type AnalyzeRequest,
} from "./api";
import { AnalysisControls } from "./analysis-controls";
import { AnnotationDrawer } from "./annotation-drawer";
import { ChunkProjectionView } from "./chunk-projection-view";
import {
  layerAccent,
  readDocumentShape,
  summarizeDocumentShape,
  type AnnotationLayerView,
  type AnnotationView,
  type DocumentShapeView,
} from "./document-shape";
import { SemanticWorkbench, type ResultViewName } from "./semantic-workbench";
import { ModelDataWorkbench } from "./model-data-workbench";
import { readSearchIndexes, readSearchResponse } from "./search-adapter";
import { ServerSearchWorkbench } from "./server-search-workbench";
import { asciiLowerCase, formatInteger } from "./text-utils";
import {
  activeUiExtension,
  extensionInitials,
  readUiExtensions,
  type UiExtension,
} from "./ui-extensions";
import { errorMessage, requiredElement } from "./ui-utils";
import { WorkbenchNavigation } from "./workbench-navigation";

const sampleText =
  "Apache OpenNLP helps developers build applications that process natural language. " +
  "The project provides tokenization, sentence detection, part-of-speech tagging, named entity recognition, and more.";

const form = requiredElement<HTMLFormElement>("analysis-form");
const textArea = requiredElement<HTMLTextAreaElement>("analysis-text");
const analyzeButton = requiredElement<HTMLButtonElement>("analyze-button");
const sampleButton = requiredElement<HTMLButtonElement>("sample-button");
const copyButton = requiredElement<HTMLButtonElement>("copy-button");
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
let currentShape: DocumentShapeView | undefined;

const analysisControls = new AnalysisControls(updateFormState);
const annotationDrawer = new AnnotationDrawer();
const modelDataWorkbench = new ModelDataWorkbench();
const chunkProjectionView = new ChunkProjectionView((group, chunk, trigger) => {
  annotationDrawer.showChunk(group, chunk, trigger);
});
new WorkbenchNavigation();

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
    currentJson = JSON.stringify({ document: hit.sourceDocument }, null, 2);
    responseOutput.textContent = currentJson;
    chunkProjectionView.render({ document: hit.sourceDocument });
    copyButton.disabled = !currentJson;
    semanticWorkbench.setDocument(hit.documentId, shape, currentJson);
    selectResultTab("document");
  },
  selectAnnotation: selectAnnotationFromGraph,
});

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
form.addEventListener("submit", submitAnalysis);
copyButton.addEventListener("click", copyResponse);
layerFilter.addEventListener("input", filterLayerButtons);
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
    currentJson = JSON.stringify(response, null, 2);
    responseOutput.textContent = currentJson;
    const shape = readDocumentShape(response);
    chunkProjectionView.render(response);
    renderDocumentShape(shape);
    semanticWorkbench.setDocument(text, shape, currentJson);
    selectResultTab("document");
    copyButton.disabled = false;
    setFormStatus("Analysis complete.");
  } catch (error) {
    currentJson = "";
    copyButton.disabled = true;
    responseOutput.textContent = "The analysis request did not complete.";
    setFormStatus(errorMessage(error, "Analysis failed. Please try again."), true);
  } finally {
    setBusy(false);
  }
}

function renderDocumentShape(shape: DocumentShapeView): void {
  currentShape = shape;
  layerList.replaceChildren();
  annotatedText.replaceChildren();
  annotationDrawer.reset();
  layerSummary.textContent = `${shape.layers.length} ${shape.layers.length === 1 ? "layer" : "layers"}`;
  const summary = summarizeDocumentShape(shape);
  resultLayerCount.textContent = String(summary.layerCount);
  resultAnnotationCount.textContent = String(summary.annotationCount);
  resultOffsetEncoding.textContent = summary.offsetEncodingLabel;
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

  const initialLayer = shape.layers.find((layer) => layer.id === "opennlp:tokens")
    ?? shape.layers.find((layer) => layer.annotations.some(hasUsableSpan))
    ?? shape.layers[0];
  if (!initialLayer) {
    return;
  }
  for (const layer of shape.layers) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "layer-button";
    button.dataset.layerId = layer.id;
    button.dataset.searchText = asciiLowerCase(`${layer.id} ${layer.title} ${layer.valueType}`);
    button.dataset.accent = layerAccent(layer);
    button.setAttribute("aria-pressed", String(layer === initialLayer));
    const name = document.createElement("span");
    name.textContent = layer.title;
    const count = document.createElement("small");
    count.textContent = String(layer.annotations.length);
    button.append(name, count);
    button.title = `${layer.id}, ${layer.valueType}`;
    button.addEventListener("click", () => selectLayer(shape, layer));
    layerList.append(button);
  }
  selectLayer(shape, initialLayer);
}

function parseStoredResponse(value: string): unknown {
  if (!value) {
    return undefined;
  }
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return undefined;
  }
}

function selectLayer(shape: DocumentShapeView, layer: AnnotationLayerView): void {
  for (const button of layerList.querySelectorAll<HTMLButtonElement>(".layer-button")) {
    button.setAttribute("aria-pressed", String(button.dataset.layerId === layer.id));
  }
  annotationDrawer.describeLayer(layer);
  annotatedText.replaceChildren();
  annotatedText.dataset.accent = layerAccent(layer);
  annotatedText.setAttribute("aria-label", `${layer.title} annotations over document text`);

  const positional = layer.annotations
    .filter(hasUsableSpan)
    .sort((left, right) => left.start! - right.start! || left.end! - right.end!);
  let cursor = 0;
  for (const annotation of positional) {
    const start = Math.max(cursor, Math.min(annotation.start!, shape.rawText.length));
    const end = Math.max(start, Math.min(annotation.end!, shape.rawText.length));
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
  appendText(shape.rawText.slice(cursor));

  if (positional.length === 0) {
    annotatedText.textContent = shape.rawText;
    annotationDrawer.describeLayer(layer, "This document-scoped layer has no selectable text spans.");
  }
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
  selectResultTab("document");
  selectLayer(currentShape, layer);
  annotationDrawer.showAnnotation(layer, annotation);
}

function filterLayerButtons(): void {
  if (!currentShape) {
    return;
  }
  const query = asciiLowerCase(layerFilter.value.trim());
  const buttons = Array.from(layerList.querySelectorAll<HTMLButtonElement>(".layer-button"));
  let visibleCount = 0;
  for (const button of buttons) {
    const visible = !query || button.dataset.searchText?.includes(query) === true;
    button.hidden = !visible;
    if (visible) {
      visibleCount++;
    }
  }
  layerSummary.textContent = query
    ? `${visibleCount} of ${buttons.length} layers`
    : `${buttons.length} ${buttons.length === 1 ? "layer" : "layers"}`;

  const selected = buttons.find((button) => button.getAttribute("aria-pressed") === "true");
  if (selected?.hidden) {
    const next = buttons.find((button) => !button.hidden);
    const layer = currentShape.layers.find((candidate) => candidate.id === next?.dataset.layerId);
    if (layer) {
      selectLayer(currentShape, layer);
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

function createAnalysisRequest(text: string, includeChunks = true): AnalyzeRequest {
  return analysisControls.request(text, includeChunks);
}

async function copyResponse(): Promise<void> {
  if (!currentJson) {
    return;
  }
  try {
    await navigator.clipboard.writeText(currentJson);
    copyButton.textContent = "Copied";
    window.setTimeout(() => {
      copyButton.textContent = "Copy JSON";
    }, 1500);
  } catch {
    setFormStatus("Copy failed. Select the response text and copy it manually.", true);
    responseOutput.focus();
  }
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
