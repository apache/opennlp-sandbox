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
package org.apache.opennlp.grpc.webapp.defaultui;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ServiceLoader;
import java.util.zip.GZIPInputStream;

import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWebUiExtensionTest {

  @Test
  void registersDefaultHomepageThroughServiceLoader() {
    final WebUiExtension extension = ServiceLoader.load(WebUiExtension.class).findFirst()
        .orElseThrow();

    assertTrue(extension instanceof DefaultWebUiExtension);
    assertEquals("org.apache.opennlp.default-ui", extension.descriptor().id().value());
    assertEquals("/", extension.descriptor().mountPath().value());
    assertEquals("/META-INF/opennlp-grpc-ui/default",
        extension.descriptor().resourceRoot().value());
  }

  @Test
  void packagesThePinnedPublicDomainAliceDemo()
      throws IOException, NoSuchAlgorithmException {
    final String resource = "META-INF/opennlp-grpc-ui/default/data/"
        + "alice-in-wonderland.txt.gz";
    final InputStream compressed = DefaultWebUiExtensionTest.class.getClassLoader()
        .getResourceAsStream(resource);
    assertNotNull(compressed);
    final byte[] text;
    try (GZIPInputStream input = new GZIPInputStream(compressed)) {
      text = input.readAllBytes();
    }

    assertEquals(151_064, text.length);
    assertEquals("e16dadeebd96b871f754070d7cda0837898f37cd3f5ec22d94cf08a440e80833",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text)));
    final String content = new String(text, java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(content.contains("Alice’s Adventures in Wonderland"));
    assertTrue(content.contains("CHAPTER XII."));
    assertFalse(content.contains("Project Gutenberg"));
    assertFalse(content.contains("MILLENNIUM FULCRUM"));
  }
}
