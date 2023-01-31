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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ParserCacheSerializer {
  private static Logger LOG = Logger
      .getLogger("opennlp.tools.textsimilarity.chunker2matcher.ParserCacheSerializer");
  private static boolean javaObjectSerialization = false;
  private static String RESOURCE_DIR = "src/test/resources/";
  public static String parseCacheFileName = "sentence_parseObject.dat";
  public static String parseCacheFileNameCSV = "sentence_parseObject.csv";

  public static void writeObject(Object objectToSerialize) {
    if (javaObjectSerialization) {
      String filename = RESOURCE_DIR + parseCacheFileName;
      FileOutputStream fos = null;
      ObjectOutputStream out = null;
      try {
        fos = new FileOutputStream(filename);
        out = new ObjectOutputStream(fos);
        out.writeObject(objectToSerialize);
        out.close();
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    } else {

      Map<String, String[][]> sentence_parseObject = (Map<String, String[][]>) objectToSerialize;
      List<String> keys = new ArrayList<String>(sentence_parseObject.keySet());
      try {
        CSVWriter writer = new CSVWriter(new FileWriter(RESOURCE_DIR
            + parseCacheFileNameCSV, false));
        for (String k : keys) {
          String[][] triplet = sentence_parseObject.get(k);
          writer.writeNext(new String[] { k });
          writer.writeNext(triplet[0]);
          writer.writeNext(triplet[1]);
          writer.writeNext(triplet[2]);

        }
        writer.close();
      } catch (IOException e) {
        LOG.severe(e.getMessage());
      }
    }

  }

  public static Object readObject() {
    if (javaObjectSerialization) {
      String filename = RESOURCE_DIR + parseCacheFileName;
      Object data = null;
      FileInputStream fis = null;
      ObjectInputStream in = null;
      try {
        fis = new FileInputStream(filename);
        in = new ObjectInputStream(fis);
        data = (Object) in.readObject();
        in.close();
      } catch (IOException ex) {
        System.out.println("Cant find parsing cache file ");
      } catch (ClassNotFoundException ex) {
        ex.printStackTrace();
      }
      return data;
    } else {
      CSVReader reader = null;
      List<String[]> lines = null;

      try {
        reader = new CSVReader(new FileReader(RESOURCE_DIR
            + parseCacheFileNameCSV), ',');
        lines = reader.readAll();
      } catch (FileNotFoundException e) {
    	  if (javaObjectSerialization)
    		  System.err.println("Cannot find cache file");
        return null;
      } catch (IOException ioe) {
        ioe.printStackTrace();
        return null;
      }
      Map<String, String[][]> sentence_parseObject = new HashMap<String, String[][]>();
      int count = 0;
      for (int i = 0; i < lines.size() - 3; i += 4) {
        String key = lines.get(i)[0];
        String[][] value = new String[][] { lines.get(i + 1), lines.get(i + 2),
            lines.get(i + 3) };
        sentence_parseObject.put(key, value);
      }

      return sentence_parseObject;
    }

  }

  public class ParserObjectSer {

  }

}
