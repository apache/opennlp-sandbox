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

import { describe, expect, it } from "vitest";

import { activeUiExtension, extensionInitials, readUiExtensions } from "../src/ui-extensions";

const extensions = [
  { id: "default", title: "Apache OpenNLP", mountPath: "/" },
  { id: "search", title: "Embedding Search", mountPath: "/search" },
  { id: "detail", title: "Search Detail", mountPath: "/search/detail" },
];

describe("UI extension catalog", () => {
  it("reads only complete, unique extension entries", () => {
    expect(readUiExtensions({
      extensions: [
        extensions[0],
        extensions[1],
        { id: "search", title: "Duplicate", mountPath: "/duplicate" },
        { id: "missing-mount", title: "Incomplete" },
        { id: "relative", title: "Relative", mountPath: "tools" },
        null,
      ],
    })).toEqual(extensions.slice(0, 2));
  });

  it("selects the most specific matching mount", () => {
    expect(activeUiExtension(extensions, "/search/detail/item")).toBe("detail");
    expect(activeUiExtension(extensions, "/search/results")).toBe("search");
    expect(activeUiExtension(extensions, "/unknown")).toBe("default");
  });

  it("creates short text icons without assuming provider artwork", () => {
    expect(extensionInitials("Embedding Search")).toBe("ES");
    expect(extensionInitials("Tokenizer")).toBe("TO");
  });
});
