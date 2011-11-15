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

package opennlp.tools.similarity.apps.utils;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.stemmer.PorterStemmer;

public class StringDistanceMeasurer {
  // external tools
  private PorterStemmer ps; // stemmer

  private static final int MIN_STRING_LENGTH_FOR_WORD = 4;

  protected int MIN_STRING_LENGTH_FOR_DISTORTED_WORD = 6;

  protected static final int ACCEPTABLE_DEVIATION_IN_CHAR = 2;

  private static final double MIN_SCORE_FOR_LING = 100; // 0.7;

  public StringDistanceMeasurer() {
    // first get stemmer
    ps = new PorterStemmer();
    if (MIN_SCORE_FOR_LING > 1.0)
      return;

  }

  // gets string array and process numbers, applies stemming and forms a list
  protected List<String> filterWordArray(String[] strWords) {
    List<String> strList = new ArrayList<String>();
    for (String w : strWords) {
      Boolean bInteger = true;
      try {
        Integer.parseInt(w);
      } catch (Exception e) {
        bInteger = false;
      }
      if (w.length() < MIN_STRING_LENGTH_FOR_WORD && !bInteger) // only
                                                                // non-integer
                                                                // short
        // string like preposition is uninteresting
        continue;
      try {
        w = ps.stem(w.toLowerCase()).toString();
      } catch (Exception e) {
        // do nothing, just have original term
      }
      if (w.startsWith("Invalid"))
        continue;
      strList.add(w);
    }
    return strList;
  }

  protected List<String> filterWordArrayNoStem(String[] strWords) {
    List<String> strList = new ArrayList<String>();
    for (String w : strWords) {
      Boolean bInteger = true;
      try {
        Integer.parseInt(w);
      } catch (Exception e) {
        bInteger = false;
      }
      if (w.length() < MIN_STRING_LENGTH_FOR_WORD && !bInteger) // only
                                                                // non-integer
                                                                // short
        // string like preposition is uninteresting
        continue;
      w = w.toLowerCase();

      strList.add(w);
    }
    return strList;
  }

  // main entry point. Gets two strings and applies string match
  // and also linguistic match if score > a threshold
  public double measureStringDistance(String str1, String str2) {
    double result = (double) -1.0;
    try {
      str1 = StringCleaner.processSnapshotForMatching(str1);
      str2 = StringCleaner.processSnapshotForMatching(str2);
      if (str1.equals(str2)) // || str1.endsWith(str2) || str2.endsWith(str1))
                             // bg 03-2011
        return 1.0;

      String[] str1Words = str1.split(" ");
      String[] str2Words = str2.split(" ");
      List<String> str1List = filterWordArray(str1Words), str2List = filterWordArray(str2Words);

      int l1 = str1List.size(), l2 = str2List.size();
      if (l1 < 2)
        l1 = str1Words.length;
      if (l2 < 2)
        l2 = str2Words.length;

      int lOverlap = 0;
      List<String> strListOverlap = new ArrayList<String>(str1List);
      strListOverlap.retainAll(str2List);
      for (String w : strListOverlap) {
        if (w.toLowerCase().equals(w)) // no special interest word
          lOverlap++;
        else
          lOverlap += 2; // if capitalized, or specific word => important so
                         // double score
      }
      result = Math.pow((double) (lOverlap * lOverlap) / (double) l1
          / (double) l2, 0.4);

      // now we try to find similar words which are long or Upper case
      int countSimilar = 0;
      str1List.removeAll(strListOverlap);
      str2List.removeAll(strListOverlap);
      for (String w1 : str1List) {
        for (String w2 : str2List) {
          if (w1.length() > MIN_STRING_LENGTH_FOR_DISTORTED_WORD
              || !w1.toLowerCase().equals(w1))
            if (w2.length() > MIN_STRING_LENGTH_FOR_DISTORTED_WORD
                || !w2.toLowerCase().equals(w2))
              if (LevensteinDistanceFinder.levensteinDistance(w1, w2, 1, 10, 1,
                  10) <= ACCEPTABLE_DEVIATION_IN_CHAR)
                countSimilar++;
        }
      }
      lOverlap += countSimilar;
      result = Math.pow((double) (lOverlap * lOverlap) / (double) l1
          / (double) l2, 0.4);
      if (result > 1)
        result = (double) 1.0;

      // double ld = LevensteinDistanceFinder. levensteinDistance(str1, str2, 1,
      // 10, 1, 10);
      // System.out.println(ld);

    } catch (Exception e) {
      e.printStackTrace();
      return (double) -1.0;
    }

    Double linguisticScore = (double) -1.0;
    // to be developed - employs linguistic processor
    /*
     * if (result>MIN_SCORE_FOR_LING) { List<List<ParseTreeChunk>> matchResult =
     * pos.matchOrigSentencesCache(str1, str2); linguisticScore =
     * ParseTreeChunkListScorer.getParseTreeChunkListScore(matchResult);
     * System.out.println(matchResult);
     * 
     * // magic formula for 0.7 string match and 0.3 linguistic match result =
     * result*0.7 + linguisticScore/6.0* 0.3; }
     */
    return result;
  }

  public double measureStringDistanceNoStemming(String str1, String str2) {
    double result = (double) -1.0;
    try {
      str1 = StringCleaner.processSnapshotForMatching(str1);
      str2 = StringCleaner.processSnapshotForMatching(str2);
      if (str1.equals(str2)) // || str1.endsWith(str2) || str2.endsWith(str1))
                             // bg 03-2011
        return 1.0;

      String[] str1Words = str1.split(" ");
      String[] str2Words = str2.split(" ");
      List<String> str1List = filterWordArrayNoStem(str1Words), str2List = filterWordArrayNoStem(str2Words);

      int l1 = str1List.size(), l2 = str2List.size();
      if (l1 < 2)
        l1 = str1Words.length;
      if (l2 < 2)
        l2 = str2Words.length;

      int lOverlap = 0;
      List<String> strListOverlap = new ArrayList<String>(str1List);
      strListOverlap.retainAll(str2List);
      for (String w : strListOverlap) {
        if (w.toLowerCase().equals(w)) // no special interest word
          lOverlap++;
        else
          lOverlap += 2; // if capitalized, or specific word => important so
                         // double score
      }
      result = Math.pow((double) (lOverlap * lOverlap) / (double) l1
          / (double) l2, 0.4);

      // now we try to find similar words which are long or Upper case
      int countSimilar = 0;
      str1List.removeAll(strListOverlap);
      str2List.removeAll(strListOverlap);
      for (String w1 : str1List) {
        for (String w2 : str2List) {
          if (w1.length() > MIN_STRING_LENGTH_FOR_DISTORTED_WORD
              || !w1.toLowerCase().equals(w1))
            if (w2.length() > MIN_STRING_LENGTH_FOR_DISTORTED_WORD
                || !w2.toLowerCase().equals(w2))
              if (LevensteinDistanceFinder.levensteinDistance(w1, w2, 1, 10, 1,
                  10) <= ACCEPTABLE_DEVIATION_IN_CHAR)
                countSimilar++;
        }
      }
      lOverlap += countSimilar;
      result = Math.pow((double) (lOverlap * lOverlap) / (double) l1
          / (double) l2, 0.4);
      if (result > 1)
        result = (double) 1.0;

      // double ld = LevensteinDistanceFinder. levensteinDistance(str1, str2, 1,
      // 10, 1, 10);
      // System.out.println(ld);

    } catch (Exception e) {
      e.printStackTrace();
      return (double) -1.0;
    }

    Double linguisticScore = (double) -1.0;
    // to be developed - employs linguistic processor
    /*
     * if (result>MIN_SCORE_FOR_LING) { List<List<ParseTreeChunk>> matchResult =
     * pos.matchOrigSentencesCache(str1, str2); linguisticScore =
     * ParseTreeChunkListScorer.getParseTreeChunkListScore(matchResult);
     * System.out.println(matchResult);
     * 
     * // magic formula for 0.7 string match and 0.3 linguistic match result =
     * result*0.7 + linguisticScore/6.0* 0.3; }
     */
    return result;
  }

  public static void main(String[] args) {
    StringDistanceMeasurer meas = new StringDistanceMeasurer();

    // String sent1 =
    // "estoy en LA,California y no encuentro tu album en NINGUNA parte!!! " +
    // "NO MANCHES!!!lo tengo que comprar por internet!! " ;

    // redunction of announcement
    String sent2a = "Tomarow come check us out if your in the area show starts at 6:00pm "
        + "2404 E. La Palma Anaheim, California 92806 Cost:$3";
    String sent2b = "Tomorrow you can check us if you area will show start at 6 pm "
        + "2404 East La Palma Anaheim, $3";
    // common sub-chunk = [VBZ-starts IN-at NNP-* NNP-* ]

    // original posting and its yahoo.com search snapshot
    String sent4a = "Fliers may have to wait years for new liquid screening equipment";
    String sent4b = "for screening checkpoints and equipment; improving ... "
        + "Wait times are not just a problem at large airports";

    // slang and search snapshot
    String sent5a = "hell yea i stay in california. and hell no lol LA sucks hella bad, "
        + "i lived there for a while and hated it sooo much, so boring! ";

    String sent5b = "My life is so boring without Tree Hill and the OC. America is sooo "
        + "racist I LOVE YOU SO MUCH. TO everyone that has hurt me...no one in the ..... Yeah sucks I know";

    String sent6a = "I think its gonna be in the east coast as well. California is pretty but way "
        + "to close to LA and helicopters are gonna ruin it";
    String sent6b = "could be in east coast as well. California is pretty but way "
        + "to close to LA and choppers will ruin it";
    // common sub-chunk = [JJ-east NN-coast ]

    String sent7a = "Iran nuke document called 'alarming'. Their Program started in the 50s with our help!";
    String sent7b = "nuke project of Iran is alarming' Program started in 1950s with our help";
    // common sub-chunk = [VBD-started IN-in NNS-50s IN-with PRP$-our NN-help ]

    // News title for the same event
    String sent8a = "Pakistan slaps travel ban on defence minister";
    String sent8b = "Pakistan corruption fall-out threatens stability";
    String sent8c = "Pakistan defence minister 'barred from leaving country'";
    String sent8d = "Pakistani defence minister banned from travel";
    String sent8dd = "Pakistani defence minister banned from travel"; // to
                                                                      // check
                                                                      // the
                                                                      // case of
                                                                      // 1.0

    // common sub-chunk = [NN-defence NN-minister ]

    List<Double> matchRes = new ArrayList<Double>();
    matchRes.add(meas.measureStringDistance(sent2a, sent2b));
    matchRes.add(meas.measureStringDistance(sent4a, sent4b));
    matchRes.add(meas.measureStringDistance(sent5a, sent5b));
    matchRes.add(meas.measureStringDistance(sent6a, sent6b));
    matchRes.add(meas.measureStringDistance(sent7a, sent7b));

    System.out.println(matchRes);
    // [0.8178702752867737, 0.21082473737065027, 0.27594593229224296,
    // 0.7517586466500455, 0.9100766715907641]

    matchRes = new ArrayList<Double>();
    matchRes.add(meas.measureStringDistance(sent8a, sent8b));
    matchRes.add(meas.measureStringDistance(sent8a, sent8c));
    matchRes.add(meas.measureStringDistance(sent8a, sent8d));
    matchRes.add(meas.measureStringDistance(sent8b, sent8c));
    matchRes.add(meas.measureStringDistance(sent8b, sent8d));
    matchRes.add(meas.measureStringDistance(sent8c, sent8d));

    System.out.println(matchRes);
    // [0.48044977359257246, 0.8365116420730185, 0.8365116420730185,
    // 0.48044977359257246, 0.27594593229224296,
    // 0.6391010941257969]

    matchRes = new ArrayList<Double>();
    // to verify that the same sentence gives 1
    matchRes.add(meas.measureStringDistance(sent8dd, sent8d));
    // to verify that totally different sentences give 0
    matchRes.add(meas.measureStringDistance(sent2a, sent8d));

    System.out.println("Now testing 1 and 0: \n" + matchRes);
    // Now testing 1 and 0:
    // [1.0, 0.0]
  }
}
