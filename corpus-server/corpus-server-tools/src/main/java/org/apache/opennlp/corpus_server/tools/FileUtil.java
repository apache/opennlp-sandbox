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

package org.apache.opennlp.corpus_server.tools;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

  static byte[] fileToBytes(File file) throws IOException {

    try (ByteArrayOutputStream fileBytes = new ByteArrayOutputStream((int) file.length());
         InputStream fileIn = new BufferedInputStream(new FileInputStream(file))) {
      byte[] buffer = new byte[1024];
      int length;
      while ((length = fileIn.read(buffer)) > 0) {
        fileBytes.write(buffer, 0, length);
      }
      return fileBytes.toByteArray();
    }
  }

}
