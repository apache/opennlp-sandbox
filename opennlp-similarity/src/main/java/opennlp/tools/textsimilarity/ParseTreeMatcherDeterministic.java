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
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ParseTreeMatcherDeterministic {

  private GeneralizationListReducer generalizationListReducer = new GeneralizationListReducer();

  private LemmaFormManager lemmaFormManager = new LemmaFormManager();

  private POSManager posManager = new POSManager();

  public List<ParseTreeChunk> generalizeTwoGroupedPhrasesDeterministic(
      ParseTreeChunk chunk1, ParseTreeChunk chunk2) {
    List<String> pos1 = chunk1.getPOSs();
    List<String> pos2 = chunk2.getPOSs();
    List<String> lem1 = chunk1.getLemmas();
    List<String> lem2 = chunk2.getLemmas();

    List<String> lem1stem = new ArrayList<String>();
    List<String> lem2stem = new ArrayList<String>();

    PorterStemmer ps = new PorterStemmer();
    for (String word : lem1) {
      try {
        lem1stem.add(ps.stem(word.toLowerCase()));
      } catch (Exception e) {
        // e.printStackTrace();

        if (word.length() > 2)
          System.err.println("Unable to stem: " + word);
      }
    }
    try {
      for (String word : lem2) {
        lem2stem.add(ps.stem(word.toLowerCase()));
      }
    } catch (Exception e) {
      System.err.println("problem processing word " + lem2.toString());
    }

    List<String> overlap = new ArrayList(lem1stem);
    overlap.retainAll(lem2stem);

    if (overlap == null || overlap.size() < 1)
      return null;

    List<Integer> occur1 = new ArrayList<Integer>(), occur2 = new ArrayList<Integer>();
    for (String word : overlap) {
      Integer i1 = lem1stem.indexOf(word);
      Integer i2 = lem2stem.indexOf(word);
      occur1.add(i1);
      occur2.add(i2);
    }

    // now we search for plausible sublists of overlaps
    // if at some position correspondence is inverse (one of two position
    // decreases instead of increases)
    // then we terminate current alignment accum and start a new one
    List<List<int[]>> overlapsPlaus = new ArrayList<List<int[]>>();
    // starts from 1, not 0
    List<int[]> accum = new ArrayList<int[]>();
    accum.add(new int[] { occur1.get(0), occur2.get(0) });
    for (int i = 1; i < occur1.size(); i++) {

      if (occur1.get(i) > occur1.get(i - 1)
          && occur2.get(i) > occur2.get(i - 1))
        accum.add(new int[] { occur1.get(i), occur2.get(i) });
      else {
        overlapsPlaus.add(accum);
        accum = new ArrayList<int[]>();
        accum.add(new int[] { occur1.get(i), occur2.get(i) });
      }
    }
    if (accum.size() > 0) {
      overlapsPlaus.add(accum);
    }

    List<ParseTreeChunk> results = new ArrayList<ParseTreeChunk>();
    for (List<int[]> occur : overlapsPlaus) {
      List<Integer> occr1 = new ArrayList<Integer>(), occr2 = new ArrayList<Integer>();
      for (int[] column : occur) {
        occr1.add(column[0]);
        occr2.add(column[1]);
      }

      int ov1 = 0, ov2 = 0; // iterators over common words;
      List<String> commonPOS = new ArrayList<String>(), commonLemmas = new ArrayList<String>();
      // we start two words before first word
      int k1 = occr1.get(ov1) - 2, k2 = occr2.get(ov2) - 2;
      // if (k1<0) k1=0; if (k2<0) k2=0;
      Boolean bReachedCommonWord = false;
      while (k1 < 0 || k2 < 0) {
        k1++;
        k2++;
      }
      int k1max = pos1.size() - 1, k2max = pos2.size() - 1;
      while (k1 <= k1max && k2 <= k2max) {
        // first check if the same POS
        String sim = posManager.similarPOS(pos1.get(k1), pos2.get(k2));
        String lemmaMatch = lemmaFormManager.matchLemmas(ps, lem1.get(k1),
            lem2.get(k2), sim);
        if ((sim != null)
            && (lemmaMatch == null || (lemmaMatch != null && !lemmaMatch
                .equals("fail")))) {
          commonPOS.add(pos1.get(k1));
          if (lemmaMatch != null) {
            commonLemmas.add(lemmaMatch);
            // System.out.println("Added "+lemmaMatch);
            if (k1 == occr1.get(ov1) && k2 == occr2.get(ov2))
              bReachedCommonWord = true; // now we can have different increment
                                         // opera
            else {
              if (occr1.size() > ov1 + 1 && occr2.size() > ov2 + 1
                  && k1 == occr1.get(ov1 + 1) && k2 == occr2.get(ov2 + 1)) {
                ov1++;
                ov2++;
                bReachedCommonWord = true;
              }
              // else
              // System.err.println("Next match reached '"+lemmaMatch+
              // "' | k1 - k2: "+k1 + " "+k2 +
              // "| occur index ov1-ov2 "+
              // ov1+" "+ov2+
              // "| identified positions of match: occr1.get(ov1) - occr2.get(ov1) "
              // +
              // occr1.get(ov1) + " "+ occr2.get(ov1));
            }
          } else {
            commonLemmas.add("*");
          } // the same parts of speech, proceed to the next word in both
            // expressions
          k1++;
          k2++;

        } else if (!bReachedCommonWord) {
          k1++;
          k2++;
        } // still searching
        else {
          // different parts of speech, jump to the next identified common word
          ov1++;
          ov2++;
          if (ov1 > occr1.size() - 1 || ov2 > occr2.size() - 1)
            break;
          // now trying to find
          int kk1 = occr1.get(ov1) - 2, // new positions of iterators
          kk2 = occr2.get(ov2) - 2;
          int countMove = 0;
          while ((kk1 < k1 + 1 || kk2 < k2 + 1) && countMove < 2) { // if it is
                                                                    // behind
                                                                    // current
                                                                    // position,
                                                                    // synchroneously
                                                                    // move
                                                                    // towards
                                                                    // right
            kk1++;
            kk2++;
            countMove++;
          }
          k1 = kk1;
          k2 = kk2;

          if (k1 > k1max)
            k1 = k1max;
          if (k2 > k2max)
            k2 = k2max;
          bReachedCommonWord = false;
        }
      }
      ParseTreeChunk currResult = new ParseTreeChunk(commonLemmas, commonPOS,
          0, 0);
      results.add(currResult);
    }

    return results;
  }

  // main function to generalize two expressions grouped by phrase types
  // returns a list of generalizations for each phrase type with filtered
  // sub-expressions
  public List<List<ParseTreeChunk>> matchTwoSentencesGroupedChunksDeterministic(
      List<List<ParseTreeChunk>> sent1, List<List<ParseTreeChunk>> sent2) {
    List<List<ParseTreeChunk>> results = new ArrayList<List<ParseTreeChunk>>();
    // first irerate through component
    for (int comp = 0; comp < 2 && // just np & vp
        comp < sent1.size() && comp < sent2.size(); comp++) {
      List<ParseTreeChunk> resultComps = new ArrayList<ParseTreeChunk>();
      // then iterate through each phrase in each component
      for (ParseTreeChunk ch1 : sent1.get(comp)) {
        for (ParseTreeChunk ch2 : sent2.get(comp)) { // simpler version
          List<ParseTreeChunk> chunkToAdd = generalizeTwoGroupedPhrasesDeterministic(
              ch1, ch2);

          if (chunkToAdd == null)
            chunkToAdd = new ArrayList<ParseTreeChunk>();
          // System.out.println("ch1 = "+
          // ch1.toString()+" | ch2="+ch2.toString()
          // +"\n result = "+chunkToAdd.toString() + "\n");
          /*
           * List<ParseTreeChunk> chunkToAdd1 =
           * ParseTreeMatcherDeterministic.generalizeTwoGroupedPhrasesDeterministic
           * ( ParseTreeMatcher.prepositionalNNSTransform(ch1), ch2); if
           * (chunkToAdd1!=null) chunkToAdd.addAll(chunkToAdd1);
           * List<ParseTreeChunk> chunkToAdd2 =
           * ParseTreeMatcherDeterministic.generalizeTwoGroupedPhrasesDeterministic
           * ( ParseTreeMatcher.prepositionalNNSTransform(ch2), ch1); if
           * (chunkToAdd2!=null) chunkToAdd.addAll(chunkToAdd2);
           */

          // For generalized match not with orig sentences but with templates
          // if (!LemmaFormManager.mustOccurVerifier(ch1, ch2, chunkToAdd))
          // continue; // if the words which have to stay do not stay, proceed
          // to other elements
          Boolean alreadyThere = false;
          for (ParseTreeChunk chunk : resultComps) {
            if (chunkToAdd.contains(chunk)) {
              alreadyThere = true;
              break;
            }

            // }
          }

          if (!alreadyThere && chunkToAdd != null && chunkToAdd.size() > 0) {
            resultComps.addAll(chunkToAdd);
          }

        }
      }
      List<ParseTreeChunk> resultCompsRed = generalizationListReducer
          .applyFilteringBySubsumption(resultComps);

      resultComps = resultCompsRed;
      results.add(resultComps);
    }

    return results;
  }

}
