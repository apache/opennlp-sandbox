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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.webapp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;

final class WebUiAssetResolver {

  private final WebUiExtensionRegistry registry;

  /**
   * Creates an asset resolver.
   *
   * @param registry The available UI extensions.
   * @throws IllegalArgumentException If {@code registry} is {@code null}.
   */
  WebUiAssetResolver(WebUiExtensionRegistry registry) {
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    this.registry = registry;
  }

  /**
   * Resolves one safe HTTP path to an extension resource.
   *
   * @param rawPath The undecoded request path.
   * @return The asset, or an empty value when the path is invalid or missing.
   */
  Optional<WebUiAsset> resolve(String rawPath) {
    if (!isSafePath(rawPath)) {
      return Optional.empty();
    }
    for (WebUiExtension extension : registry.extensions()) {
      WebUiExtensionDescriptor descriptor = extension.descriptor();
      String mount = descriptor.mountPath().value();
      if (!matchesMount(rawPath, mount)) {
        continue;
      }
      String relative = relativePath(rawPath, mount);
      String resourceRoot = descriptor.resourceRoot().value().substring(1);
      String resourceName = resourceRoot + "/" + (relative.isEmpty() ? "index.html" : relative);
      try (InputStream input = extension.resourceClassLoader().getResourceAsStream(resourceName)) {
        if (input == null) {
          return Optional.empty();
        }
        return Optional.of(new WebUiAsset(contentType(resourceName), input.readAllBytes()));
      } catch (IOException exception) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Returns whether a request path belongs to a mount.
   *
   * @param path The request path.
   * @param mount The extension mount.
   * @return {@code true} when the mount owns the path.
   */
  private boolean matchesMount(String path, String mount) {
    if (mount.equals("/")) {
      return path.startsWith("/");
    }
    return path.equals(mount) || path.startsWith(mount + "/");
  }

  /**
   * Returns the resource path relative to its mount.
   *
   * @param path The request path.
   * @param mount The matching extension mount.
   * @return The relative resource path.
   */
  private String relativePath(String path, String mount) {
    if (path.equals(mount)) {
      return "";
    }
    if (mount.equals("/")) {
      return path.substring(1);
    }
    return path.substring(mount.length() + 1);
  }

  /**
   * Returns whether a raw request path is safe for classpath lookup.
   *
   * @param path The undecoded request path.
   * @return {@code true} when the path is safe.
   */
  private boolean isSafePath(String path) {
    if (path == null || !path.startsWith("/") || path.indexOf('\\') >= 0
        || path.indexOf('%') >= 0 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0
        || path.contains("//")) {
      return false;
    }
    String remainder = path.substring(1);
    int segmentStart = 0;
    for (int index = 0; index <= remainder.length(); index++) {
      if (index == remainder.length() || remainder.charAt(index) == '/') {
        // Every segment is checked, including empty ones, matching the
        // previous split("/", -1) semantics.
        if (!isSafeSegment(remainder.substring(segmentStart, index))) {
          return false;
        }
        segmentStart = index + 1;
      }
    }
    return true;
  }

  /**
   * Returns whether one path segment is safe for classpath lookup.
   *
   * @param segment The path segment.
   * @return {@code true} when the segment is safe.
   */
  private static boolean isSafeSegment(String segment) {
    if (segment.equals(".") || segment.equals("..")) {
      return false;
    }
    for (int index = 0; index < segment.length(); index++) {
      if (Character.isISOControl(segment.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the response media type for a resource name.
   *
   * @param resourceName The classpath resource name.
   * @return The response media type.
   */
  private String contentType(String resourceName) {
    String lower = resourceName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".html")) {
      return "text/html; charset=utf-8";
    }
    if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
      return "text/javascript; charset=utf-8";
    }
    if (lower.endsWith(".css")) {
      return "text/css; charset=utf-8";
    }
    if (lower.endsWith(".json") || lower.endsWith(".map")) {
      return "application/json; charset=utf-8";
    }
    if (lower.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".ico")) {
      return "image/x-icon";
    }
    if (lower.endsWith(".woff2")) {
      return "font/woff2";
    }
    return "application/octet-stream";
  }
}
