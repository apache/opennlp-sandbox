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
  const words = title.trim().split(/\s+/u).filter(Boolean);
  if (words.length > 1) {
    return `${firstCharacter(words[0])}${firstCharacter(words[1])}`.toLocaleUpperCase();
  }
  return Array.from(words[0] ?? "UI").slice(0, 2).join("").toLocaleUpperCase();
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
  return path;
}

function firstCharacter(value: string | undefined): string {
  return Array.from(value ?? "")[0] ?? "";
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
