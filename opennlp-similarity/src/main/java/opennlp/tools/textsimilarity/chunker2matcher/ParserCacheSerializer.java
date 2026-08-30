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

package opennlp.tools.textsimilarity.chunker2matcher;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.slf4j.LoggerFactory;

public class ParserCacheSerializer {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  private static final boolean JAVA_OBJECT_SERIALIZATION = false;
  private static final String RESOURCE_DIR = "src/test/resources/";
  private static final String PARSE_CACHE_FILE_NAME = "sentence_parseObject.dat";
  private static final String PARSE_CACHE_FILE_NAME_CSV = "sentence_parseObject.csv";

  // Written outside the source tree: forked JVMs racing on a tracked file corrupt it.
  private static final String CACHE_DIR_PROPERTY = "opennlp.similarity.parseCacheDir";
  private static final String DEFAULT_CACHE_DIR = "target";

  public static void writeObject(Object objectToSerialize) {
    if (JAVA_OBJECT_SERIALIZATION) {
      String filename = RESOURCE_DIR + PARSE_CACHE_FILE_NAME;
      try(FileOutputStream fos = new FileOutputStream(filename);
          ObjectOutputStream out = new ObjectOutputStream(fos)) {

        out.writeObject(objectToSerialize);
      } catch (IOException ioe) {
        LOG.error(ioe.getMessage(), ioe);
      }
    } else {
      Map<String, String[][]> sentence_parseObject = (Map<String, String[][]>) objectToSerialize;
      final List<String> keys = new ArrayList<>(sentence_parseObject.keySet());

      final Path p = cacheOutputFile();
      try {
        final Path dir = p.getParent();
        if (dir != null) {
          Files.createDirectories(dir);
        }
      } catch (IOException e) {
        LOG.error("Cannot create parse cache directory for {}: {}", p, e.getMessage());
        return;
      }
      try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8,
              StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
        for (String k : keys) {
          String[][] triplet = sentence_parseObject.get(k);
          writer.writeNext(new String[] { k });
          writer.writeNext(triplet[0]);
          writer.writeNext(triplet[1]);
          writer.writeNext(triplet[2]);
        }
      } catch (IOException e) {
        LOG.error(e.getMessage());
      }
    }
  }

  public static Object readObject() {
    if (JAVA_OBJECT_SERIALIZATION) {
      String filename = RESOURCE_DIR + PARSE_CACHE_FILE_NAME;
      Object data = null;
      try (InputStream fis = new BufferedInputStream(new FileInputStream(filename));
           ObjectInputStream in = new ObjectInputStream(fis)) {

        data = in.readObject();
      } catch (IOException ex) {
        LOG.error("Cant find parsing cache file {} due to: {}", filename, ex.getMessage());
      } catch (ClassNotFoundException ex) {
        LOG.error(ex.getMessage());
      }
      return data;
    } else {
      final List<String[]> lines = readCsvLines();
      // Never null: callers dereference this without a check.
      Map<String, String[][]> sentence_parseObject = new HashMap<>();
      if (lines == null) {
        LOG.warn("Cannot find parse cache file {} on the classpath or on disk", PARSE_CACHE_FILE_NAME_CSV);
        return sentence_parseObject;
      }
      for (int i = 0; i < lines.size() - 3; i += 4) {
        String key = lines.get(i)[0];
        String[][] value = new String[][] { lines.get(i + 1), lines.get(i + 2),
            lines.get(i + 3) };
        sentence_parseObject.put(key, value);
      }
      return sentence_parseObject;
    }
  }

  // Classpath copy wins, so the working directory cannot affect the result.
  private static List<String[]> readCsvLines() {
    try (InputStream is = ParserCacheSerializer.class.getResourceAsStream("/" + PARSE_CACHE_FILE_NAME_CSV)) {
      if (is != null) {
        try (CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8), ',')) {
          return reader.readAll();
        }
      }
    } catch (IOException ioe) {
      LOG.error(ioe.getMessage(), ioe);
    }
    // Fall back to an earlier run, then the legacy in-tree location.
    for (Path p : List.of(cacheOutputFile(), Path.of(RESOURCE_DIR + PARSE_CACHE_FILE_NAME_CSV))) {
      if (!Files.isReadable(p)) {
        continue;
      }
      try (CSVReader reader = new CSVReader(Files.newBufferedReader(p, StandardCharsets.UTF_8), ',')) {
        return reader.readAll();
      } catch (IOException ioe) {
        LOG.error(ioe.getMessage(), ioe);
      }
    }
    return null;
  }

  private static Path cacheOutputFile() {
    return Path.of(System.getProperty(CACHE_DIR_PROPERTY, DEFAULT_CACHE_DIR), PARSE_CACHE_FILE_NAME_CSV);
  }
}
