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
    expect(html).toContain('id="model-list" aria-label="Available model packs"');
    expect(html).toContain('id="service-status" role="status" aria-live="polite"');
    expect(html).toContain('id="form-status" role="status" aria-live="polite"');
    expect(html).toContain('id="response-output"');
    expect(html).toContain('id="analysis-result-panel"');
    expect(html).toContain('id="lifecycle-workspace-status" class="form-status" role="status"');
    expect(html).toContain('id="lifecycle-alias-status" class="form-status" role="status"');
    expect(html).toContain('id="lifecycle-rebuild-status" class="form-status" role="status"');
    expect(html).toContain('id="collection-status" class="form-status" role="status"');
    expect(html).toContain('id="download-button"');
    expect(html).toContain('aria-label="Workbench navigation"');
    expect(html).toContain('role="tablist" aria-label="Analysis result views"');
    expect(html).toContain('id="document-view" role="tabpanel"');
    expect(html).toContain('id="layer-list" aria-label="Annotation layers"');
    expect(html).toContain('id="annotated-text"');
    expect(html).toContain('id="document-window-position"');
    expect(html).toContain('id="document-window-label"');
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
    expect(html).toContain('id="alice-sample-button"');
    expect(html).toContain('id="pride-sample-button"');
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
    expect(html).toContain('<fieldset id="feature-picker" class="feature-picker" hidden>');
    expect(html).toContain("1 · Pick the new embedding model");
    expect(html).toContain("2 · Pick vector storage");
    expect(html).toContain("3 · Optional alias to switch");
    expect(html).toContain('id="annotation-drawer-backdrop"');
    expect(html).toContain('id="annotation-details-close"');
    expect(html).toContain('role="dialog" aria-modal="true"');
    expect(html).toContain('role="tablist" aria-label="Workbench navigation"');
    expect(html).toContain('data-workbench-tab="analysis"');
    expect(html).toContain('data-workbench-tab="workflows"');
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
    expect(html).toContain('id="server-search-index-help"');
    expect(html).toContain('Pick a configured index or');
    expect(html).toContain('data-workbench-jump="workflows">build an index from your own documents</button>');
    expect(html).toContain('<label for="server-search-query">');
    expect(html).toContain('id="server-search-status" role="status" aria-live="polite"');
    expect(html).toContain('id="server-search-results" aria-label="Server search results"');
    expect(html).toContain('role="group" aria-label="Results view"');
    expect(html).toContain('id="server-view-list-button"');
    expect(html).toContain('id="server-view-heatmap-button"');
    expect(html).toContain('id="server-search-heatmap" aria-label="Document heatmap of scored chunks"');
    expect(html).toContain('id="score-legend" aria-label="Similarity score color scale from minus one to one"');
    expect(html).toContain('id="search-source-text"');
    expect(html).toContain('id="search-inspector"');
    expect(html).toContain('id="search-analytics" aria-label="Selected document analytics"');
    expect(html).toContain('Search the documents you analyze');
    expect(html).toContain("<summary>What is a live index?</summary>");
    expect(html).toContain('id="workspace-name-input"');
    expect(html).toContain('The browser renders server scores and');
    expect(html).toContain('never performs vector ranking');
    expect(html).toContain('workspace-provider-select');
    expect(html).toContain('STANDARD_SEARCH_PROVIDER_TURBO_QUANT');
  });

  it("provides a guided corpus-to-search workflow with visible stage status", () => {
    expect(html).toContain('id="workflows-workbench"');
    expect(html).toContain("<summary>What this tab builds</summary>");
    expect(html).toContain('id="workflow-sample-button"');
    expect(html).toContain('id="workflow-mode-badge"');
    expect(html).toContain('id="workflow-corpus"');
    expect(html).toContain('id="workflow-teacher-select"');
    expect(html).toContain('id="workflow-query"');
    expect(html).toContain('id="workflow-run-button"');
    expect(html).toContain('id="workflow-stages"');
    expect(html).toContain('data-workflow-stage="analyze"');
    expect(html).toContain('data-workflow-stage="vocabulary"');
    expect(html).toContain('data-workflow-stage="train"');
    expect(html).toContain('data-workflow-stage="embed"');
    expect(html).toContain('data-workflow-stage="index"');
    expect(html).toContain('data-workflow-stage="search"');
    expect(html).toContain('id="workflow-analysis-results"');
    expect(html).toContain('id="workflow-search-heatmap"');
  });

  it("allows corpus search result counts up to fifty thousand", () => {
    expect(html).toContain('id="server-search-top-k" type="number" min="1" max="50000"');
  });

  it("holds inspector placeholders until an analyzed document is selected", () => {
    // Counts render as ellipses so zeros are never mistaken for analysis data.
    expect(html).toContain('<div><dt>Sentences</dt><dd id="search-sentence-count">…</dd></div>');
    expect(html).toContain('<dd id="search-token-count">…</dd>');
    expect(html).toContain('<dd id="search-entity-count">…</dd>');
    expect(html).toContain('<dd id="search-chunk-count">…</dd>');
    expect(html).toContain('<dd id="search-term-count">…</dd>');
    expect(html).toContain('id="search-original-panel"');
  });

  it("holds analysis summary placeholders until an analysis runs", () => {
    // The summary shows ellipses before the first analysis, so zero counts are
    // never mistaken for the result of an analysis that has not happened.
    expect(html).toContain('<div><dt>Layers</dt><dd id="result-layer-count">…</dd></div>');
    expect(html).toContain('<div><dt>Annotations</dt><dd id="result-annotation-count">…</dd></div>');
  });

  it("scopes the hero to the Analyze panel and bridges the two search tabs", () => {
    // The hero and the analyzer callout live inside the Analyze tab panel, so
    // other tabs render under their own headings.
    const analysisPanel = html.slice(html.indexOf('id="analysis-workbench"'), html.indexOf('id="server-search"'));
    expect(analysisPanel).toContain('id="playground-heading"');
    expect(analysisPanel).toContain('How to use the analyzer');
    expect(html).toContain('id="workspace-index-select"');
    expect(html).toContain('data-workbench-jump="session-search"');
    expect(html).toContain('data-workbench-jump="corpus-search"');
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

  it("gives text inputs the app font and the shared focus ring", () => {
    expect(css).toContain("button, input, textarea, select { font: inherit; }");
    expect(css).toMatch(/input:focus-visible[^{]*\{[^}]*outline:/s);
  });

  it("uses a full-width four-choice navigation row on narrow screens", () => {
    expect(css).toMatch(/@media \(max-width: 560px\)[\s\S]*?\.site-nav\s*\{[^}]*grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);[^}]*overflow:\s*visible;/);
    expect(css).not.toMatch(/@media \(max-width: 560px\)[\s\S]*?\.site-nav\s*\{[^}]*overflow-x:\s*auto;/);
  });

  it("keeps a large layer catalog from displacing the document on narrow screens", () => {
    expect(css).toMatch(/@media \(max-width: 850px\)[\s\S]*?\.layer-list\s*\{[^}]*max-height:\s*9rem;[^}]*overflow-y:\s*auto;/);
  });
});

describe("theme contract", () => {
  const css = readFileSync(fileURLToPath(new URL("../src/style.css", import.meta.url)), "utf8");

  it("defines the complete dark-first token block on bare :root", () => {
    expect(css).toMatch(/:root\s*\{[^}]*--ground:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--surface:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--line:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--text-strong:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--muted:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--accent-cyan:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--accent-rose:/s);
    expect(css).toMatch(/:root\s*\{[^}]*--warn:/s);
    expect(css).toMatch(/:root\s*\{[^}]*color-scheme:\s*dark/s);
  });

  it("redefines the light palette only behind the system-preference guard", () => {
    expect(css).toMatch(/@media \(prefers-color-scheme: light\)\s*\{\s*:root:not\(\[data-theme="dark"\]\)\s*\{/);
  });

  it("lets an explicit data-theme choice win in both directions", () => {
    expect(css).toContain(':root[data-theme="light"]');
    expect(css).toContain(':root[data-theme="dark"]');
  });

  it("offers the theme toggle in the site header", () => {
    expect(html).toContain('id="theme-toggle"');
  });
});
