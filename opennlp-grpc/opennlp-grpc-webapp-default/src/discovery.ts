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

export interface DiscoveryOption {
  id: string;
  label: string;
}

export function discoverProfiles(value: unknown): DiscoveryOption[] {
  return normalizeOptions(findArray(value, ["availableProfileIds", "profiles", "availableProfiles", "analysisProfiles"]));
}

export function discoverModelBundles(value: unknown): DiscoveryOption[] {
  return normalizeOptions(findArray(value, ["modelBundles", "bundles", "models"]));
}

function findArray(value: unknown, keys: string[]): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (!isRecord(value)) {
    return [];
  }
  for (const key of keys) {
    if (Array.isArray(value[key])) {
      return value[key];
    }
  }
  for (const nestedKey of ["data", "service", "capabilities"]) {
    const nested = value[nestedKey];
    if (isRecord(nested)) {
      const match = findArray(nested, keys);
      if (match.length > 0) {
        return match;
      }
    }
  }
  return [];
}

function normalizeOptions(values: unknown[]): DiscoveryOption[] {
  const options = values.flatMap((value): DiscoveryOption[] => {
    if (typeof value === "string") {
      const id = value.trim();
      return id ? [{ id, label: id }] : [];
    }
    if (!isRecord(value)) {
      return [];
    }
    const id = firstString(value.bundleId, value.id, value.key, value.name, value.modelBundleId);
    if (!id) {
      return [];
    }
    return [{ id, label: firstString(value.title, value.displayName, value.label, value.name) || id }];
  });
  return [...new Map(options.map((option) => [option.id, option])).values()];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function firstString(...values: unknown[]): string {
  return values.find((value): value is string => typeof value === "string" && value.trim().length > 0)?.trim() ?? "";
}
