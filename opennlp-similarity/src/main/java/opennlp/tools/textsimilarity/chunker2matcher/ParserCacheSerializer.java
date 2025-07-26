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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
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

      final Path p = Path.of(RESOURCE_DIR + PARSE_CACHE_FILE_NAME_CSV);
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
      List<String[]> lines;
      final String fileName = RESOURCE_DIR + PARSE_CACHE_FILE_NAME_CSV;
      
      try (CSVReader reader = new CSVReader(new FileReader(fileName), ',')) {
        lines = reader.readAll();
      } catch (FileNotFoundException e) {
        if (JAVA_OBJECT_SERIALIZATION)
          LOG.warn("Cannot find cache file");
        return null;
      } catch (IOException ioe) {
        LOG.error(ioe.getMessage(), ioe);
        return null;
      }
      Map<String, String[][]> sentence_parseObject = new HashMap<>();
      for (int i = 0; i < lines.size() - 3; i += 4) {
        String key = lines.get(i)[0];
        String[][] value = new String[][] { lines.get(i + 1), lines.get(i + 2),
            lines.get(i + 3) };
        sentence_parseObject.put(key, value);
      }
      return sentence_parseObject;
    }
  }
}
