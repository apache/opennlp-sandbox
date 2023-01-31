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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ParseTreeMatcher {

  private static final int NUMBER_OF_ITERATIONS = 2;

  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
  private POSManager posManager = new POSManager();
  private LemmaFormManager lemmaFormManager = new LemmaFormManager();

  public ParseTreeMatcher() {

  }

  public ParseTreeChunk generalizeTwoGroupedPhrasesOLD(ParseTreeChunk chunk1,
      ParseTreeChunk chunk2) {
    List<String> pos1 = chunk1.getPOSs();
    List<String> pos2 = chunk1.getPOSs();

    List<String> commonPOS = new ArrayList<String>(), commonLemmas = new ArrayList<String>();
    int k1 = 0, k2 = 0;
    Boolean incrFirst = true;
    while (k1 < pos1.size() && k2 < pos2.size()) {
      // first check if the same POS
      String sim = posManager.similarPOS(pos1.get(k1), pos2.get(k2));
      if (sim != null) {
        commonPOS.add(pos1.get(k1));
        if (chunk1.getLemmas().size() > k1 && chunk2.getLemmas().size() > k2
            && chunk1.getLemmas().get(k1).equals(chunk2.getLemmas().get(k2))) {
          commonLemmas.add(chunk1.getLemmas().get(k1));
        } else {
          commonLemmas.add("*");
        }
        k1++;
        k2++;
      } else if (incrFirst) {
        k1++;
      } else {
        k2++;
      }
      incrFirst = !incrFirst;
    }

    ParseTreeChunk res = new ParseTreeChunk(commonLemmas, commonPOS, 0, 0);
    // if (parseTreeChunkListScorer.getScore(res)> 0.6)
    // System.out.println(chunk1 + "  + \n"+ chunk2 + " = \n" + res);
    return res;
  }

  // A for B => B have A
  // transforms expr { A B C prep X Y }
  // into {A B {X Y} C}
  // should only be applied to a noun phrase
  public ParseTreeChunk prepositionalNNSTransform(ParseTreeChunk ch) {
    List<String> transfPOS = new ArrayList<String>(), transfLemmas = new ArrayList<String>();
    if (!ch.getPOSs().contains("IN"))
      return ch;
    int indexIN = ch.getPOSs().lastIndexOf("IN");

    if (indexIN < 2)// preposition is a first word - should not be in a noun
                    // phrase
      return ch;
    String Word_IN = ch.getLemmas().get(indexIN);
    if (!(Word_IN.equals("to") || Word_IN.equals("on") || Word_IN.equals("in")
        || Word_IN.equals("of") || Word_IN.equals("with")
        || Word_IN.equals("by") || Word_IN.equals("from")))
      return ch;

    List<String> toShiftAfterPartPOS = ch.getPOSs().subList(indexIN + 1,
        ch.getPOSs().size());
    List<String> toShiftAfterPartLemmas = ch.getLemmas().subList(indexIN + 1,
        ch.getLemmas().size());

    if (indexIN - 1 > 0)
      transfPOS.addAll(ch.getPOSs().subList(0, indexIN - 1));
    transfPOS.addAll(toShiftAfterPartPOS);
    transfPOS.add(ch.getPOSs().get(indexIN - 1));

    if (indexIN - 1 > 0)
      transfLemmas.addAll(ch.getLemmas().subList(0, indexIN - 1));
    transfLemmas.addAll(toShiftAfterPartLemmas);
    transfLemmas.add(ch.getLemmas().get(indexIN - 1));

    return new ParseTreeChunk(transfLemmas, transfPOS, 0, 0);
  }

  public ParseTreeChunk generalizeTwoGroupedPhrasesRandomSelectHighestScoreWithTransforms(
      ParseTreeChunk chunk1, ParseTreeChunk chunk2) {
    ParseTreeChunk chRes1 = generalizeTwoGroupedPhrasesRandomSelectHighestScore(
        chunk1, chunk2);
    ParseTreeChunk chRes2 = generalizeTwoGroupedPhrasesRandomSelectHighestScore(
        prepositionalNNSTransform(chunk1), chunk2);
    ParseTreeChunk chRes3 = generalizeTwoGroupedPhrasesRandomSelectHighestScore(
        prepositionalNNSTransform(chunk2), chunk1);

    ParseTreeChunk chRes = null;
    if (parseTreeChunkListScorer.getScore(chRes1) > parseTreeChunkListScorer
        .getScore(chRes2))
      if (parseTreeChunkListScorer.getScore(chRes1) > parseTreeChunkListScorer
          .getScore(chRes3))
        chRes = chRes1;
      else
        chRes = chRes3;
    else if (parseTreeChunkListScorer.getScore(chRes2) > parseTreeChunkListScorer
        .getScore(chRes3))
      chRes = chRes2;
    else
      chRes = chRes3;

    return chRes;
  }

  public ParseTreeChunk generalizeTwoGroupedPhrasesRandomSelectHighestScore(
      ParseTreeChunk chunk1, ParseTreeChunk chunk2) {
    List<String> pos1 = chunk1.getPOSs();
    List<String> pos2 = chunk2.getPOSs();
    // Map <ParseTreeChunk, Double> scoredResults = new HashMap <ParseTreeChunk,
    // Double> ();
    int timesRepetitiveRun = NUMBER_OF_ITERATIONS;

    Double globalScore = -1.0;
    ParseTreeChunk result = null;

    for (int timesRun = 0; timesRun < timesRepetitiveRun; timesRun++) {
      List<String> commonPOS = new ArrayList<String>(), commonLemmas = new ArrayList<String>();
      int k1 = 0, k2 = 0;
      Double score = 0.0;
      while (k1 < pos1.size() && k2 < pos2.size()) {
        // first check if the same POS
        String sim = posManager.similarPOS(pos1.get(k1), pos2.get(k2));
        String lemmaMatch = lemmaFormManager.matchLemmas(null, chunk1
            .getLemmas().get(k1), chunk2.getLemmas().get(k2), sim);
	Random rand = new Random();
        // if (LemmaFormManager.acceptableLemmaAndPOS(sim, lemmaMatch)){
        if ((sim != null)
            && (lemmaMatch == null || (lemmaMatch != null && !lemmaMatch
                .equals("fail")))) {
          // if (sim!=null){ // && (lemmaMatch!=null &&
          // !lemmaMatch.equals("fail"))){
          commonPOS.add(pos1.get(k1));
          if (chunk1.getLemmas().size() > k1 && chunk2.getLemmas().size() > k2
              && lemmaMatch != null) {
            commonLemmas.add(lemmaMatch);

          } else {
            commonLemmas.add("*");

          }
          k1++;
          k2++;
        } else if (rand.nextDouble() > 0.5) {
          k1++;
        } else {
          k2++;
        }

      }
      ParseTreeChunk currResult = new ParseTreeChunk(commonLemmas, commonPOS,
          0, 0);
      score = parseTreeChunkListScorer.getScore(currResult);
      if (score > globalScore) {
        // System.out.println(chunk1 + "  + \n"+ chunk2 + " = \n" +
        // result+" score = "+ score +"\n\n");
        result = currResult;
        globalScore = score;
      }
    }

    for (int timesRun = 0; timesRun < timesRepetitiveRun; timesRun++) {
      List<String> commonPOS = new ArrayList<String>(), commonLemmas = new ArrayList<String>();
      int k1 = pos1.size() - 1, k2 = pos2.size() - 1;
      Double score = 0.0;
      while (k1 >= 0 && k2 >= 0) {
        // first check if the same POS
        String sim = posManager.similarPOS(pos1.get(k1), pos2.get(k2));
        String lemmaMatch = lemmaFormManager.matchLemmas(null, chunk1
            .getLemmas().get(k1), chunk2.getLemmas().get(k2), sim);
        // if (acceptableLemmaAndPOS(sim, lemmaMatch)){
        if ((sim != null)
            && (lemmaMatch == null || (lemmaMatch != null && !lemmaMatch
                .equals("fail")))) {
          commonPOS.add(pos1.get(k1));
          if (chunk1.getLemmas().size() > k1 && chunk2.getLemmas().size() > k2
              && lemmaMatch != null) {
            commonLemmas.add(lemmaMatch);
          } else {
            commonLemmas.add("*");

          }
          k1--;
          k2--;
        } else if (Math.random() > 0.5) {
          k1--;
        } else {
          k2--;
        }

      }
      Collections.reverse(commonLemmas);
      Collections.reverse(commonPOS);

      ParseTreeChunk currResult = new ParseTreeChunk(commonLemmas, commonPOS,
          0, 0);
      score = parseTreeChunkListScorer.getScore(currResult);
      if (score > globalScore) {
        // System.out.println(chunk1 + "  + \n"+ chunk2 + " = \n" +
        // currResult+" score = "+ score +"\n\n");
        result = currResult;
        globalScore = score;
      }
    }

    // // System.out.println(chunk1 + "  + \n"+ chunk2 + " = \n" + result
    // +" score = " +
    // // parseTreeChunkListScorer.getScore(result)+"\n\n");
    return result;
  }

  public Boolean acceptableLemmaAndPOS(String sim, String lemmaMatch) {
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
}
