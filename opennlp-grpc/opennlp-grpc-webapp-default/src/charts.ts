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

import { GraphChart, HeatmapChart, type GraphSeriesOption, type HeatmapSeriesOption } from "echarts/charts";
import {
  GridComponent,
  TooltipComponent,
  VisualMapComponent,
  type GridComponentOption,
  type TooltipComponentOption,
  type VisualMapComponentOption,
} from "echarts/components";
import { getInstanceByDom, init, use, type ComposeOption, type ECharts } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";

import { escapeHtml } from "./text-utils";
import type {
  DocumentGraph,
  DocumentGraphNode,
  HeatmapRow,
  LinguisticGraph,
  LinguisticGraphLink,
  LinguisticGraphNode,
} from "./visualization-data";

type ChartOption = ComposeOption<
  GraphSeriesOption | HeatmapSeriesOption | GridComponentOption | TooltipComponentOption | VisualMapComponentOption
>;

use([GraphChart, HeatmapChart, GridComponent, TooltipComponent, VisualMapComponent, CanvasRenderer]);

export interface ChartHandle {
  dispose(): void;
  resize(): void;
}

export function renderHeatmap(
  element: HTMLElement,
  rows: HeatmapRow[],
  emptyMessage: string,
  onSelect?: (row: HeatmapRow) => void,
): ChartHandle | undefined {
  disposeExisting(element);
  if (rows.length === 0) {
    element.textContent = emptyMessage;
    return undefined;
  }
  element.replaceChildren();
  const chart = init(element, undefined, { renderer: "canvas" });
  const values = rows.map((row, index) => [index, 0, row.score, row.label, row.category ?? row.modelId ?? ""]);
  chart.setOption({
    animationDuration: 350,
    grid: { left: 12, right: 12, top: 10, bottom: 40, containLabel: true },
    tooltip: {
      formatter: (parameters: unknown) => heatmapTooltip(parameters),
    },
    xAxis: {
      type: "category",
      data: rows.map((_, index) => String(index + 1)),
      axisLabel: { color: "#8491a8" },
      axisLine: { lineStyle: { color: "#3a465c" } },
      name: "Text segment",
      nameTextStyle: { color: "#8491a8" },
    },
    yAxis: { type: "category", data: ["score"], show: false },
    visualMap: {
      min: Math.min(-1, ...rows.map((row) => row.score)),
      max: Math.max(1, ...rows.map((row) => row.score)),
      calculable: false,
      orient: "horizontal",
      left: "center",
      bottom: 0,
      textStyle: { color: "#aeb9ca" },
      inRange: { color: ["#b42318", "#e5e7eb", "#16835a"] },
    },
    series: [{ type: "heatmap", data: values, itemStyle: { borderColor: "#101827", borderWidth: 3 } }],
  } satisfies ChartOption);
  chart.on("click", (event) => {
    const dataIndex = typeof event.dataIndex === "number" ? event.dataIndex : -1;
    const row = rows[dataIndex];
    if (row && onSelect) {
      onSelect(row);
    }
  });
  element.dataset.chartActive = "true";
  return handle(chart, element);
}

export function renderDocumentGraph(
  element: HTMLElement,
  graph: DocumentGraph,
  onSelect: (node: DocumentGraphNode) => void,
): ChartHandle | undefined {
  disposeExisting(element);
  if (graph.nodes.length <= 1) {
    element.textContent = "Analyze a document with typed layers to build its graph.";
    return undefined;
  }
  element.replaceChildren();
  const chart = init(element, undefined, { renderer: "canvas" });
  chart.setOption({
    animationDuration: 450,
    tooltip: { formatter: "{b}" },
    series: [{
      type: "graph",
      layout: "force",
      roam: true,
      draggable: true,
      force: { repulsion: 105, edgeLength: [35, 90], gravity: 0.08 },
      label: { show: true, color: "#dbe7f6", fontSize: 10, position: "right" },
      lineStyle: { color: "#52627c", opacity: 0.7, curveness: 0.08 },
      emphasis: { focus: "adjacency", lineStyle: { width: 2 } },
      data: graph.nodes.map((node) => ({
        id: node.id,
        name: node.label,
        symbolSize: node.kind === "document" ? 44 : node.kind === "layer" ? 28 : 12,
        itemStyle: { color: node.kind === "document" ? "#3f8efc" : node.kind === "layer" ? "#42c8a3" : "#c37cff" },
      })),
      links: graph.links,
    }],
  } satisfies ChartOption);
  chart.on("click", (event) => {
    const id = typeof event.data === "object" && event.data !== null && "id" in event.data
      ? String(event.data.id)
      : "";
    const node = graph.nodes.find((candidate) => candidate.id === id);
    if (node) {
      onSelect(node);
    }
  });
  element.dataset.chartActive = "true";
  return handle(chart, element);
}

export function renderDependencyGraph(
  element: HTMLElement,
  graph: LinguisticGraph,
  onSelect: (item: LinguisticGraphNode | LinguisticGraphLink) => void,
): ChartHandle | undefined {
  return renderLinguisticGraph(
    element,
    graph,
    "Run dependency parsing to see token-to-token arcs.",
    false,
    onSelect,
  );
}

export function renderEntityRelationGraph(
  element: HTMLElement,
  graph: LinguisticGraph,
  onSelect: (item: LinguisticGraphNode | LinguisticGraphLink) => void,
): ChartHandle | undefined {
  return renderLinguisticGraph(
    element,
    graph,
    "Run named-entity recognition and relation extraction to build an entity network.",
    true,
    onSelect,
  );
}

function renderLinguisticGraph(
  element: HTMLElement,
  graph: LinguisticGraph,
  emptyMessage: string,
  forceLayout: boolean,
  onSelect: (item: LinguisticGraphNode | LinguisticGraphLink) => void,
): ChartHandle | undefined {
  disposeExisting(element);
  if (graph.nodes.length === 0 || graph.links.length === 0) {
    element.textContent = emptyMessage;
    return undefined;
  }
  element.replaceChildren();
  const chart = init(element, undefined, { renderer: "canvas" });
  const tokenCount = graph.nodes.filter((node) => node.kind === "token").length;
  const data = graph.nodes.map((node, index) => ({
    id: node.id,
    name: node.label,
    symbolSize: node.kind === "root" ? 42 : node.kind === "entity" ? 38 : 30,
    itemStyle: {
      color: node.kind === "root" ? "#3f8efc" : node.kind === "entity" ? "#c37cff" : "#42c8a3",
    },
    ...(forceLayout ? {} : {
      x: node.kind === "root" ? Math.max(0, tokenCount - 1) * 55 : Math.max(0, index - 1) * 110,
      y: node.kind === "root" ? 10 : 180,
    }),
  }));
  const links = graph.links.map((link, index) => ({
    id: `linguistic-link:${index}`,
    name: link.label,
    source: link.source,
    target: link.target,
  }));
  chart.setOption({
    animationDuration: 450,
    tooltip: { formatter: "{b}" },
    series: [{
      type: "graph",
      layout: forceLayout ? "force" : "none",
      roam: true,
      draggable: forceLayout,
      force: forceLayout ? { repulsion: 260, edgeLength: [110, 180], gravity: 0.1 } : undefined,
      label: { show: true, color: "#dbe7f6", fontSize: 11, position: "bottom" },
      edgeLabel: { show: true, formatter: linguisticEdgeLabel, color: "#aeb9ca", fontSize: 10 },
      edgeSymbol: ["none", "arrow"],
      edgeSymbolSize: [0, 9],
      lineStyle: { color: "#71819a", opacity: 0.85, curveness: forceLayout ? 0.12 : 0.28 },
      emphasis: { focus: "adjacency", lineStyle: { width: 2 } },
      data,
      links,
    }],
  } satisfies ChartOption);
  chart.on("click", (event) => {
    const id = typeof event.data === "object" && event.data !== null && "id" in event.data
      ? String(event.data.id)
      : "";
    const node = graph.nodes.find((candidate) => candidate.id === id);
    if (node) {
      onSelect(node);
      return;
    }
    const prefix = "linguistic-link:";
    if (id.startsWith(prefix)) {
      const link = graph.links[Number(id.slice(prefix.length))];
      if (link) {
        onSelect(link);
      }
    }
  });
  element.dataset.chartActive = "true";
  return handle(chart, element);
}

function linguisticEdgeLabel(parameters: unknown): string {
  if (typeof parameters !== "object" || parameters === null || !("data" in parameters)) {
    return "";
  }
  const data = (parameters as { data?: unknown }).data;
  if (typeof data !== "object" || data === null || !("name" in data)) {
    return "";
  }
  return String(data.name ?? "");
}

function heatmapTooltip(parameters: unknown): string {
  if (typeof parameters !== "object" || parameters === null || !("data" in parameters)) {
    return "";
  }
  const data = (parameters as { data?: unknown }).data;
  if (!Array.isArray(data)) {
    return "";
  }
  const score = typeof data[2] === "number" ? data[2].toFixed(4) : "unknown";
  return `${escapeHtml(String(data[3] ?? "Text segment"))}<br/>Score: ${score}`
    + (data[4] ? `<br/>${escapeHtml(String(data[4]))}` : "");
}

function handle(chart: ECharts, element: HTMLElement): ChartHandle {
  return {
    dispose: () => {
      chart.dispose();
      delete element.dataset.chartActive;
    },
    resize: () => chart.resize(),
  };
}

function disposeExisting(element: HTMLElement): void {
  const instance = getInstanceByDom(element);
  if (instance) {
    instance.dispose();
  }
  delete element.dataset.chartActive;
}
