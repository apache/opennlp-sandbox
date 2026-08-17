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

import { beforeEach, describe, expect, it } from "vitest";

import { AnnotationDrawer } from "../src/annotation-drawer";
import { readDocumentShape } from "../src/document-shape";

describe("annotation drawer", () => {
  beforeEach(() => {
    document.body.innerHTML = `
      <div id="annotation-drawer-backdrop" hidden></div>
      <aside id="annotation-details" hidden>
        <button id="annotation-details-close" type="button">Close</button>
        <div id="annotation-details-content"></div>
      </aside>`;
  });

  it("shows every typed annotation covering a combined text segment", () => {
    const shape = readDocumentShape({
      document: {
        rawText: "Paris",
        offsetEncoding: "OFFSET_ENCODING_UTF16_CODE_UNIT",
        layers: { layers: [
          { id: "opennlp:tokens", stringValues: { annotations: [{ span: { end: 5 }, value: "Paris" }] } },
          { id: "opennlp:entities", entityValues: { annotations: [{
            annotationSpan: { end: 5 }, entityType: "location", text: "Paris",
          }] } },
        ] },
      },
    });
    const entries = shape.layers.flatMap((layer) => layer.annotations.map((annotation) => ({ layer, annotation })));
    const drawer = new AnnotationDrawer();

    drawer.showAnnotations("Paris", 0, 5, entries);

    const panel = document.getElementById("annotation-details")!;
    expect(panel.hidden).toBe(false);
    expect(panel.textContent).toContain("2 annotations");
    expect(panel.textContent).toContain("opennlp:tokens");
    expect(panel.textContent).toContain("opennlp:entities");
    expect(panel.textContent).toContain("location");
  });
});
