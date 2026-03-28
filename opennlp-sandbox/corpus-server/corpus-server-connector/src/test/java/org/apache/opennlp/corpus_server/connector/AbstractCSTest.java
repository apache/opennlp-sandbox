/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.corpus_server.connector;

import org.apache.opennlp.corpus_server.impl.DerbyCorporaStore;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public abstract class AbstractCSTest {

  static {
    // Remove existing handlers
    org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();

    // Install SLF4J bridge
    org.slf4j.bridge.SLF4JBridgeHandler.install();
  }

  protected static final URL BASE_LOCATION = AbstractCSTest.class.getProtectionDomain().getCodeSource().getLocation();

  protected static void cleanTestDB() throws IOException {
    try {
      Path p = Path.of(getDBPathWithName());
      if (p.toFile().exists()) {
        try (var dirStream = Files.walk(p)) {
          dirStream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
        }
      }
    } catch (URISyntaxException e) {
      throw new IOException("Can't clean Test DB!", e);
    }
  }

  protected static String getDBPath() throws URISyntaxException {
    return Paths.get(BASE_LOCATION.toURI()).toAbsolutePath().getParent().toString() + File.separator;
  }

  private static String getDBPathWithName() throws URISyntaxException {
    return getDBPath() + DerbyCorporaStore.DB_NAME;
  }
}
