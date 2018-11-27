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

package opennlp.tools.textsimilarity;

import java.util.List;

import opennlp.tools.stemmer.PStemmer;

public class LemmaFormManager {

  public String matchLemmas(PStemmer ps, String lemma1, String lemma2,
      String POS) {
    if (POS == null) {
      return null;
    }
    lemma1 = lemma1.toLowerCase();
    lemma2 = lemma2.toLowerCase();
    // numbers have to be exact
    if (POS.equals("CD")) {
      if (lemma1.equals(lemma2)) {
        return lemma1;
      } else {
        return null;
      }
    }

    // 'must' occurrence of word - if not equal then 'fail'
    if (lemma1.endsWith("_xyz") || lemma2.endsWith("_xyz")) {
      lemma1 = lemma1.replace("_xyz", "");
      lemma2 = lemma2.replace("_xyz", "");
      if (lemma1.equals(lemma2)) {
        return lemma1;
      } else { // trying to check if nouns and different plural/single form
        if (POS.equals("NN") || POS.equals("NP")) {
          if ((lemma1.equals(lemma2 + "s") || lemma2.equals(lemma1 + "s"))
              || lemma1.endsWith(lemma2) || lemma2.endsWith(lemma1)
              || lemma1.startsWith(lemma2) || lemma2.startsWith(lemma1))
            return lemma1;
        }
        return "fail";
      }
    }

    if (lemma1.equals(lemma2)) {
      return lemma1;
    }

    if (POS.equals("NN") || POS.equals("NP")) {
      if ((lemma1.equals(lemma2 + "s") || lemma2.equals(lemma1 + "s"))
          || lemma1.endsWith(lemma2) || lemma2.endsWith(lemma1)
          || lemma1.startsWith(lemma2) || lemma2.startsWith(lemma1)) {
        return lemma1;
      }
    }
    try {
      if (ps != null) {
        if (ps.stem(lemma1).toString()
            .equalsIgnoreCase(ps.stem(lemma2).toString())) {
          return lemma1;
        }
      }
    } catch (Exception e) {
      System.err.println("Problem processing " + lemma1 + " " + lemma2);
      return null;
    }

    return null;
  }

  public boolean acceptableLemmaAndPOS(String sim, String lemmaMatch) {
    if (sim == null) {
      return false;
    }

    if (lemmaMatch != null && !lemmaMatch.equals("fail")) {
      return false;
    }
    // even if lemmaMatch==null
    return true;
    // if (sim!=null && (lemmaMatch!=null && !lemmaMatch.equals("fail"))){

  }

  // all lemmas ending with # in ch1 and/or ch2 SHOULD occur in chunkToAdd
  public boolean mustOccurVerifier(ParseTreeChunk ch1, ParseTreeChunk ch2,
      ParseTreeChunk chunkToAdd) {
    List<String> lemmasWithMustOccur = ch1.getLemmas();
    lemmasWithMustOccur.addAll(ch2.getLemmas());
    List<String> res = chunkToAdd.getLemmas();
    for (String lem : lemmasWithMustOccur) {
      if (lem.endsWith("_xyz")) {
        String pureLem = lem.replace("_xyz", "");
        if (!res.contains(pureLem)) { // should occur but does not
          return false;
        }// failed the test
      }
    }
    return true;
  }

}
