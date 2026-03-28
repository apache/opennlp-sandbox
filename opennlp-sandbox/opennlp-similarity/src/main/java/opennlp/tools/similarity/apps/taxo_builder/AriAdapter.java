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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class makes it possible to use old prolog-files as the bases for
 * taxonomy-learner. It cleans the prolog files and returns with Strings which
 * can be used for the taxonomy extender process.
 */
public class AriAdapter {

  private static final Character AMPERSAND_CHAR = '&';
  private static final String AMPERSAND = "&";
  private static final String PERCENT = "%";
  private static final String COLON = ":";

  // income_taks(state,company(cafeteria,_)):-do(71100).
  final Map<String, List<List<String>>> lemma_AssocWords = new HashMap<>();

  public void getChainsFromARIfile(String fileName) {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(cl.getResourceAsStream(fileName)))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.length() < 10 || line.startsWith(PERCENT) || line.startsWith(COLON))
          continue;
        String chain0 = line.replace("_,", AMPERSAND).replace("_)", AMPERSAND)
            .replace(":-do(", AMPERSAND).replace(":-var", AMPERSAND).replace("taks", "tax")
            .replace(":- do(", AMPERSAND).replace("X=", AMPERSAND).replace(":-", AMPERSAND)
            .replace("[X|_]", AMPERSAND).replace("nonvar", AMPERSAND).replace("var", AMPERSAND)
            .replace('(', AMPERSAND_CHAR).replace(')', AMPERSAND_CHAR).replace(',', AMPERSAND_CHAR)
            .replace('.', AMPERSAND_CHAR).replace("&&&", AMPERSAND).replace("&&", AMPERSAND)
            .replace(AMPERSAND, " ");
        String[] chains = chain0.split(" ");
        List<String> chainList = new ArrayList<>(); // Arrays.asList(chains);
        for (String word : chains) {
          if (word != null && word.length() > 2 && !word.contains("0")
              && !word.contains("1") && !word.contains("2")
              && !word.contains("3") && !word.contains("4")
              && !word.contains("5"))
            chainList.add(word);
        }
        if (chains.length < 1 || chainList.size() < 1
            || chainList.get(0).length() < 3)
          continue;
        String entry = chainList.get(0);
        if (entry.length() < 3)
          continue;
        chainList.remove(entry);
        List<List<String>> res = lemma_AssocWords.get(entry);
        if (res == null) {
          List<List<String>> resList = new ArrayList<>();
          resList.add(chainList);
          lemma_AssocWords.put(entry, resList);
        } else {
          res.add(chainList);
          lemma_AssocWords.put(entry, res);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();

    }
  }

}
