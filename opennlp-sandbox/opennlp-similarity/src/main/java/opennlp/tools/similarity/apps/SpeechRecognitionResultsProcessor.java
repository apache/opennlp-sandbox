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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeechRecognitionResultsProcessor /*extends BingWebQueryRunner*/ {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
  private ParserChunker2MatcherProcessor sm;
  private final WebSearchEngineResultsScraper scraper = new WebSearchEngineResultsScraper();

  /**
   * Gets an expression and tries to find it on the web. If search results are
   * syntactically similar to this phrase, then we conclude that this phrase is
   * meaningful (makes sense), someone have said something similar. If search
   * results ate not similar to this phrase, we conclude that the phrase is
   * meaningless (does not make sense, nobody has ever said something like that)
   * 
   * @param  hits
   *          list of search results for a phrase being assesses with
   *          respect to meaningfulness
   * @param searchQuery
   *          the phrase we are assessing
   * @return total similarity score for all search results
   */
  private double calculateTotalMatchScoreForHits(List<HitBase> hits,
      String searchQuery) {

    sm = ParserChunker2MatcherProcessor.getInstance();
    double totalMatchScore = 0;
    for (HitBase hit : hits) {
      String snapshot = hit.getAbstractText().replace("<b>...</b>", ". ")
          .replace("<b>", "").replace("</b>", "");
      snapshot = snapshot.replace("</B>", "").replace("<B>", "")
          .replace("<br>", "").replace("</br>", "").replace("...", ". ")
          .replace("|", " ").replace(">", " ");
      snapshot += " . " + hit.getTitle();
      double score = 0.0;
      try {
        SentencePairMatchResult matchRes = sm.assessRelevance(snapshot,
            searchQuery);
        List<List<ParseTreeChunk>> match = matchRes.getMatchResult();
        score = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
        /*
        if (score > 1.5) {
          LOG.info(score + " | " + match);
        }
        */
      } catch (Exception e) {
        LOG.error("Problem processing snapshot " + snapshot, e);
      }
      totalMatchScore += score;

    }

    return totalMatchScore;
  }

  public void close() {
    sm.close();
  }

  /**
   * phrase meaningfulness assessment function which takes a list of phrases
   * which are speech recognition results and re-ranks these phrases according
   * to the meaningfulness score which is determined by
   * 'calculateTotalMatchScoreForHits'
   * 
   * @param sents
   *          list of phrases which are speech recognition results
   * @return re-ranked list of phrases which are speech recognition results
   *         (from more meaningfulness to less meaningfulness)
   */
  public List<SentenceMeaningfullnessScore> runSearchAndScoreMeaningfulness(
      List<String> sents) {
    List<SentenceMeaningfullnessScore> res = new ArrayList<>();
    double bestSentScore = -1;
    for (String sentence : sents) {
      try {
        List<HitBase> resultList = scraper.runSearch(sentence);
        double scoreForSentence = calculateTotalMatchScoreForHits(resultList, sentence);
        LOG.debug("Total meaningfulness score = {} for sentence = {}", scoreForSentence, sentence);
        if (scoreForSentence > bestSentScore) {
          bestSentScore = scoreForSentence;
        }
        res.add(new SentenceMeaningfullnessScore(sentence, scoreForSentence));
      } catch (Exception e) {
        LOG.warn("No search results for query '{}' - reason: {}", sentence, e.getLocalizedMessage());
        return null;
      }
    }
    return res;

  }

  public static class SentenceMeaningfullnessScore {
    final String sentence;
    final double score;

    public SentenceMeaningfullnessScore(String sent, double sc) {
      sentence = sent;
      score = sc;
    }

    @Override
    public String toString() {
      return "Total meaningfulness score = " + score + " for sentence = "
          + sentence + "\n";
    }

    public double getScore() {
      return score;
    }
  }

  public static void main(String[] args) {
    SpeechRecognitionResultsProcessor proc = new SpeechRecognitionResultsProcessor();
    proc.runSearchAndScoreMeaningfulness(Arrays.asList(
            "meeting with alex at you for not to come over to 8 pm",
            "meeting with alex at you for not to come over to eat",
            "meeting with alex at il fornaio tomorrow to 8 pm"));

    proc.runSearchAndScoreMeaningfulness(Arrays.asList(
            "remember to buy milk tomorrow for details",
            "remember to buy milk tomorrow from trader joes",
            "remember to buy milk tomorrow from 3 to jones",
            "remember to buy milk tomorrow for for details",
            "remember to buy milk tomorrow from third to joes",
            "remember to buy milk tomorrow from third to jones",
            "remember to buy milk tomorrow from for d jones"));

    proc.runSearchAndScoreMeaningfulness(Arrays.asList(
            "I'm off tomorrow to shop at trader joes",
            "number to get milk tomorrow trader joes",
            "number 2 finals tomorrow from trader joes",
            "number 2 finals tomorrow trader joes",
            "number to buy move tomorrow from trader joes",
            "number to buy move tomorrow trader joes",
            "define move tomorrow from trader joes",
            "define move tomorrow trader joes"));
  }

}
