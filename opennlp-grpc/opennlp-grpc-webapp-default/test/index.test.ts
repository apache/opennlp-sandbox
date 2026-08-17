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
    expect(html).toContain('id="graph-completeness"');
    expect(html).toContain('id="heatmap-mode-query"');
    expect(html).toContain('id="heatmap-mode-sentiment"');
    expect(html).toContain('id="heatmap-query"');
    expect(html).toContain('id="heatmap-status" class="heatmap-status" role="status" aria-live="polite"');
    expect(html).toContain('id="document-heatmap"');
    expect(html).toContain('id="heatmap-selection"');
    expect(html).not.toContain('id="semantic-heatmap"');
    expect(html).not.toContain('id="sentiment-heatmap"');
    expect(html).toContain('id="semantic-query"');
    expect(html).toContain('id="add-to-index-button"');
    expect(html).toContain('id="search-results"');
    expect(html).toContain('Apache OpenNLP');
  });

  it("uses a wide document workbench with explicit chunk controls and a details drawer", () => {
    expect(html).toContain('class="workspace workspace-wide"');
    expect(html).toContain('<option value="max" selected>All available features</option>');
    expect(html).toContain('id="sentence-chunks"');
    expect(html).toContain('id="token-chunks"');
    expect(html).toContain('id="token-chunk-size"');
    expect(html).toContain('id="token-chunk-overlap"');
    expect(html).toContain('id="embedding-model-select"');
    expect(html).toContain('id="enabled-feature-list"');
    expect(html).toContain('value="custom"');
    expect(html).toContain('id="feature-options"');
    expect(html).toContain('id="annotation-drawer-backdrop"');
    expect(html).toContain('id="annotation-details-close"');
    expect(html).toContain('role="dialog" aria-modal="true"');
    expect(html).toContain('role="tablist" aria-label="Workbench navigation"');
    expect(html).toContain('data-workbench-tab="analysis"');
    expect(html).toContain('data-workbench-tab="corpus-search"');
    expect(html).toContain('data-workbench-tab="session-search"');
    expect(html).toContain('data-workbench-tab="models"');
    expect(html).toContain('id="model-data-workbench"');
    expect(html).toContain("install-resource");
    expect(html).toContain('data-result-tab="chunks"');
    expect(html).toContain('id="chunks-view" role="tabpanel"');
    expect(html).toContain('id="chunk-projection"');
  });

  it("provides server-backed corpus and dynamic workspace search", () => {
    expect(html).toContain('<label for="server-search-index">');
    expect(html).toContain('<label for="server-search-query">');
    expect(html).toContain('id="server-search-status" role="status" aria-live="polite"');
    expect(html).toContain('id="server-search-results" aria-label="Server search results"');
    expect(html).toContain('id="score-legend" aria-label="Similarity score color scale from minus one to one"');
    expect(html).toContain('id="search-source-text"');
    expect(html).toContain('id="search-inspector"');
    expect(html).toContain('id="search-analytics" aria-label="Selected document analytics"');
    expect(html).toContain('On-the-fly workspace index');
    expect(html).toContain('The browser renders server scores and never performs vector ranking');
  });
});

describe("large-document layout contract", () => {
  const css = readFileSync(fileURLToPath(new URL("../src/style.css", import.meta.url)), "utf8");

  it("keeps the document viewport vertically scrollable without horizontal scrolling", () => {
    expect(css).toMatch(/\.annotated-text\s*\{[^}]*overflow-y:\s*auto;/s);
    expect(css).toMatch(/\.annotated-text\s*\{[^}]*overflow-x:\s*hidden;/s);
    expect(css).toMatch(/\.annotated-text\s*\{[^}]*overflow-wrap:\s*anywhere;/s);
    expect(css).toMatch(/\.annotated-text\s*\{[^}]*max-height:/s);
  });

  it("wraps every long-form evidence and JSON surface instead of scrolling sideways", () => {
    expect(css).toMatch(/\.response-panel #response-output\s*\{[^}]*overflow-x:\s*hidden;/s);
    expect(css).toMatch(/\.search-source-text\s*\{[^}]*overflow-x:\s*hidden;/s);
    expect(css).toMatch(/\.chunk-comparison pre\s*\{[^}]*overflow-x:\s*hidden;/s);
    expect(css).toMatch(/\.drawer-chunk-text\s*\{[^}]*overflow-wrap:\s*anywhere;/s);
  });

  it("uses a full-width four-choice navigation row on narrow screens", () => {
    expect(css).toMatch(/@media \(max-width: 560px\)[\s\S]*?\.site-nav\s*\{[^}]*grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);[^}]*overflow:\s*visible;/);
    expect(css).not.toMatch(/@media \(max-width: 560px\)[\s\S]*?\.site-nav\s*\{[^}]*overflow-x:\s*auto;/);
  });

  it("keeps a large layer catalog from displacing the document on narrow screens", () => {
    expect(css).toMatch(/@media \(max-width: 850px\)[\s\S]*?\.layer-list\s*\{[^}]*max-height:\s*9rem;[^}]*overflow-y:\s*auto;/);
  });
});
