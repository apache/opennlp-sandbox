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

/** The three theme preferences the header toggle cycles through. */
export type ThemePreference = "system" | "light" | "dark";

/** localStorage key holding an explicit theme choice; absent means system. */
export const THEME_STORAGE_KEY = "opennlp-grpc-webapp-theme";

/** Visible button labels per preference. */
const TOGGLE_LABELS: Record<ThemePreference, string> = {
  system: "Auto",
  light: "Light",
  dark: "Dark",
};

/** Accessible descriptions naming the current mode and the mode a click selects. */
const TOGGLE_DESCRIPTIONS: Record<ThemePreference, string> = {
  system: "Color theme: system. Activate for the light theme.",
  light: "Color theme: light. Activate for the dark theme.",
  dark: "Color theme: dark. Activate for the system theme.",
};

/** Returns the preference after the given one in the system, light, dark cycle. */
export function nextThemePreference(current: ThemePreference): ThemePreference {
  if (current === "system") {
    return "light";
  }
  return current === "light" ? "dark" : "system";
}

/** Reads the persisted preference; unknown values and storage failures mean system. */
export function readStoredTheme(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    return "system";
  }
}

/** Persists an explicit choice, or clears the key for system; failures are ignored. */
function storeTheme(preference: ThemePreference): void {
  try {
    if (preference === "system") {
      window.localStorage.removeItem(THEME_STORAGE_KEY);
    } else {
      window.localStorage.setItem(THEME_STORAGE_KEY, preference);
    }
  } catch {
    // The choice still themes this page; it just does not survive a reload.
  }
}

/** Stamps data-theme on the document for explicit choices; system removes it. */
export function applyTheme(preference: ThemePreference): void {
  if (preference === "system") {
    document.documentElement.removeAttribute("data-theme");
  } else {
    document.documentElement.setAttribute("data-theme", preference);
  }
}

/** Reflects the current preference on the toggle button. */
function renderToggle(button: HTMLButtonElement, preference: ThemePreference): void {
  button.textContent = TOGGLE_LABELS[preference];
  button.setAttribute("aria-label", TOGGLE_DESCRIPTIONS[preference]);
}

/** Applies the persisted preference and wires the header toggle to cycle it. */
export function initThemeToggle(button: HTMLButtonElement): void {
  let preference = readStoredTheme();
  applyTheme(preference);
  renderToggle(button, preference);
  button.addEventListener("click", () => {
    preference = nextThemePreference(preference);
    applyTheme(preference);
    storeTheme(preference);
    renderToggle(button, preference);
  });
}
