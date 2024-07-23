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
import java.util.List;
import java.util.logging.Logger;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class SearchResultsProcessor extends BingQueryRunner {
  private static final Logger LOG =
          Logger.getLogger("opennlp.tools.similarity.apps.SearchResultsProcessor");
  private final ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
  ParserChunker2MatcherProcessor sm;
  final WebSearchEngineResultsScraper scraper = new WebSearchEngineResultsScraper();

  /*
   * Takes a search engine API (or scraped) search results and calculates the parse tree similarity
   * between the question and each snippet. Ranks those snippets with higher
   * similarity score up
   */
  
  
  private List<HitBase> calculateMatchScoreResortHits(List<HitBase> hits,
      String searchQuery) {

    List<HitBase> newHitList = new ArrayList<>();
    sm = ParserChunker2MatcherProcessor.getInstance();

    for (HitBase hit : hits) {
      String snapshot = hit.getAbstractText().replace("<b>...</b>", ". ").replace("<span class='best-phrase'>", " ").replace("<span>", " ").replace("<span>", " ")
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
          LOG.finest(score + " | " + snapshot);
         */
      } catch (Exception e) {
        LOG.warning("Problem processing snapshot " + snapshot);
        e.printStackTrace();
      }
      hit.setGenerWithQueryScore(score);
      newHitList.add(hit);
    }
    newHitList.sort(new HitBaseComparable());
   
    // LOG.info("\n\n ============= NEW ORDER ================= ");
    /*
    for (HitBase hit : newHitList) {
      LOG.info(hit.toString());
    }
    */

    return newHitList;
  }

  public void close() {
    sm.close();
  }

  public List<HitBase> runSearch(String query) {
    
    
    List<HitBase> hits = scraper.runSearch(query);
    hits = calculateMatchScoreResortHits(hits, query);
    return hits;
  }
  
  public List<HitBase> runSearchViaAPI(String query) {
	List<HitBase> hits;
    try {
      List<HitBase> resultList = runSearch(query);
      // now we apply our own relevance filter
      hits = calculateMatchScoreResortHits(resultList, query);

    } catch (Exception e) {
      // e.printStackTrace();
      LOG.info("No search results for query '" + query);
      return null;
    }


    return hits;
  }

}
