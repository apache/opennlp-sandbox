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

/** @vitest-environment jsdom */

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";

import { tabTargetIndex, WorkbenchNavigation } from "../src/workbench-navigation";

const html = readFileSync(join(import.meta.dirname, "..", "index.html"), "utf8");

function pressKey(element: HTMLElement, key: string): void {
  element.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true }));
}

describe("tab navigation key targets", () => {
  it("steps through tabs with wraparound and jumps to the ends with Home and End", () => {
    expect(tabTargetIndex("ArrowRight", 0, 3)).toBe(1);
    expect(tabTargetIndex("ArrowRight", 2, 3)).toBe(0);
    expect(tabTargetIndex("ArrowLeft", 0, 3)).toBe(2);
    expect(tabTargetIndex("Home", 2, 3)).toBe(0);
    expect(tabTargetIndex("End", 0, 3)).toBe(2);
  });

  it("ignores keys that do not navigate and empty tab lists", () => {
    expect(tabTargetIndex("Enter", 1, 3)).toBeUndefined();
    expect(tabTargetIndex("ArrowDown", 1, 3)).toBeUndefined();
    expect(tabTargetIndex("Home", 0, 0)).toBeUndefined();
  });
});

describe("workbench navigation", () => {
  beforeEach(() => {
    document.body.innerHTML = html.replace(/^[\s\S]*<body[^>]*>/, "").replace(/<\/body>[\s\S]*$/, "");
  });

  it("jumps to the last and first workbench tabs with End and Home", () => {
    new WorkbenchNavigation();
    const analysisTab = document.getElementById("analysis-workbench-tab") as HTMLButtonElement;
    const lifecycleTab = document.getElementById("lifecycle-workbench-tab") as HTMLButtonElement;

    pressKey(analysisTab, "End");

    expect(lifecycleTab.getAttribute("aria-selected")).toBe("true");
    expect((document.getElementById("lifecycle-workbench") as HTMLElement).hidden).toBe(false);
    expect((document.getElementById("analysis-workbench") as HTMLElement).hidden).toBe(true);

    pressKey(lifecycleTab, "Home");

    expect(analysisTab.getAttribute("aria-selected")).toBe("true");
    expect((document.getElementById("analysis-workbench") as HTMLElement).hidden).toBe(false);
    expect((document.getElementById("lifecycle-workbench") as HTMLElement).hidden).toBe(true);
  });

  it("keeps arrow-key stepping with wraparound", () => {
    new WorkbenchNavigation();
    const analysisTab = document.getElementById("analysis-workbench-tab") as HTMLButtonElement;

    pressKey(analysisTab, "ArrowLeft");

    const lifecycleTab = document.getElementById("lifecycle-workbench-tab") as HTMLButtonElement;
    expect(lifecycleTab.getAttribute("aria-selected")).toBe("true");
    expect((document.getElementById("lifecycle-workbench") as HTMLElement).hidden).toBe(false);
  });

  it("opens workflows from the build-your-own-index action", () => {
    new WorkbenchNavigation();
    const action = document.querySelector<HTMLButtonElement>("#server-search-index-help [data-workbench-jump]")!;

    action.click();

    expect(document.getElementById("workflows-workbench-tab")?.getAttribute("aria-selected"))
      .toBe("true");
    expect((document.getElementById("workflows-workbench") as HTMLElement).hidden).toBe(false);
    expect((document.getElementById("server-search") as HTMLElement).hidden).toBe(true);
  });

  it("serves jump links created after start-up and hands their focus to the target workbench", () => {
    document.body.innerHTML = html;
    const navigation = new WorkbenchNavigation();
    const focused: string[] = [];
    navigation.onFocus("models", (step) => focused.push(step));
    const jump = document.createElement("button");
    jump.dataset.workbenchJump = "models";
    jump.dataset.workbenchFocus = "PIPELINE_STEP_NER";
    document.getElementById("analysis-workbench")!.append(jump);

    jump.click();

    expect(document.getElementById("model-data-workbench")!.hidden).toBe(false);
    expect(document.getElementById("analysis-workbench")!.hidden).toBe(true);
    expect(focused).toEqual(["PIPELINE_STEP_NER"]);
  });
});
