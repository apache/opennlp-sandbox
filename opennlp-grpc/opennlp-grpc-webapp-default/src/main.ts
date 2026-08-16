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

import { analyze, getHealth, getModelBundles, getServiceInfo, type AnalyzeRequest } from "./api";
import { discoverModelBundles, discoverProfiles, type DiscoveryOption } from "./discovery";
import {
  layerAccent,
  readDocumentShape,
  summarizeDocumentShape,
  type AnnotationLayerView,
  type AnnotationView,
  type DocumentShapeView,
} from "./document-shape";

const sampleText =
  "Apache OpenNLP helps developers build applications that process natural language. " +
  "The project provides tokenization, sentence detection, part-of-speech tagging, named entity recognition, and more.";

const form = requiredElement<HTMLFormElement>("analysis-form");
const textArea = requiredElement<HTMLTextAreaElement>("analysis-text");
const profileSelect = requiredElement<HTMLSelectElement>("profile-select");
const modelList = requiredElement<HTMLUListElement>("model-list");
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
const annotationDetails = requiredElement<HTMLElement>("annotation-details");
const documentView = requiredElement<HTMLElement>("document-view");
const jsonView = requiredElement<HTMLElement>("json-view");
const resultTabs = Array.from(document.querySelectorAll<HTMLButtonElement>("[data-result-tab]"));
const layerFilter = requiredElement<HTMLInputElement>("layer-filter");
const resultLayerCount = requiredElement<HTMLElement>("result-layer-count");
const resultAnnotationCount = requiredElement<HTMLElement>("result-annotation-count");
const resultOffsetEncoding = requiredElement<HTMLElement>("result-offset-encoding");

let serviceAvailable = false;
let busy = false;
let currentJson = "";
let currentShape: DocumentShapeView | undefined;

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
  tab.addEventListener("click", () => selectResultTab(tab.dataset.resultTab === "json" ? "json" : "document"));
  tab.addEventListener("keydown", navigateResultTabs);
}

void initialize();

async function initialize(): Promise<void> {
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
  const [infoResult, bundlesResult] = await Promise.allSettled([getServiceInfo(), getModelBundles()]);
  const serviceInfo = infoResult.status === "fulfilled" ? infoResult.value : undefined;
  const bundlesInfo = bundlesResult.status === "fulfilled" ? bundlesResult.value : undefined;
  const profiles = discoverProfiles(serviceInfo);
  const bundles = discoverModelBundles(bundlesInfo);

  populateSelect(profileSelect, profiles);
  populateModelList(bundles);
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

async function submitAnalysis(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  const text = textArea.value.trim();
  if (!text || busy || !serviceAvailable) {
    return;
  }

  const request: AnalyzeRequest = {
    document: { rawText: text },
    options: { offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT" },
  };
  if (profileSelect.value) {
    request.profileId = profileSelect.value;
  }

  setBusy(true);
  setFormStatus("Analyzing text…");
  responseOutput.textContent = "Waiting for the service response…";
  try {
    const response = await analyze(request);
    currentJson = JSON.stringify(response, null, 2);
    responseOutput.textContent = currentJson;
    renderDocumentShape(readDocumentShape(response));
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
  annotationDetails.replaceChildren(message("Select highlighted text to inspect its typed annotation."));
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
    annotationDetails.replaceChildren(message("This analysis returned no document-shape layers."));
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
    button.dataset.searchText = `${layer.id} ${layer.title} ${layer.valueType}`.toLowerCase();
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

function selectLayer(shape: DocumentShapeView, layer: AnnotationLayerView): void {
  for (const button of layerList.querySelectorAll<HTMLButtonElement>(".layer-button")) {
    button.setAttribute("aria-pressed", String(button.dataset.layerId === layer.id));
  }
  annotationDetails.replaceChildren(layerOverview(layer));
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
    marker.addEventListener("click", () => showAnnotation(layer, annotation));
    annotatedText.append(marker);
    cursor = end;
  }
  appendText(shape.rawText.slice(cursor));

  if (positional.length === 0) {
    annotatedText.textContent = shape.rawText;
    annotationDetails.append(message("This document-scoped layer has no selectable text spans."));
  }
}

function showAnnotation(layer: AnnotationLayerView, annotation: AnnotationView): void {
  const title = document.createElement("strong");
  title.textContent = annotation.label;
  const facts = document.createElement("dl");
  facts.className = "annotation-facts";
  addFact(facts, "Layer", layer.id);
  addFact(facts, "Value type", layer.valueType);
  if (annotation.start !== undefined && annotation.end !== undefined) {
    addFact(facts, "Browser span", `${annotation.start}..${annotation.end}`);
  }
  if (annotation.probability !== undefined) {
    addFact(facts, "Probability", annotation.probability.toFixed(4));
  }
  if (annotation.score !== undefined) {
    addFact(facts, "Score", annotation.score.toFixed(4));
  }
  const source = document.createElement("pre");
  source.textContent = JSON.stringify(annotation.source, null, 2);
  annotationDetails.replaceChildren(title, facts, source);
}

function filterLayerButtons(): void {
  if (!currentShape) {
    return;
  }
  const query = layerFilter.value.trim().toLowerCase();
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

function layerOverview(layer: AnnotationLayerView): HTMLElement {
  const container = document.createElement("div");
  const title = document.createElement("strong");
  title.textContent = layer.title;
  const description = document.createElement("p");
  const identity = layer.standardIdentity ?? layer.id;
  description.textContent = `${identity} contains ${layer.annotations.length} ${layer.valueType.toLowerCase()} `
    + `${layer.annotations.length === 1 ? "annotation" : "annotations"}.`;
  container.append(title, description);
  return container;
}

function addFact(list: HTMLDListElement, term: string, value: string): void {
  const name = document.createElement("dt");
  name.textContent = term;
  const detail = document.createElement("dd");
  detail.textContent = value;
  list.append(name, detail);
}

function appendText(value: string): void {
  if (value) {
    annotatedText.append(document.createTextNode(value));
  }
}

function hasUsableSpan(annotation: AnnotationView): boolean {
  return annotation.start !== undefined && annotation.end !== undefined && annotation.end > annotation.start;
}

function message(value: string): HTMLParagraphElement {
  const paragraph = document.createElement("p");
  paragraph.textContent = value;
  return paragraph;
}

function selectResultTab(tabName: "document" | "json"): void {
  documentView.hidden = tabName !== "document";
  jsonView.hidden = tabName !== "json";
  for (const tab of resultTabs) {
    const selected = tab.dataset.resultTab === tabName;
    tab.setAttribute("aria-selected", String(selected));
    tab.tabIndex = selected ? 0 : -1;
  }
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
    selectResultTab(next.dataset.resultTab === "json" ? "json" : "document");
    next.focus();
  }
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

function populateSelect(select: HTMLSelectElement, options: DiscoveryOption[]): void {
  for (const option of options) {
    select.add(new Option(option.label, option.id));
  }
  select.disabled = options.length === 0;
}

function populateModelList(options: DiscoveryOption[]): void {
  modelList.replaceChildren();
  const visibleOptions = options.slice(0, 4);
  if (visibleOptions.length === 0) {
    const item = document.createElement("li");
    item.textContent = "None reported";
    item.className = "is-empty";
    modelList.append(item);
    return;
  }
  for (const option of visibleOptions) {
    const item = document.createElement("li");
    item.textContent = option.label;
    item.title = option.id;
    modelList.append(item);
  }
  if (options.length > visibleOptions.length) {
    const item = document.createElement("li");
    item.textContent = `+${options.length - visibleOptions.length} more`;
    modelList.append(item);
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
  characterCount.textContent = `${count.toLocaleString()} ${count === 1 ? "character" : "characters"}`;
  analyzeButton.disabled = busy || !serviceAvailable || textArea.value.trim().length === 0;
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

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`Required element #${id} is missing.`);
  }
  return element as T;
}
