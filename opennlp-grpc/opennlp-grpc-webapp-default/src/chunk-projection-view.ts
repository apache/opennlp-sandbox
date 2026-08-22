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

import {
  readChunkProjection,
  type ChunkProjectionGroup,
  type ChunkProjectionItem,
} from "./chunk-projection";
import { emptyMessage, requiredElement } from "./ui-utils";

export class ChunkProjectionView {
  readonly #container = requiredElement<HTMLElement>("chunk-projection");
  readonly #select: (group: ChunkProjectionGroup, chunk: ChunkProjectionItem, trigger: HTMLElement) => void;

  constructor(select: (group: ChunkProjectionGroup, chunk: ChunkProjectionItem, trigger: HTMLElement) => void) {
    this.#select = select;
  }

  render(value: unknown): void {
    const groups = readChunkProjection(value);
    if (groups.length === 0) {
      this.#container.replaceChildren(emptyMessage("No chunk groups were returned for this analysis."));
      return;
    }
    this.#container.replaceChildren(...groups.map((group) => this.groupColumn(group)));
  }

  private groupColumn(group: ChunkProjectionGroup): HTMLElement {
    const section = document.createElement("section");
    section.className = "chunk-group-column";
    const header = document.createElement("header");
    const identity = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = group.title;
    const strategy = document.createElement("span");
    strategy.textContent = group.strategy;
    identity.append(title, strategy);
    const count = document.createElement("strong");
    count.textContent = `${group.chunks.length} ${group.chunks.length === 1 ? "chunk" : "chunks"}`;
    header.append(identity, count);
    const list = document.createElement("div");
    list.className = "chunk-card-list";
    if (group.chunks.length === 0) {
      list.append(emptyMessage("This strategy returned no chunks."));
    } else {
      for (const chunk of group.chunks) {
        list.append(this.chunkCard(group, chunk));
      }
    }
    section.append(header, list);
    return section;
  }

  private chunkCard(group: ChunkProjectionGroup, chunk: ChunkProjectionItem): HTMLButtonElement {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "chunk-card";
    const meta = document.createElement("span");
    meta.textContent = `#${chunk.index}  ·  ${chunk.start}..${chunk.end}`;
    const content = document.createElement("strong");
    content.textContent = chunk.text;
    const embeddings = document.createElement("small");
    embeddings.textContent = chunk.embeddingCount === 0
      ? "No attached vector"
      : `${chunk.embeddingCount} attached ${chunk.embeddingCount === 1 ? "vector" : "vectors"}`;
    button.append(meta, content, embeddings);
    button.addEventListener("click", () => this.#select(group, chunk, button));
    return button;
  }
}
