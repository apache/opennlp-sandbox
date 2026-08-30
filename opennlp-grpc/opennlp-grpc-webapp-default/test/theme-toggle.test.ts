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

import { afterEach, beforeEach, describe, expect, it } from "vitest";

import {
  applyTheme,
  initThemeToggle,
  nextThemePreference,
  readStoredTheme,
  THEME_STORAGE_KEY,
} from "../src/theme-toggle";

/** Replaces window.localStorage with an accessor that always throws. */
function denyStorage(): void {
  Object.defineProperty(window, "localStorage", {
    configurable: true,
    get() {
      throw new Error("storage access denied");
    },
  });
}

const originalStorage = Object.getOwnPropertyDescriptor(window, "localStorage")!;

function toggleButton(): HTMLButtonElement {
  document.body.innerHTML = '<button id="theme-toggle" type="button"></button>';
  return document.getElementById("theme-toggle") as HTMLButtonElement;
}

beforeEach(() => {
  document.documentElement.removeAttribute("data-theme");
  window.localStorage.clear();
});

afterEach(() => {
  Object.defineProperty(window, "localStorage", originalStorage);
  document.documentElement.removeAttribute("data-theme");
});

describe("theme preference cycle", () => {
  it("cycles system to light to dark and back to system", () => {
    expect(nextThemePreference("system")).toBe("light");
    expect(nextThemePreference("light")).toBe("dark");
    expect(nextThemePreference("dark")).toBe("system");
  });

  it("stamps data-theme for explicit choices and removes it for system", () => {
    applyTheme("light");
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    applyTheme("dark");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    applyTheme("system");
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
  });
});

describe("stored theme", () => {
  it("returns system when nothing was stored", () => {
    expect(readStoredTheme()).toBe("system");
  });

  it("returns a stored explicit choice", () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "dark");
    expect(readStoredTheme()).toBe("dark");
  });

  it("treats an unknown stored value as system", () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "sepia");
    expect(readStoredTheme()).toBe("system");
  });

  it("returns system when storage access throws", () => {
    denyStorage();
    expect(readStoredTheme()).toBe("system");
  });
});

describe("theme toggle button", () => {
  it("starts in system mode with an accessible label", () => {
    const button = toggleButton();
    initThemeToggle(button);
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
    expect(button.textContent).toBe("Auto");
    expect(button.getAttribute("aria-label")).toContain("system");
  });

  it("applies a persisted choice on initialization", () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, "dark");
    const button = toggleButton();
    initThemeToggle(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(button.textContent).toBe("Dark");
  });

  it("cycles the theme and persists each explicit choice on click", () => {
    const button = toggleButton();
    initThemeToggle(button);

    button.click();
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("light");
    expect(button.textContent).toBe("Light");
    expect(button.getAttribute("aria-label")).toContain("light");

    button.click();
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark");
    expect(button.textContent).toBe("Dark");

    button.click();
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBeNull();
    expect(button.textContent).toBe("Auto");
  });

  it("keeps toggling the page theme when storage access throws", () => {
    denyStorage();
    const button = toggleButton();
    initThemeToggle(button);
    button.click();
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    button.click();
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
  });
});
