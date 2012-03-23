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
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;


public class SearchResultsProcessor extends BingWebQueryRunner {
	private static Logger LOG = Logger.getLogger("opennlp.tools.similarity.apps.SearchResultsProcessor");
	private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	ParserChunker2MatcherProcessor sm ;

	/*
	 * Takes Bing API search results and calculates the parse tree similarity between the question and each snippet.
	 * Ranks those snippets with higher similarity score up
	 */
	private	BingResponse calculateMatchScoreResortHits(BingResponse resp, String searchQuery){
		// TODO
		/*if query is multi-sentence, special handling
		int indexDot = searchQuery.indexOf("."); 
		if (indexDot>0 && indexDot<searchQuery.length()-1){
			MultipleSentenceQueryAnswerer ans = new MultipleSentenceQueryAnswerer();
			return ans.calculateMatchScoreResortHits(resp, searchQuery);		
		} */
		List<HitBase> newHitList =	new ArrayList<HitBase>();
		sm = ParserChunker2MatcherProcessor.getInstance();

		for(HitBase hit: resp.getHits()){
			String snapshot = hit.getAbstractText().replace("<b>...</b>", ". ").replace("<b>", "").replace("</b>","");
			snapshot=snapshot.replace("</B>", "").replace("<B>", "").replace("<br>", "").replace("</br>", "").replace("...", ". ").replace("|", " ").replace(">", " ");
			snapshot+=" . "+hit.getTitle();
			Double score = 0.0;
			try {
				SentencePairMatchResult matchRes = sm.assessRelevance(snapshot, searchQuery);
				List<List<ParseTreeChunk>> match = matchRes.getMatchResult();
				score = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
				LOG.finest(score + " | "+ snapshot );
			}
			catch (Exception e){
				LOG.severe("Problem processing snapshot "+snapshot);
				e.printStackTrace();
			}
			hit.setGenerWithQueryScore(score);
			newHitList.add(hit);
		}
		Collections.sort(newHitList,new HitBaseComparable());
		resp.setHits(newHitList);
		LOG.info("\n\n ============= NEW ORDER ================= ");
		for(HitBase hit: newHitList){
			LOG.info(hit.toString());
		}


		return resp; 
	}
	
	public void close(){
		sm.close();
	}

	public List<HitBase> runSearch(String query) {
		BingResponse resp = null, // obtained from bing
		newResp = null; // re-sorted based on similarity
		try {
			List<String> resultList = search(query, "", "", 30);
			resp = populateBingHit(resultList.get(0));
			// now we apply our own relevance filter
			newResp=calculateMatchScoreResortHits(resp, query);

		} catch (Exception e) {
			// e.printStackTrace();
			LOG.info("No search results for query '" + query);
			e.printStackTrace();
			return null;
		}
		// cast to super class
		List<HitBase> hits = new ArrayList<HitBase>();
		for (HitBase h : resp.getHits())
			hits.add((HitBase) h);

		hits = removeDuplicates(hits, 0.9);

		return hits;
	}




}
