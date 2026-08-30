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

/** Returns the required page element or fails during application startup. */
export function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) {
    throw new Error(`Required element #${id} is missing.`);
  }
  return element as T;
}

/** Creates a consistently styled empty-state message. */
export function emptyMessage(value: string): HTMLParagraphElement {
  const paragraph = document.createElement("p");
  paragraph.className = "empty-message";
  paragraph.textContent = value;
  return paragraph;
}

// Pending label restores per button, so repeated feedback never stacks timers.
const labelRestores = new WeakMap<HTMLButtonElement, number>();

/**
 * Shows a transient outcome label (such as "Copied") on a button and restores
 * the button's own label after the delay, so feedback never sticks. Repeated
 * calls restart the delay and still restore the original label.
 */
export function flashButtonLabel(
  button: HTMLButtonElement,
  label: string,
  restoreMillis = 1500,
): void {
  const pending = labelRestores.get(button);
  if (pending !== undefined) {
    window.clearTimeout(pending);
  } else {
    button.dataset.restoreLabel = button.textContent ?? "";
  }
  button.textContent = label;
  labelRestores.set(button, window.setTimeout(() => {
    button.textContent = button.dataset.restoreLabel ?? "";
    delete button.dataset.restoreLabel;
    labelRestores.delete(button);
  }, restoreMillis));
}

/** Returns an Error message or the caller-supplied fallback. */
export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

/**
 * Appends one wrapped name/value row to a fact list, linking the value to
 * `href` in a new tab when one is given.
 */
export function addFact(list: HTMLDListElement, term: string, value: string, href?: string): void {
  const container = document.createElement("div");
  const name = document.createElement("dt");
  name.textContent = term;
  const detail = document.createElement("dd");
  if (href) {
    const link = document.createElement("a");
    link.href = href;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    link.textContent = value;
    detail.append(link);
  } else {
    detail.textContent = value;
  }
  container.append(name, detail);
  list.append(container);
}
