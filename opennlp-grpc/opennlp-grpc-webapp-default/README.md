<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements. See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0.
-->

# Apache OpenNLP gRPC Default Webapp

This module provides the default `WebUiExtension`. Its Vite build is packaged at
`META-INF/opennlp-grpc-ui/default/`, and the extension mounts it at `/`.

The normal Maven lifecycle installs the pinned Node and npm versions under `target/`, runs
`npm ci --ignore-scripts`, executes the frontend tests, and creates the production assets.
Ignoring npm lifecycle scripts is intentional: all frontend dependencies are build and test
tools, and none requires an installation script. This keeps dependency installation from
executing package-provided code that the build does not need.

For Java-only reactor work, skip all frontend goals with:

```shell
mvn -Dfrontend.skip=true package
```

## End-to-end tests

`e2e/` holds a Playwright suite that drives a running server and gateway through a real browser:
tab scoping and bridging, a complete analyze round trip onto the calm Highlights overlay, corpus
search hit inspection, the Models & data readiness grid and catalog install states, the Trainer
gating states and server-fed pickers, and the Lifecycle flow of saving, aliasing, collecting and
making a live index read-only (the spec builds that index through the gateway and removes it
afterwards). It is intentionally not part of the Maven build because it needs browser binaries
and a live stack. Start the stack (for example the Docker demonstration image), then:

```shell
npx playwright install chromium   # once
OPENNLP_E2E_BASE_URL=http://127.0.0.1:7072 npm run e2e
```

Tests that depend on optional server state, such as a configured read-only corpus index, skip
with a reason instead of failing. Specs that leave artifacts behind on the server, the workflow
build and learning a vocabulary, run only with `OPENNLP_E2E_WORKFLOW_WRITE=1`. `tsc --noEmit` type-checks the suite as part of `npm test`, so
the Maven build still catches compile drift in the specs.

The browser uses the same-origin HTTP facade. Analysis requests follow protobuf JSON exactly.
The Analyze action consumes `/api/v1/analyze-progressive`, renders complete layers as their
branches finish, and replaces the partial state with the terminal canonical response. Copy,
download, semantic heatmap, and graph actions remain tied to that final response.
The default feature preset builds an inline profile from features that the service reports as
both supported and configured. For example:

```json
{
  "document": { "rawText": "Text to analyze" },
  "profile": {
    "steps": [
      "PIPELINE_STEP_LANGUAGE_DETECT",
      "PIPELINE_STEP_SENTENCE_DETECT",
      "PIPELINE_STEP_TOKENIZE",
      "PIPELINE_STEP_POS_TAG",
      "PIPELINE_STEP_LEMMATIZE",
      "PIPELINE_STEP_STEM",
      "PIPELINE_STEP_TERM_VECTOR"
    ]
  },
  "options": { "offsetEncoding": "OFFSET_ENCODING_UTF16_CODE_UNIT" },
  "chunkEmbedConfigs": [
    {
      "configId": "sentence-chunks",
      "resultSetName": "Sentence chunks",
      "chunking": {
        "strategy": { "standard": "STANDARD_CHUNKING_STRATEGY_SENTENCE" },
        "cleanText": true,
        "preserveUrls": true
      }
    },
    {
      "configId": "token-chunks",
      "resultSetName": "Token windows",
      "chunking": {
        "strategy": { "standard": "STANDARD_CHUNKING_STRATEGY_TOKEN" },
        "chunkSize": 128,
        "chunkOverlap": 16,
        "cleanText": true,
        "preserveUrls": true
      }
    }
  ]
}
```

The named profiles and server automatic selection remain available as explicit alternatives. The
sentence and token-window chunk strategies are independent controls, so one analysis can return
both projections. Chunks do not require an embedding model. If a configured model is selected,
the same controls request attached chunk embeddings.

The feature matrix shows every pipeline step implemented by the server. Configured steps can be
selected individually; supported steps that lack a model or data resource remain visible with a
clear unavailable status. Choosing a dependent feature automatically adds its configured sentence,
token, POS, or NER backbone in canonical execution order.

The Models & data tab presents the same readiness inventory outside the request form and shows the
checksum-required `install-resource` server command. Installation stays an explicit operator action
before server startup; the unauthenticated browser facade never accepts an arbitrary download URL
or writes into the server's model directory.

The workbench requests UTF-16 offsets so typed annotation spans map directly to JavaScript string
indices. The Analyze tab uses the full available width. Its source editor and annotated document
scroll vertically for long input and never require horizontal scrolling. Result projections reuse
the same response: Document, Chunks, Heatmap, Graph, and Protobuf JSON. The Document projection
reads `document.layers.layers`, places every typed layer in a searchable horizontal rail, and
highlights the selected layer over `document.rawText`. Selecting text, a graph node, or a chunk
opens typed details in a dismissible side drawer instead of narrowing the document canvas. The
result summary reports layer and annotation counts plus the active offset encoding.

A term-vector layer combines into one stacked bar instead of one chip per term: each segment is
sized by the term's frequency, the highest-frequency terms are shown and the tail folds into a
remainder segment, and selecting the bar pops out the full frequency-ranked term list in the
drawer, where each row drills into that term's typed annotation.

Combined word popovers omit sentence embeddings that merely cover the selected word. Selecting
the Embeddings layer exposes vectors at their sentence or document span, while selecting a chunk
exposes vectors attached to that complete chunk. Vector details show the model, granularity,
dimension count, and first three values. Copy vector copies the complete numeric array.

The top-level navigation separates Analyze, immutable Corpus search, and process-local Workspace
search. The tool switcher is a separate extension-level control. It loads the host's
`/api/v1/ui-extensions` catalog, remains hidden when only the default extension is installed, and
links every additional ServiceLoader extension when present.

The Workspace search workbench uses chunk embeddings already present in the same document shape.
The browser sends only document identity, source text, metadata, offset encoding, and selected chunk
embedding groups to `IndexDocuments`; the gRPC server validates routes, spans, dimensions, and
limits before atomically publishing a process-local flat or TurboQuant index. The first query automatically adds
the current document when needed, while the explicit Add button supports building a multi-document
workspace. Queries go through `SearchIndex`, and only server-ranked scores return to the browser.
Clearing the workspace calls `DeleteSearchIndex`. Query similarity and typed sentiment scores are
shown inline over continuous source text. All chunk projections are selected by default and render
as separate lanes; one projection can be selected when a narrower comparison is useful. Each lane
uses its own process-local TurboQuant index and the typed exhaustive search request. Overlapping
token windows remain individually selectable below the source projection, and unreturned chunks
remain gray when the response-byte cap truncates a result. Selecting a scored chunk opens its
score, offsets, route, provenance, and every intersecting non-vector annotation in the detail
drawer. The graph view switches among the document layer overview, labeled dependency arcs over
the token sequence, and a directed entity relation network. Selecting a token, entity, or edge
opens the same typed details used by the Document view. Modes remain disabled until the server
returns their typed layer. ECharts is loaded only when the graph is opened.

The bundled Alice's Adventures in Wonderland demo exercises the long-document path without a
network dependency. It is a deterministic gzip of the public-domain novel text with Project
Gutenberg's header, footer, and branding removed; `public/data/README.md` records the source and
both artifact hashes. The Document projection keeps the complete response while rendering one
16,000-character annotation window at a time, selected with a native position slider. Large
results do not eagerly create a second, formatted JSON copy. Copy JSON and Download JSON remain
explicit actions when the complete protobuf JSON is needed. Graphs keep a balanced 120-annotation
overview, and explicit complete-graph expansion is limited to 5,000 annotations.

The immutable Corpus search lens is separate from the process-local Workspace search. It discovers
immutable indexes from `GET /api/v1/search-indexes` and sends document-shaped queries to
`POST /api/v1/search`. Search response parsing is isolated in `search-adapter.ts`. Each result
keeps its numeric cosine score, authoritative source span, indexed chunk text, configured and
actual query embedding routes, and corpus provenance. Discovered query and response byte caps are
shown with the selected index, and server response truncation is reported as a bounded successful
result. The inspector uses a fixed red-neutral-green scale over `[-1, 1]`, compares the original
and indexed text, and lazily analyzes the selected source document when typed layers are not
already present. Shared offset utilities reject invalid UTF-8 and UTF-16 boundaries and
out-of-range code-point offsets.

Frontend responsibilities are kept separate: `api.ts` owns HTTP, `analysis-config.ts` owns pure
capability and request shaping, `analysis-controls.ts` owns the associated form controls,
`document-shape.ts` owns wire normalization, `chunk-projection.ts` owns chunk wire parsing, and
`chunk-projection-view.ts` owns its DOM rendering. `annotation-drawer.ts` owns detail disclosure and
focus restoration. `workbench-navigation.ts` owns the top-level tabs. `visualization-data.ts`
creates renderer-neutral data, `document-heatmap-view.ts` renders inline projection lanes, and
`charts.ts` is the Apache ECharts graph adapter.
`semantic-workbench.ts` coordinates server-owned workspace indexing, and
`server-search-workbench.ts` coordinates immutable corpus search. Locale-independent cursor
helpers in `text-utils.ts` own casing, whitespace, identifier splitting, and tooltip escaping.
`main.ts` remains the page entry point and document projection coordinator.

The runtime visualization dependency is Apache ECharts (Apache License 2.0), which brings zrender
(BSD 3-Clause) and tslib (Zero-Clause BSD). Their required license text is packaged in the JAR.
The build-only dependencies are Vite (MIT), Vitest (MIT), TypeScript (Apache License 2.0),
Playwright Test (Apache License 2.0), and the Node type declarations (MIT). The frontend Maven plugin is Apache License 2.0. Node and npm are
downloaded into `target/` for the build and are not packaged in the module JAR.
