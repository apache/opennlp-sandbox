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

import type { ChunkProjectionGroup, ChunkProjectionItem } from "./chunk-projection";
import type { AnnotationEntry, AnnotationLayerView, AnnotationView } from "./document-shape";
import { asciiLowerCase } from "./text-utils";
import { emptyMessage, requiredElement } from "./ui-utils";

export class AnnotationDrawer {
  readonly #drawer = requiredElement<HTMLElement>("annotation-details");
  readonly #content = requiredElement<HTMLElement>("annotation-details-content");
  readonly #closeButton = requiredElement<HTMLButtonElement>("annotation-details-close");
  readonly #backdrop = requiredElement<HTMLElement>("annotation-drawer-backdrop");
  #returnFocus?: HTMLElement;

  constructor() {
    this.#closeButton.addEventListener("click", () => this.close());
    this.#backdrop.addEventListener("click", () => this.close());
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !this.#drawer.hidden) {
        this.close();
      }
    });
  }

  reset(): void {
    this.close(false);
    this.#content.replaceChildren(emptyMessage("Select highlighted text to inspect its typed annotation."));
  }

  describeLayer(layer: AnnotationLayerView, note?: string): void {
    const container = document.createElement("div");
    const title = document.createElement("strong");
    title.textContent = layer.title;
    const description = document.createElement("p");
    const identity = layer.standardIdentity ?? layer.id;
    description.textContent = `${identity} contains ${layer.annotations.length} ${asciiLowerCase(layer.valueType)} `
      + `${layer.annotations.length === 1 ? "annotation" : "annotations"}.`;
    container.append(title, description);
    this.#content.replaceChildren(container);
    if (note) {
      this.#content.append(emptyMessage(note));
    }
  }

  showMessage(value: string): void {
    this.#content.replaceChildren(emptyMessage(value));
  }

  showAnnotation(layer: AnnotationLayerView, annotation: AnnotationView, trigger?: HTMLElement): void {
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
    this.#content.replaceChildren(title, facts, source);
    this.open(trigger);
  }

  showAnnotations(
    text: string,
    start: number,
    end: number,
    entries: AnnotationEntry[],
    trigger?: HTMLElement,
  ): void {
    const title = document.createElement("strong");
    title.textContent = text;
    const summary = document.createElement("p");
    summary.textContent = `${entries.length} ${entries.length === 1 ? "annotation" : "annotations"} cover `
      + `characters ${start}..${end}.`;
    this.#content.replaceChildren(title, summary,
      ...entries.map((entry) => annotationBlock(entry.layer, entry.annotation)));
    this.open(trigger);
  }

  showChunk(group: ChunkProjectionGroup, chunk: ChunkProjectionItem, trigger: HTMLElement): void {
    const title = document.createElement("strong");
    title.textContent = `${group.title}, chunk ${chunk.index}`;
    const facts = document.createElement("dl");
    facts.className = "annotation-facts";
    addFact(facts, "Strategy", group.strategy);
    addFact(facts, "Group", group.id);
    addFact(facts, "Document span", `${chunk.start}..${chunk.end}`);
    addFact(facts, "Embeddings", String(chunk.embeddingCount));
    if (group.embeddingModelIds.length > 0) {
      addFact(facts, "Models", joinLabels(group.embeddingModelIds));
    }
    const text = document.createElement("p");
    text.className = "drawer-chunk-text";
    text.textContent = chunk.text;
    this.#content.replaceChildren(title, facts, text);
    this.open(trigger);
  }

  private open(trigger?: HTMLElement): void {
    this.#returnFocus = trigger ?? activeElement();
    this.#drawer.hidden = false;
    this.#backdrop.hidden = false;
    document.body.classList.add("drawer-open");
    this.#closeButton.focus();
  }

  private close(restoreFocus = true): void {
    if (this.#drawer.hidden) {
      return;
    }
    this.#drawer.hidden = true;
    this.#backdrop.hidden = true;
    document.body.classList.remove("drawer-open");
    if (restoreFocus) {
      this.#returnFocus?.focus();
    }
    this.#returnFocus = undefined;
  }
}

function annotationBlock(layer: AnnotationLayerView, annotation: AnnotationView): HTMLElement {
  const block = document.createElement("section");
  block.className = "annotation-entry";
  const title = document.createElement("strong");
  title.textContent = `${layer.title}: ${annotation.label}`;
  const facts = document.createElement("dl");
  facts.className = "annotation-facts";
  addFact(facts, "Layer", layer.id);
  addFact(facts, "Value type", layer.valueType);
  if (annotation.probability !== undefined) {
    addFact(facts, "Probability", annotation.probability.toFixed(4));
  }
  if (annotation.score !== undefined) {
    addFact(facts, "Score", annotation.score.toFixed(4));
  }
  const source = document.createElement("pre");
  source.textContent = JSON.stringify(annotation.source, null, 2);
  block.append(title, facts, source);
  return block;
}

function addFact(list: HTMLDListElement, term: string, value: string): void {
  const name = document.createElement("dt");
  name.textContent = term;
  const detail = document.createElement("dd");
  detail.textContent = value;
  list.append(name, detail);
}

function joinLabels(values: string[]): string {
  let result = "";
  for (const value of values) {
    result += result ? `, ${value}` : value;
  }
  return result;
}

function activeElement(): HTMLElement | undefined {
  return document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
}
