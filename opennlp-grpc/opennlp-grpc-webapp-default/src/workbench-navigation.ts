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

import { requiredElement } from "./ui-utils";

export type WorkbenchName =
  | "analysis" | "workflows" | "corpus-search" | "session-search" | "models" | "trainer" | "lifecycle";

export class WorkbenchNavigation {
  readonly #tabs = Array.from(document.querySelectorAll<HTMLButtonElement>("[data-workbench-tab]"));
  readonly #analysis = requiredElement<HTMLElement>("analysis-workbench");
  readonly #workflows = requiredElement<HTMLElement>("workflows-workbench");
  readonly #corpusSearch = requiredElement<HTMLElement>("server-search");
  readonly #sessionSearch = requiredElement<HTMLElement>("session-search");
  readonly #models = requiredElement<HTMLElement>("model-data-workbench");
  readonly #trainer = requiredElement<HTMLElement>("vocabulary-trainer");
  readonly #lifecycle = requiredElement<HTMLElement>("lifecycle-workbench");

  constructor() {
    for (const tab of this.#tabs) {
      tab.addEventListener("click", () => this.select(workbenchName(tab.dataset.workbenchTab)));
      tab.addEventListener("keydown", (event) => this.navigate(event));
    }
    // Jump links are created at runtime too (a browned-out feature's fix button), so one
    // delegated listener serves them all.
    document.addEventListener("click", (event) => {
      const jump = (event.target as Element | null)?.closest<HTMLElement>("[data-workbench-jump]");
      if (!jump) {
        return;
      }
      const name = workbenchName(jump.dataset.workbenchJump);
      this.show(name);
      const focus = jump.dataset.workbenchFocus;
      if (focus) {
        this.#focusHandlers.get(name)?.(focus);
      }
    });
  }

  readonly #focusHandlers = new Map<WorkbenchName, (focus: string) => void>();

  /**
   * Registers what a workbench does when a jump carries a focus, e.g. Models & data
   * scrolling to the card that fixes a pipeline step.
   */
  onFocus(name: WorkbenchName, handler: (focus: string) => void): void {
    this.#focusHandlers.set(name, handler);
  }

  /** Switches to the named workbench, exactly as selecting its tab would. */
  show(name: WorkbenchName): void {
    this.select(name);
  }

  private select(name: WorkbenchName): void {
    this.#analysis.hidden = name !== "analysis";
    this.#workflows.hidden = name !== "workflows";
    this.#corpusSearch.hidden = name !== "corpus-search";
    this.#sessionSearch.hidden = name !== "session-search";
    this.#models.hidden = name !== "models";
    this.#trainer.hidden = name !== "trainer";
    this.#lifecycle.hidden = name !== "lifecycle";
    for (const tab of this.#tabs) {
      const selected = tab.dataset.workbenchTab === name;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
    }
  }

  private navigate(event: KeyboardEvent): void {
    const currentIndex = this.#tabs.indexOf(event.currentTarget as HTMLButtonElement);
    const targetIndex = tabTargetIndex(event.key, currentIndex, this.#tabs.length);
    if (targetIndex === undefined) {
      return;
    }
    event.preventDefault();
    const next = this.#tabs[targetIndex];
    if (next) {
      this.select(workbenchName(next.dataset.workbenchTab));
      next.focus();
    }
  }
}

/**
 * Resolves the tab a navigation key targets, following the ARIA tabs pattern:
 * arrow keys step with wraparound, Home and End jump to the ends of the list.
 * Returns undefined for keys that do not navigate.
 */
export function tabTargetIndex(key: string, currentIndex: number, count: number): number | undefined {
  if (count <= 0) {
    return undefined;
  }
  if (key === "Home") {
    return 0;
  }
  if (key === "End") {
    return count - 1;
  }
  if (key === "ArrowLeft") {
    return (currentIndex - 1 + count) % count;
  }
  if (key === "ArrowRight") {
    return (currentIndex + 1) % count;
  }
  return undefined;
}

function workbenchName(value: string | undefined): WorkbenchName {
  return value === "workflows" || value === "corpus-search" || value === "session-search" || value === "models"
      || value === "trainer" || value === "lifecycle"
    ? value : "analysis";
}
