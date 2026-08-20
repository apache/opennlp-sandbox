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

type WorkbenchName = "analysis" | "corpus-search" | "session-search" | "models" | "trainer";

export class WorkbenchNavigation {
  readonly #tabs = Array.from(document.querySelectorAll<HTMLButtonElement>("[data-workbench-tab]"));
  readonly #analysis = requiredElement<HTMLElement>("analysis-workbench");
  readonly #corpusSearch = requiredElement<HTMLElement>("server-search");
  readonly #sessionSearch = requiredElement<HTMLElement>("session-search");
  readonly #models = requiredElement<HTMLElement>("model-data-workbench");
  readonly #trainer = requiredElement<HTMLElement>("vocabulary-trainer");

  constructor() {
    for (const tab of this.#tabs) {
      tab.addEventListener("click", () => this.select(workbenchName(tab.dataset.workbenchTab)));
      tab.addEventListener("keydown", (event) => this.navigate(event));
    }
  }

  private select(name: WorkbenchName): void {
    this.#analysis.hidden = name !== "analysis";
    this.#corpusSearch.hidden = name !== "corpus-search";
    this.#sessionSearch.hidden = name !== "session-search";
    this.#models.hidden = name !== "models";
    this.#trainer.hidden = name !== "trainer";
    for (const tab of this.#tabs) {
      const selected = tab.dataset.workbenchTab === name;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
    }
  }

  private navigate(event: KeyboardEvent): void {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") {
      return;
    }
    event.preventDefault();
    const currentIndex = this.#tabs.indexOf(event.currentTarget as HTMLButtonElement);
    const direction = event.key === "ArrowRight" ? 1 : -1;
    const next = this.#tabs[(currentIndex + direction + this.#tabs.length) % this.#tabs.length];
    if (next) {
      this.select(workbenchName(next.dataset.workbenchTab));
      next.focus();
    }
  }
}

function workbenchName(value: string | undefined): WorkbenchName {
  return value === "corpus-search" || value === "session-search" || value === "models"
      || value === "trainer"
    ? value : "analysis";
}
