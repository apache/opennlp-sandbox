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
./mvnw -Dfrontend.skip=true package
```

The browser uses the same-origin HTTP facade. Analysis requests follow protobuf JSON exactly:

```json
{
  "document": { "rawText": "Text to analyze" },
  "profileId": "en-basic",
  "options": { "offsetEncoding": "OFFSET_ENCODING_UTF16_CODE_UNIT" }
}
```

The workbench requests UTF-16 offsets so typed annotation spans map directly to JavaScript string
indices. Its document view reads `document.layers.layers`, lists every typed layer, highlights the
selected layer over `document.rawText`, and exposes the complete annotation value when highlighted
text is selected. The result summary reports layer and annotation counts plus the active offset
encoding, and the layer list remains searchable for profiles that produce many results. Raw
protobuf JSON remains available in a separate result tab. The tool switcher loads the host's
`/api/v1/ui-extensions` catalog and links every discovered ServiceLoader extension. Its static
default entry remains usable when catalog discovery is unavailable.

The optional semantic workbench uses embedding annotations from the same typed layers. An analyzed
document can be added to an in-memory browser index, then ranked by cosine similarity against a
query analyzed with the selected profile. The index is scoped to the current tab. It is not
persisted and is not sent back to the service. Query similarity and typed sentiment scores are
shown as heatmaps. The graph view links the document to its layers and annotations, and annotation
nodes open the same details used by the Document view. Visualization code is loaded only when a
graph or heatmap is opened.

Frontend responsibilities are kept separate: `api.ts` owns HTTP, `document-shape.ts` owns wire
normalization, `embedding-workbench.ts` owns vector math and the session index,
`visualization-data.ts` creates renderer-neutral data, `charts.ts` is the Apache ECharts adapter,
and `semantic-workbench.ts` coordinates the semantic controls. `main.ts` remains the page entry
point and document inspector.

Model bundles are displayed as service capability information. A named analysis profile remains
the only user-selectable analysis option sent by the playground.

The runtime visualization dependency is Apache ECharts (Apache License 2.0), which brings zrender
(BSD 3-Clause) and tslib (Zero-Clause BSD). Their required license text is packaged in the JAR.
The build-only dependencies are Vite (MIT), Vitest (MIT), TypeScript (Apache License 2.0), and the
Node type declarations (MIT). The frontend Maven plugin is Apache License 2.0. Node and npm are
downloaded into `target/` for the build and are not packaged in the module JAR.
