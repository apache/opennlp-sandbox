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

package opennlp.tools.disambiguator.datareader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ClustersReader {

  public static String path = "src\\test\\resources\\phraseclusters\\";
  private static HashMap<String, ArrayList<ClusterMembership>> map = new HashMap<String, ArrayList<ClusterMembership>>();

  public void readFile(String url) {

    File file = new File(url);

    try (BufferedReader clusterList = new BufferedReader(new FileReader(file))) {

      String line;

      // Read the file
      while ((line = clusterList.readLine()) != null) {

        String[] parts = line.split("\\t");
        String phraseKey = parts[0];
        String[] phraseWords = phraseKey.split("\\s");

        System.out.println(phraseKey);

        ArrayList<ClusterMembership> memberships = new ArrayList<ClusterMembership>();

        for (int i = 1; i < parts.length; i += 2) {
          ClusterMembership membership = new ClusterMembership(
              Integer.parseInt(parts[i]), Double.parseDouble(parts[i + 1]));
          membership.phrase = phraseKey;
          membership.phraseWords = phraseWords;

          memberships.add(membership);
        }
        map.put(phraseKey, memberships);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public boolean getNgramClusters(String word) {

    File folder = new File(path);
    if (folder.isDirectory()) {
      for (File file : folder.listFiles()) {
        readFile(file.getAbsolutePath());
      }

    } else {
      return false;
    }

    return true;

  }

}
