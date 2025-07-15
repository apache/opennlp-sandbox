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

package opennlp.tools.similarity.apps.taxo_builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DomainTaxonomyExtenderTest {

  @TempDir
  public Path tempDir;

  @Test
  void testExtendTaxonomy() throws IOException {
    // test
    DomainTaxonomyExtender dte = new DomainTaxonomyExtender("musicTaxonomyRoot.csv");
    dte.extendTaxonomy("music", "en", tempDir);

    // verify
    final String outDir = tempDir.toAbsolutePath().toString();
    File dat = new File(Paths.get(
            outDir, DomainTaxonomyExtender.TAXO_FILENAME).toString());
    File csv = new File(Paths.get(
            outDir, DomainTaxonomyExtender.TAXO_FILENAME + ".csv").toString());
    File csvList = new File(Paths.get(
            outDir,DomainTaxonomyExtender.TAXO_FILENAME + "_ListEntries" + ".csv").toString());

    assertTrue(dat.exists());
    assertTrue(csv.exists());
    assertTrue(csvList.exists());
    assertTrue(Files.readAllBytes(dat.toPath()).length > 0);
    assertTrue(Files.readAllBytes(csv.toPath()).length > 0);
    assertTrue(Files.readAllBytes(csvList.toPath()).length > 0);
  }
}
