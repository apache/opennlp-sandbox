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

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const html = readFileSync(fileURLToPath(new URL("../index.html", import.meta.url)), "utf8");

describe("analysis playground markup", () => {
  it("provides labelled controls and announced status regions", () => {
    expect(html).toContain('<label for="analysis-text">');
    expect(html).toContain('<label for="profile-select">');
    expect(html).toContain('id="model-list" aria-label="Available model bundles"');
    expect(html).toContain('id="service-status" role="status" aria-live="polite"');
    expect(html).toContain('id="form-status" role="status" aria-live="polite"');
    expect(html).toContain('id="response-output"');
    expect(html).toContain('aria-label="Workbench navigation"');
    expect(html).toContain('role="tablist" aria-label="Analysis result views"');
    expect(html).toContain('id="document-view" role="tabpanel"');
    expect(html).toContain('id="layer-list" aria-label="Annotation layers"');
    expect(html).toContain('id="annotated-text"');
    expect(html).toContain('id="annotation-details"');
    expect(html).toContain('aria-label="OpenNLP tools"');
    expect(html).toContain('id="tool-navigation"');
    expect(html).toContain('id="tool-navigation-status"');
    expect(html).toContain('id="layer-filter"');
    expect(html).toContain('id="result-layer-count"');
    expect(html).toContain('id="result-annotation-count"');
    expect(html).toContain('id="result-offset-encoding"');
    expect(html).toContain('data-result-tab="heatmap"');
    expect(html).toContain('data-result-tab="graph"');
    expect(html).toContain('id="semantic-query"');
    expect(html).toContain('id="add-to-index-button"');
    expect(html).toContain('id="search-results"');
    expect(html).toContain('Apache OpenNLP');
  });

  it("provides an accessible server search lens and clearly labels browser-local search", () => {
    expect(html).toContain('<label for="server-search-index">');
    expect(html).toContain('<label for="server-search-query">');
    expect(html).toContain('id="server-search-status" role="status" aria-live="polite"');
    expect(html).toContain('id="server-search-results" aria-label="Server search results"');
    expect(html).toContain('id="score-legend" aria-label="Similarity score color scale from minus one to one"');
    expect(html).toContain('id="search-source-text"');
    expect(html).toContain('id="search-inspector"');
    expect(html).toContain('id="search-analytics" aria-label="Selected document analytics"');
    expect(html).toContain('Browser-session vector search');
    expect(html).toContain('The index stays in this browser tab');
  });
});
