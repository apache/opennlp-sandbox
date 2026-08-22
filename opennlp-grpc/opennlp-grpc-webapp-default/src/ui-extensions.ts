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
  asciiLowerCase,
  asciiUpperCase,
  firstCodePoints,
  isWhitespaceCodePoint,
  splitWords,
} from "./text-utils";

export interface UiExtension {
  id: string;
  title: string;
  mountPath: string;
}

export function readUiExtensions(value: unknown): UiExtension[] {
  if (!isRecord(value) || !Array.isArray(value.extensions)) {
    return [];
  }
  const extensions: UiExtension[] = [];
  const ids = new Set<string>();
  const mounts = new Set<string>();
  for (const candidate of value.extensions) {
    if (!isRecord(candidate)) {
      continue;
    }
    const id = readNonBlankString(candidate.id);
    const title = readNonBlankString(candidate.title);
    const mountPath = readMountPath(candidate.mountPath);
    if (!id || !title || !mountPath || ids.has(id) || mounts.has(mountPath)) {
      continue;
    }
    ids.add(id);
    mounts.add(mountPath);
    extensions.push({ id, title, mountPath });
  }
  return extensions;
}

export function activeUiExtension(extensions: UiExtension[], pathname: string): string | undefined {
  return extensions
    .filter((extension) => matchesMount(pathname, extension.mountPath))
    .sort((left, right) => right.mountPath.length - left.mountPath.length)[0]?.id;
}

export function extensionInitials(title: string): string {
  const words = splitWords(title);
  if (words.length > 1) {
    return asciiUpperCase(`${firstCodePoints(words[0] ?? "", 1)}${firstCodePoints(words[1] ?? "", 1)}`);
  }
  return asciiUpperCase(firstCodePoints(words[0] ?? "UI", 2));
}

function matchesMount(pathname: string, mountPath: string): boolean {
  return mountPath === "/" || pathname === mountPath || pathname.startsWith(`${mountPath}/`);
}

function readNonBlankString(value: unknown): string | undefined {
  if (typeof value !== "string" || value.trim().length === 0 || value !== value.trim()) {
    return undefined;
  }
  return value;
}

function readMountPath(value: unknown): string | undefined {
  const path = readNonBlankString(value);
  if (!path || !path.startsWith("/") || (path.length > 1 && path.endsWith("/"))) {
    return undefined;
  }
  if (path.length === 1) {
    return path;
  }
  if (isApiNamespace(path)) {
    return undefined;
  }
  for (const character of path) {
    const codePoint = character.codePointAt(0)!;
    if (character === "\\" || character === "?" || character === "#" || character === "%"
        || isControlCodePoint(codePoint) || isWhitespaceCodePoint(codePoint)) {
      return undefined;
    }
  }
  let segmentStart = 1;
  for (let index = 1; index <= path.length; index++) {
    if (index !== path.length && path.charAt(index) !== "/") {
      continue;
    }
    const segmentLength = index - segmentStart;
    if (segmentLength === 0
        || (segmentLength === 1 && path.charAt(segmentStart) === ".")
        || (segmentLength === 2 && path.charAt(segmentStart) === "."
          && path.charAt(segmentStart + 1) === ".")) {
      return undefined;
    }
    segmentStart = index + 1;
  }
  return path;
}

function isApiNamespace(path: string): boolean {
  return path.length >= 4
    && asciiLowerCase(path.slice(0, 4)) === "/api"
    && (path.length === 4 || path.charAt(4) === "/");
}

function isControlCodePoint(codePoint: number): boolean {
  return codePoint <= 0x001f || (codePoint >= 0x007f && codePoint <= 0x009f);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
