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
package opennlp.tools.parse_thicket.apps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;



import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.HitBaseComparable;
import opennlp.tools.similarity.apps.WebSearchEngineResultsScraper;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class MultiSentenceSearchResultsProcessor  {
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.SearchResultsProcessor");

	private WebSearchEngineResultsScraper scraper = new WebSearchEngineResultsScraper();
	private ParserChunker2MatcherProcessor matcher = ParserChunker2MatcherProcessor.getInstance();
	private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	private BingQueryRunner bingSearcher = new BingQueryRunner();
	private SnippetToParagraph snp = new SnippetToParagraph();

	protected static final int NUM_OF_SEARCH_RESULTS = 10;

	/*
	 * Takes a search engine API (or scraped) search results and calculates the parse tree similarity
	 * between the question and each snippet. Ranks those snippets with higher
	 * similarity score up
	 */


	protected List<HitBase> calculateMatchScoreResortHits(List<HitBase> hits,
			String searchQuery) {

		List<HitBase> newHitList = new ArrayList<HitBase>();
		int count = 0;
		for (HitBase hit : hits) {
			if (count>10)
				break;
			count++;
			String[] pageSentsAndSnippet = formTextForReRankingFromHit(hit);
					
			Double score = 0.0;
			try {
				SentencePairMatchResult match = null;
				if (pageSentsAndSnippet!=null && pageSentsAndSnippet[0].length()>50){
					match = matcher.assessRelevance(pageSentsAndSnippet[0] ,
							searchQuery);
					 score = parseTreeChunkListScorer.getParseTreeChunkListScore(match.getMatchResult());
							//parseTreeChunkListScorer.getParseTreeChunkListScore(match);
					hit.setSource(match.toString());
				}
				if (score < 2){ // attempt to match with snippet, if not much luck with original text
					match = matcher.assessRelevance(pageSentsAndSnippet[1] ,
							searchQuery);
					score = parseTreeChunkListScorer.getParseTreeChunkListScore(match.getMatchResult());
				}
				LOG.info(score + " | " +pageSentsAndSnippet[1]);
			} catch (Exception e) {
				LOG.severe("Problem processing snapshot " + pageSentsAndSnippet[1]);
				e.printStackTrace();
			}
			hit.setGenerWithQueryScore(score);
			newHitList.add(hit);
		}
		
		System.out.println("\n\n ============= old ORDER ================= ");
		for (HitBase hit : newHitList) {
			System.out.println(hit.getOriginalSentences().toString() + " => "+hit.getGenerWithQueryScore());
			System.out.println("match = "+hit.getSource());
		}
		Collections.sort(newHitList, new HitBaseComparable());

		System.out.println("\n\n ============= NEW ORDER ================= ");
		for (HitBase hit : newHitList) {
			System.out.println(hit.getOriginalSentences().toString() + " => "+hit.getGenerWithQueryScore());
			System.out.println("match = "+hit.getSource());
		}

		return newHitList;
	}

	protected String[] formTextForReRankingFromHit(HitBase hit) {
		HitBase hitWithFullSents = snp.formTextFromOriginalPageGivenSnippet(hit);
		String textFromOriginalPage = "";
		try {
			List<String> sents = hitWithFullSents.getOriginalSentences();
			for(String s: sents){
				textFromOriginalPage+=s+" ";
			}

			if (textFromOriginalPage.startsWith(".")){
				textFromOriginalPage = textFromOriginalPage.substring(2);
			}
			textFromOriginalPage = textFromOriginalPage.replace(" . .", ". ").replace(". . ", ". ").
					replace("..", ". ").trim();
		} catch (Exception e1) {
			e1.printStackTrace();
			LOG.info("Problem processing snapshot "+hit.getAbstractText());
		}
		hit.setPageContent(textFromOriginalPage);
		String snapshot = hit.getAbstractText().replace("<b>...</b>", ". ").replace("<span class='best-phrase'>", " ").replace("<span>", " ").replace("<span>", " ")
				.replace("<b>", "").replace("</b>", "");
		snapshot = snapshot.replace("</B>", "").replace("<B>", "")
				.replace("<br>", "").replace("</br>", "").replace("...", ". ")
				.replace("|", " ").replace(">", " ").replace(". .", ". ");
		snapshot += " . " + hit.getTitle();
		
		return new String[] { textFromOriginalPage, snapshot };
	}

	public void close() {
		// TODO
		// matcher.close();
	}

	public List<HitBase> runSearch(String query) {


		List<HitBase> hits = scraper.runSearch(query);
		hits = calculateMatchScoreResortHits(hits, query);
		return hits;
	}


	public List<HitBase> runSearchViaAPI(String query) {
		List<String[]> reportData = new ArrayList<String[]>(); 
		reportData.add(new String[]{query});
		List<HitBase> hits = null;
		try {
			List<HitBase> resultList = bingSearcher.runSearch(query, NUM_OF_SEARCH_RESULTS);
			reportData.add(convertListHitBaseIntoStringAr(resultList));
			
			// now we apply our own relevance filter
			hits = calculateMatchScoreResortHits(resultList, query);
			reportData.add(convertListHitBaseIntoStringAr(resultList));
		} catch (Exception e) {
			e.printStackTrace();
			LOG.info("No search results for query '" + query);
			return null;
		}
		//ProfileReaderWriter.writeReport(reportData, "resultsForQuery_"+query.replace(' ', '_')+".csv");
		return hits;
	}
	
	private String[] convertListHitBaseIntoStringAr(List<HitBase> list){
		List<String> results = new  ArrayList<String>(); 
		for(HitBase h: list ){
			results.add(h.getTitle()+ " | "+h.getAbstractText());
		}
		return results.toArray(new String[0]);
	}

	public static void main(String[] args){
		String query = " I see no meaningful distinction between complacency or complicity in the military's latest failure to uphold their own " +
				"standards of conduct. Nor do I see a distinction between the service member who orchestrated this offense and the chain of " +
				"command that was either oblivious to or tolerant of criminal behavior";

		new MultiSentenceSearchResultsProcessor().runSearchViaAPI(query);
	}

}
