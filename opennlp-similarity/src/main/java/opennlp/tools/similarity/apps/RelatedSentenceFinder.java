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

package opennlp.tools.similarity.apps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.parser.Parse;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.LemmaPair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.SyntMatcher;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

public class RelatedSentenceFinder {

  // TODO outsource the timeout value
  PageFetcher pFetcher = new PageFetcher();

  private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
  private ParseTreeChunk parseTreeChunk = new ParseTreeChunk();

  private static final org.slf4j.Logger LOG = LoggerFactory
      .getLogger(RelatedSentenceFinder.class);

  static StringDistanceMeasurer STRING_DISTANCE_MEASURER = new StringDistanceMeasurer();

  // used to indicate that a sentence is an opinion, so more appropriate
  static List<String> MENTAL_VERBS = new ArrayList<String>(
      Arrays.asList(new String[] { "want", "know", "believe", "appeal", "ask",
          "accept", "agree", "allow", "appeal", "ask", "assume", "believe",
          "check", "confirm", "convince", "deny", "disagree", "explain",
          "ignore", "inform", "remind", "request", "suggest", "suppose",
          "think", "threaten", "try", "understand" }));

  private static final int MAX_FRAGMENT_SENTS = 10;

  public RelatedSentenceFinder() {

  }

  public List<HitBase> findRelatedOpinionsForSentenceFastAndDummy(String word,
      List<String> sents) throws Exception {
    YahooQueryRunner yrunner = new YahooQueryRunner();
    List<HitBase> searchResult = yrunner.runSearch(word);
    return searchResult;
  }

  public List<HitBase> findRelatedOpinionsForSentence(String sentence,
      List<String> sents) throws Exception {
    List<HitBase> opinionSentencesToAdd = new ArrayList<HitBase>();
    System.out.println(" \n\n=== Sentence  = " + sentence);
    List<String> nounPhraseQueries = buildSearchEngineQueryFromSentence(sentence);

    YahooQueryRunner yrunner = new YahooQueryRunner();
    for (String query : nounPhraseQueries) {
      System.out.println("\nquery = " + query);
      // query += " "+join(MENTAL_VERBS, " OR ") ;
      List<HitBase> searchResult = yrunner.runSearch(query);
      if (searchResult != null) {
        for (HitBase item : searchResult) { // got some text from .html
          if (item.getAbstractText() != null
              && !(item.getUrl().indexOf(".pdf") > 0)) { // exclude
                                                         // pdf
            opinionSentencesToAdd
                .add(augmentWithMinedSentencesAndVerifyRelevance(item,
                    sentence, sents));
          }
        }
      }
    }

    opinionSentencesToAdd = removeDuplicatesFromResultantHits(opinionSentencesToAdd);
    return opinionSentencesToAdd;
  }

  public List<HitBase> findActivityDetailsForEventGroupName(String sentence,
      List<String> sents) throws Exception {
    List<HitBase> opinionSentencesToAdd = new ArrayList<HitBase>();
    System.out.println(" \n\n=== Entity to write about = " + sentence);
    List<String> nounPhraseQueries = new ArrayList<String>();
    String[] frequentPerformingVerbs = {
        " release announce celebrate discover", "introduce enjoy follow",
        "open present show", "meet enjoy follow create",
        "discover continue produce" };

    nounPhraseQueries.add(sentence + frequentPerformingVerbs);

    YahooQueryRunner yrunner = new YahooQueryRunner();
    for (String verbAddition : frequentPerformingVerbs) {
      List<HitBase> searchResult = yrunner.runSearch(sentence + " "
          + verbAddition);
      if (searchResult != null) {
        for (HitBase item : searchResult) { // got some text from .html
          if (item.getAbstractText() != null
              && !(item.getUrl().indexOf(".pdf") > 0)) { // exclude
                                                         // pdf
            opinionSentencesToAdd
                .add(augmentWithMinedSentencesAndVerifyRelevance(item,
                    sentence, sents));
          }
        }
      }
    }

    opinionSentencesToAdd = removeDuplicatesFromResultantHits(opinionSentencesToAdd);
    return opinionSentencesToAdd;
  }

  public static List<String> buildSearchEngineQueryFromSentence(String sentence) {
    ParseTreeChunk matcher = new ParseTreeChunk();
    SyntMatcher pos = SyntMatcher.getInstance();
    List<List<ParseTreeChunk>> sent1GrpLst = null;

    List<LemmaPair> origChunks1 = new ArrayList<LemmaPair>();
    String[] sents1 = pos.getSentenceDetectorME().sentDetect(sentence);
    for (String s1 : sents1) {
      Parse[] parses1 = pos.parseLine(s1, pos.getParser(), 1);
      origChunks1.addAll(pos.getAllPhrasesTWPairs(parses1[0]));
    }

    List<ParseTreeChunk> chunk1List = matcher.buildChunks(origChunks1);
    sent1GrpLst = matcher.groupChunksAsParses(chunk1List);

    // System.out.println(origChunks1);
    // System.out.println("=== Grouped chunks 1 "+ sent1GrpLst.get(0));
    List<ParseTreeChunk> nPhrases = sent1GrpLst.get(0);
    List<String> queryArrayStr = new ArrayList<String>();
    for (ParseTreeChunk ch : nPhrases) {
      String query = "";
      int size = ch.getLemmas().size();

      for (int i = 0; i < size; i++) {
        if (ch.getPOSs().get(i).startsWith("N")
            || ch.getPOSs().get(i).startsWith("J")) {
          query += ch.getLemmas().get(i) + " ";
        }
      }
      query = query.trim();
      int len = query.split(" ").length;
      if (len < 2 || len > 5)
        continue;
      if (len < 4) { // every word should start with capital
        String[] qs = query.split(" ");
        boolean bAccept = true;
        for (String w : qs) {
          if (w.toLowerCase().equals(w)) // idf only two words then
            // has to be person name,
            // title or geo location
            bAccept = false;
        }
        if (!bAccept)
          continue;
      }

      query = query.trim().replace(" ", " +");
      query = " +" + query;

      queryArrayStr.add(query);

    }
    if (queryArrayStr.size() < 1) { // release constraints on NP down to 2
                                    // keywords
      for (ParseTreeChunk ch : nPhrases) {
        String query = "";
        int size = ch.getLemmas().size();

        for (int i = 0; i < size; i++) {
          if (ch.getPOSs().get(i).startsWith("N")
              || ch.getPOSs().get(i).startsWith("J")) {
            query += ch.getLemmas().get(i) + " ";
          }
        }
        query = query.trim();
        int len = query.split(" ").length;
        if (len < 2)
          continue;

        query = query.trim().replace(" ", " +");
        query = " +" + query;

        queryArrayStr.add(query);

      }
    }

    queryArrayStr = removeDuplicatesFromQueries(queryArrayStr);
    queryArrayStr.add(sentence);

    return queryArrayStr;

  }

  // remove dupes from queries to easy cleaning dupes and repetitive search
  // afterwards
  public static List<String> removeDuplicatesFromQueries(List<String> hits) {
    StringDistanceMeasurer meas = new StringDistanceMeasurer();
    double dupeThresh = 0.8; // if more similar, then considered dupes was
    // 0.7
    List<Integer> idsToRemove = new ArrayList<Integer>();
    List<String> hitsDedup = new ArrayList<String>();
    try {
      for (int i = 0; i < hits.size(); i++)
        for (int j = i + 1; j < hits.size(); j++) {
          String title1 = hits.get(i);
          String title2 = hits.get(j);
          if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
            continue;
          if (meas.measureStringDistance(title1, title2) > dupeThresh) {
            idsToRemove.add(j); // dupes found, later list member to
            // be deleted

          }
        }

      for (int i = 0; i < hits.size(); i++)
        if (!idsToRemove.contains(i))
          hitsDedup.add(hits.get(i));

      if (hitsDedup.size() < hits.size()) {
        LOG.debug("Removed duplicates from formed query, including "
            + hits.get(idsToRemove.get(0)));
      }

    } catch (Exception e) {
      LOG.error("Problem removing duplicates from query list");
    }

    return hitsDedup;

  }

  public static List<HitBase> removeDuplicatesFromResultantHits(
      List<HitBase> hits) {
    StringDistanceMeasurer meas = new StringDistanceMeasurer();
    double dupeThresh = 0.8; // if more similar, then considered dupes was
    // 0.7
    List<Integer> idsToRemove = new ArrayList<Integer>();
    List<HitBase> hitsDedup = new ArrayList<HitBase>();
    try {
      for (int i = 0; i < hits.size(); i++)
        for (int j = i + 1; j < hits.size(); j++) {
          String title1 = hits.get(i).toString();
          String title2 = hits.get(j).toString();
          if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
            continue;
          if (meas.measureStringDistance(title1, title2) > dupeThresh) {
            idsToRemove.add(j); // dupes found, later list member to
            // be deleted

          }
        }

      for (int i = 0; i < hits.size(); i++)
        if (!idsToRemove.contains(i))
          hitsDedup.add(hits.get(i));

      if (hitsDedup.size() < hits.size()) {
        LOG.debug("Removed duplicates from formed query, including "
            + hits.get(idsToRemove.get(0)));
      }

    } catch (Exception e) {
      LOG.error("Problem removing duplicates from query list");
    }

    return hitsDedup;

  }

  public HitBase augmentWithMinedSentencesAndVerifyRelevance(HitBase item,
      String originalSentence, List<String> sentsAll) {
    // put orig sentence in structure
    List<String> origs = new ArrayList<String>();
    origs.add(originalSentence);
    item.setOriginalSentences(origs);
    String title = item.getTitle().replace("<b>", " ").replace("</b>", " ")
        .replace("  ", " ").replace("  ", " ");
    // generation results for this sentence
    List<Fragment> result = new ArrayList<Fragment>();
    // form plain text from snippet
    String snapshot = item.getAbstractText().replace("<b>", " ")
        .replace("</b>", " ").replace("  ", " ").replace("  ", " ");

    SyntMatcher sm = SyntMatcher.getInstance();
    // fix a template expression which can be substituted by original if
    // relevant
    String snapshotMarked = snapshot.replace("...", " _should_find_orig_ .");
    String[] fragments = sm.getSentenceDetectorME().sentDetect(snapshotMarked);
    List<String> allFragms = new ArrayList<String>();
    allFragms.addAll(Arrays.asList(fragments));

    String[] sents = null;
    try {
      if (snapshotMarked.length() != snapshot.length()) {
        String downloadedPage = pFetcher.fetchPage(item.getUrl());
        if (downloadedPage != null && downloadedPage.length() > 100) {
          item.setPageContent(downloadedPage);
          String pageContent = Utils.fullStripHTML(item.getPageContent());
          pageContent = pageContent.trim().replace("  ", ". ")
              .replace("..", ".").replace(". . .", " ").trim(); // sometimes
                                                                // html breaks
                                                                // are converted
                                                                // into ' ' (two
                                                                // spaces), so
                                                                // we need to
                                                                // put '.'
          sents = sm.getSentenceDetectorME().sentDetect(pageContent);
          sents = cleanListOfSents(sents);
        }
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
      System.err
          .println("Problem downloading  the page and splitting into sentences");
    }

    for (String fragment : allFragms) {
      String followSent = null;
      if (fragment.length() < 50)
        continue;
      String pageSentence = "";
      // try to find original sentence from webpage
      if (fragment.indexOf("_should_find_orig_") > -1 && sents != null
          && sents.length > 0)
        try {
          String[] mainAndFollowSent = getFullOriginalSentenceFromWebpageBySnippetFragment(
              fragment.replace("_should_find_orig_", ""), sents);
          pageSentence = mainAndFollowSent[0];
          followSent = mainAndFollowSent[1];

        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      else
        // or get original snippet
        pageSentence = fragment;
      if (pageSentence != null)
        pageSentence.replace("_should_find_orig_", "");

      // resultant sentence SHOULD NOT be longer than twice the size of
      // snippet fragment
      if (pageSentence != null
          && (float) pageSentence.length() / (float) fragment.length() < 4.0) { // was
                                                                                // 2.0,
                                                                                // but
                                                                                // since
                                                                                // snippet
                                                                                // sentences
                                                                                // are
                                                                                // rather
                                                                                // short
                                                                                // now...
        try { // get score from syntactic match between sentence in
              // original text and mined sentence
          double measScore = 0.0, syntScore = 0.0, mentalScore = 0.0;

          SentencePairMatchResult matchRes = sm.assessRelevance(pageSentence
              + " " + title, originalSentence);
          List<List<ParseTreeChunk>> match = matchRes.getMatchResult();
          if (!matchRes.isVerbExists() || matchRes.isImperativeVerb()) {
            System.out
                .println("Rejected Sentence : No verb OR Yes imperative verb :"
                    + pageSentence);
            continue;
          }

          syntScore = parseTreeChunkListScorer
              .getParseTreeChunkListScore(match);
          System.out.println(parseTreeChunk.listToString(match) + " "
              + syntScore + "\n pre-processed sent = '" + pageSentence);

          if (syntScore < 1.5) { // trying other sents
            for (String currSent : sentsAll) {
              if (currSent.startsWith(originalSentence))
                continue;
              match = sm.matchOrigSentencesCache(currSent, pageSentence);
              double syntScoreCurr = parseTreeChunkListScorer
                  .getParseTreeChunkListScore(match);
              if (syntScoreCurr > syntScore) {
                syntScore = syntScoreCurr;
              }
            }
            if (syntScore > 1.5) {
              System.out.println("Got match with other sent: "
                  + parseTreeChunk.listToString(match) + " " + syntScore);
            }
          }

          measScore = STRING_DISTANCE_MEASURER.measureStringDistance(
              originalSentence, pageSentence);

          // now possibly increase score by finding mental verbs
          // indicating opinions
          for (String s : MENTAL_VERBS) {
            if (pageSentence.indexOf(s) > -1) {
              mentalScore += 0.3;
              break;
            }
          }

          if ((syntScore > 1.5 || measScore > 0.5 || mentalScore > 0.5)
              && measScore < 0.8 && pageSentence.length() > 40) {
            String pageSentenceProc = GeneratedSentenceProcessor
                .acceptableMinedSentence(pageSentence);
            if (pageSentenceProc != null) {
              pageSentenceProc = GeneratedSentenceProcessor
                  .processSentence(pageSentenceProc);
              if (followSent != null) {
                pageSentenceProc += " "
                    + GeneratedSentenceProcessor.processSentence(followSent);
              }

              pageSentenceProc = Utils.convertToASCII(pageSentenceProc);
              Fragment f = new Fragment(pageSentenceProc, syntScore + measScore
                  + mentalScore + (double) pageSentenceProc.length()
                  / (double) 50);
              f.setSourceURL(item.getUrl());
              f.fragment = fragment;
              result.add(f);
              System.out.println("Accepted sentence: " + pageSentenceProc
                  + "| with title= " + title);
              System.out.println("For fragment = " + fragment);
            } else
              System.out
                  .println("Rejected sentence due to wrong area at webpage: "
                      + pageSentence);
          } else
            System.out.println("Rejected sentence due to low score: "
                + pageSentence);
          // }
        } catch (Throwable t) {
          System.out.println("exception " + t);
        }
      }
    }
    item.setFragments(result);
    return item;
  }

  public static String[] cleanListOfSents(String[] sents) {
    List<String> sentsClean = new ArrayList<String>();
    for (String s : sents) {
      if (s == null || s.trim().length() < 30 || s.length() < 20)
        continue;
      sentsClean.add(s);
    }
    return (String[]) sentsClean.toArray(new String[0]);
  }

  public static String[] getFullOriginalSentenceFromWebpageBySnippetFragment(
      String fragment, String[] sents) {
    if (fragment.trim().length() < 15)
      return null;

    StringDistanceMeasurer meas = new StringDistanceMeasurer();
    Double dist = 0.0;
    String result = null, followSent = null;
    for (int i = 0; i < sents.length; i++) {
      String s = sents[i];
      if (s == null || s.length() < 30)
        continue;
      Double distCurr = meas.measureStringDistance(s, fragment);
      if (distCurr > dist && distCurr > 0.4) {
        result = s;
        dist = distCurr;
        if (i < sents.length - 1 && sents[i + 1].length() > 60) {
          followSent = sents[i + 1];
        }

      }
    }
    return new String[] { result, followSent };
  }

  public static void main(String[] args) {
    RelatedSentenceFinder f = new RelatedSentenceFinder();
    String b = GeneratedSentenceProcessor
        .acceptableMinedSentence("Earlier this year it was also Quidam that broke international ground "
            + " by becoming the first Cirque du Soleil show to be seen in the Middle "
            + " East - in Dubai, United Arab Emirates - now currently playing to sold "
            + " out audiences. ");
    /*
     * System.setProperty("resourcesDirectory",
     * "C:/workspace/ZSearch/resources_external");
     * System.setProperty("dragonDirectory", "DRAGON_PATH");
     * System.setProperty("StanfordNE_resources", "STANFORD_NE_RESOURCES");
     * System.setProperty("vcb_resources", "VCB_RESOURCES");
     * System.setProperty("bing_app_id", "BING_APP_ID");
     * System.setProperty("yahoo_app_id",
     * "lyzaGJDV34EsJbugCymf7_oosEfMtbSwUBOhDQ8abqgy_Sl2roPjyg72T5.k1NIoyQ--");
     */
    List<HitBase> hits = null;
    try {
      /*
       * // uncomment the sentence you would like to serve as a seed sentence
       * for content generation for an event description hits =
       * f.findRelatedOpinionsForSentence( //
       * "Did anyone expect there to be any sort of real change? The system is a whole lot bigger than one person these days"
       * , //
       * "Reflection of Neocolonial Aggression and Destruction in Libya. Emergence of New Era of Western Democratic Dictatorship"
       * , //
       * "It has been reported by Columbia Broadcasting Systems of the American - Israeli Military Industrial Complex that the International Human Rights Organization has called upon the civilized nations of the Globe to place under arrest George Bush"
       * , //
       * "Washington bailed out the crooks who created this disaster, then they carry on with business as usual"
       * , //
       * "my comment about Rall's taking out his frustrations in writing because he can't make any money drawing never made it onto this board"
       * ,
       * "I like the app, I use it to find fun things to do in my area, it's amazing how much cool stuff it finds for me"
       * , //
       * "Cyclo-cross  is a form of bicycle racing. Races typically take place in the autumn and winter laps of a short course featuring pavement, wooded trails, grass, steep hills"
       * , //
       * "celebrate mama. Pampering for Mom including free make overs and wine. Free Gift Bags with Goodies and Samples to the first 250 Moms"
       * , //
       * "Washington Congress is taking a cautious approach to the massive street protests sweeping Egypt encouraging cries for reform, but wary that a more radical regime in Cairo could damage US interests, including the survival of Israel."
       * , // "College football Virginia Tech", //
       * " automatic ways and refereneces if they existed to produce content that increase the ratings bias opinions, counter-attach and compensate the former attacks in SEM and Opinion Mining in an automated way instead of having thousands of grantholders that manually verify the honesty of sites"
       * , //
       * "Virginia Tech quarterback Tyrod Taylor enjoyed a magnificent night, throwing three touchdown pas,ses and rushing for another in leading Virginia Tech to the ACC championship with a 44-33 victory over Florida State in the league�s title game played in front of 72,379 fans at Bank of America Stadium on Saturday night"
       * , //
       * "US banking giant Citigroup has taken over the ownership of EMI, the record label where it was the major creditor"
       * , //
       * "Egypt's army vows it will not use force against demonstrators, as the government says it is preparing to open talks with the opposition."
       * ,
       * 
       * //
       * "If you aren't enrolled in Paperless Statements and think you've received this message in error, please call our Customer Support team immediately, using the phone number on the Contact Us page on Chase Online"
       * , // "Summer camp fair at french american international school",//
       * "russian composers paganini", Arrays.asList(new String[] {
       * 
       * })); System.out.println(HitBase.toString(hits));
       * System.out.println(HitBase.toString(hits).replace("[", "").replace("]",
       * "").replace(" | ", ""));
       */
      // uncomment the sentence you would like to serve as a seed sentence for
      // content generation for an event description
      hits = f.findActivityDetailsForEventGroupName(
          "Britney Spears - The Femme Fatale Tour",
          // "Rush Time Machine",
          // "amazon webservices summit",
          // "Blue Man Group" ,
          // "Belly Dance With Zaharah",
          // "Hollander Musicology Lecture: Danielle Fosler-Lussier, Guest Lecturer",
          // "Jazz Master and arguably the most famous jazz musician alive, trumpeter Wynton Marsalis",
          // "Cyclo-cross  is a form of bicycle racing. Races typically take place in the autumn and winter laps of a short course featuring pavement, wooded trails, grass, steep hills",
          Arrays.asList(new String[] {}));
      System.out.println(HitBase.toString(hits));
      System.out.println(HitBase.toString(hits).replace("[", "")
          .replace("]", "").replace(" | ", ""));

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

}
