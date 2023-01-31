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
 * 
 */
public class AriAdapter {
  // income_taks(state,company(cafeteria,_)):-do(71100).
  Map<String, List<List<String>>> lemma_AssocWords = new HashMap<String, List<List<String>>>();

  public void getChainsFromARIfile(String fileName) {

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(
          new FileInputStream(fileName)));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.length() < 10 || line.startsWith("%") || line.startsWith(":"))
          continue;
        String chain0 = line.replace("_,", "&").replace("_)", "&")
            .replace(":-do(", "&").replace(":-var", "&").replace("taks", "tax")
            .replace(":- do(", "&").replace("X=", "&").replace(":-", "&")
            .replace("[X|_]", "&").replace("nonvar", "&").replace("var", "&")
            .replace('(', '&').replace(')', '&').replace(',', '&')
            .replace('.', '&').replace("&&&", "&").replace("&&", "&")
            .replace("&", " ");
        String[] chains = chain0.split(" ");
        List<String> chainList = new ArrayList<String>(); // Arrays.asList(chains);
        for (String word : chains) {
          if (word != null && word.length() > 2 && word.indexOf("0") < 0
              && word.indexOf("1") < 0 && word.indexOf("2") < 0
              && word.indexOf("3") < 0 && word.indexOf("4") < 0
              && word.indexOf("5") < 0)
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
          List<List<String>> resList = new ArrayList<List<String>>();
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

  public static void main(String[] args) {

    AriAdapter ad = new AriAdapter();
    ad.getChainsFromARIfile("src/test/resources/taxonomies/irs_dom.ari");
    System.out.println(ad.lemma_AssocWords);

  }

}
