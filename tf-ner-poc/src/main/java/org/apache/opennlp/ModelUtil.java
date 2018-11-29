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

package org.apache.opennlp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelUtil {

  public static Path writeModelToTmpDir(InputStream modelIn) throws IOException {
    Path tmpDir = Files.createTempDirectory("opennlp2");

    ZipInputStream zis = new ZipInputStream(modelIn);
    ZipEntry zipEntry = zis.getNextEntry();
    while(zipEntry != null){
      Path newFile = tmpDir.resolve(zipEntry.getName());

      Files.createDirectories(newFile.getParent());
      Files.copy(zis, newFile);

      // TODO: How to delete the tmp directory after we are done loading from it ?!
      newFile.toFile().deleteOnExit();

      zipEntry = zis.getNextEntry();
    }
    zis.closeEntry();
    zis.close();

    return tmpDir;
  }
}
