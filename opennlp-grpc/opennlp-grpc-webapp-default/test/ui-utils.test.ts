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

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { analysisFailureMessage, flashButtonLabel } from "../src/ui-utils";

describe("transient button feedback", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("restores the button's own label after the feedback delay", () => {
    const button = document.createElement("button");
    button.textContent = "Copy vector";

    flashButtonLabel(button, "Copied");

    expect(button.textContent).toBe("Copied");
    vi.advanceTimersByTime(1499);
    expect(button.textContent).toBe("Copied");
    vi.advanceTimersByTime(1);
    expect(button.textContent).toBe("Copy vector");
  });

  it("keeps the original label through rapid repeated feedback", () => {
    const button = document.createElement("button");
    button.textContent = "Copy id";

    flashButtonLabel(button, "Copied");
    vi.advanceTimersByTime(800);
    flashButtonLabel(button, "Copied");
    vi.advanceTimersByTime(1499);
    expect(button.textContent).toBe("Copied");
    vi.advanceTimersByTime(1);

    expect(button.textContent).toBe("Copy id");
  });
});

describe("analysisFailureMessage", () => {
  it("replaces a raw gRPC deadline diagnostic with an explanation and keeps other errors", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    expect(analysisFailureMessage(new Error(
      "CallOptions deadline exceeded after 47.28s. [closed=[], committed=[remote_addr=127.0.0.1:7371]]")))
      .toMatch(/time limit.*smaller document/);
    expect(warn).toHaveBeenCalledOnce();
    expect(analysisFailureMessage(new Error("document.raw_text is required")))
      .toBe("document.raw_text is required");
    warn.mockRestore();
  });
});
