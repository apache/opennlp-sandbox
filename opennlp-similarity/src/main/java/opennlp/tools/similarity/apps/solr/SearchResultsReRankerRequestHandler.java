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
package opennlp.tools.similarity.apps.solr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

public class SearchResultsReRankerRequestHandler extends SearchHandler {
	private static final Logger LOG =
					Logger.getLogger("com.become.search.requestHandlers.SearchResultsReRankerRequestHandler");
	private final static int MAX_SEARCH_RESULTS = 100;
	private final ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	private ParserChunker2MatcherProcessor sm = null;
	private static final String resourceDir = "/home/solr/solr-4.4.0/example/src/test/resources";
	//"C:/workspace/TestSolr/src/test/resources";

	//"/data1/solr/example/src/test/resources";

	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp){
		// get query string
		String requestExpression = req.getParamString();
		String[] exprParts = requestExpression.split("&");
		for(String part: exprParts){
			if (part.startsWith("q="))
				requestExpression = part;			
		}
		String query = StringUtils.substringAfter(requestExpression, ":");
		LOG.info(requestExpression);


		SolrParams ps = req.getOriginalParams();
		Iterator<String> iter =  ps.getParameterNamesIterator();
		List<String> keys = new ArrayList<>();
		while(iter.hasNext()){
			keys.add(iter.next());
		}

		List<HitBase> searchResults = new ArrayList<HitBase>();





		for (Integer i=0; i< MAX_SEARCH_RESULTS; i++){
			String title = req.getParams().get("t"+i);
			String descr = req.getParams().get("d"+i);

			if(title==null || descr==null)
				continue;

			HitBase hit = new HitBase();
			hit.setTitle(title);
			hit.setAbstractText(descr);
			hit.setSource(i.toString());
			searchResults.add(hit);
		}

		/*
		 * http://173.255.254.250:8983/solr/collection1/reranker/?
		 * q=search_keywords:design+iphone+cases&fields=spend+a+day+with+a+custom+iPhone+case&fields=Add+style+to+your+every+day+fresh+design+with+a+custom+iPhone+case&fields=Add+style+to+your+every+day+with+mobile+case+for+your+family&fields=Add+style+to+your+iPhone+and+iPad&fields=Add+Apple+fashion+to+your+iPhone+and+iPad
		 * 
		 */

		if (searchResults.size()<1) {
			int count=0;
			for(String val : exprParts){
				if (val.startsWith("fields=")){
					val = StringUtils.mid(val, 7, val.length());
					HitBase hit = new HitBase();
					hit.setTitle("");
					hit.setAbstractText(val);
					hit.setSource(Integer.toString(count));
					searchResults.add(hit);
					count++;
				}

			}
		}


		List<HitBase> reRankedResults = null;
		query = query.replace('+', ' ');
		if (tooFewKeywords(query)|| orQuery(query)){
			reRankedResults = searchResults;
			LOG.info("No re-ranking for "+query);
		}
		else 
			reRankedResults = calculateMatchScoreResortHits(searchResults, query);
		/*
		 * <scores>
					<score index="2">3.0005</score>
					<score index="1">2.101</score>
					<score index="3">2.1003333333333334</score>
					<score index="4">2.00025</score>
					<score index="5">1.1002</score>
			 </scores>
		 */
		StringBuilder buf = new StringBuilder();
		buf.append("<scores>");
		for(HitBase hit: reRankedResults){
			buf.append("<score index=\""+hit.getSource()+"\">"+hit.getGenerWithQueryScore()+"</score>");				
		}
		buf.append("</scores>");

		NamedList<Object> scoreNum = new NamedList<>();
		for(HitBase hit: reRankedResults){
			scoreNum.add(hit.getSource(), hit.getGenerWithQueryScore());				
		}
		
		StringBuffer bufNums = new StringBuffer(); 
		bufNums.append("order>");
		for(HitBase hit: reRankedResults){
			bufNums.append(hit.getSource()+"_");				
		}
		bufNums.append("/order>");
		
		LOG.info("re-ranking results: "+ buf);
		NamedList<Object> values = rsp.getValues();
		values.remove("response");
		values.add("response", scoreNum); 
		//values.add("new_order", bufNums.toString().trim());
		rsp.setAllValues(values);
		
	}

	private boolean orQuery(String query) {
		if (query.indexOf('|')>-1)
			return true;

		return false;
	}

	private boolean tooFewKeywords(String query) {
		String[] parts = query.split(" ");
		int MAX_QUERY_LENGTH_NOT_TO_RERANK = 3;
		if (parts!=null && parts.length< MAX_QUERY_LENGTH_NOT_TO_RERANK)
			return true;

		return false;
	}

	private List<HitBase> calculateMatchScoreResortHits(List<HitBase> hits,
			String searchQuery) {
		try {
			sm =  ParserChunker2MatcherProcessor.getInstance(resourceDir);
		} catch (Exception e){
			LOG.severe(e.getMessage());
		}
		List<HitBase> newHitList = new ArrayList<>();

		int count=1;
		for (HitBase hit : hits) {
			String snapshot = hit.getAbstractText();
			snapshot += " . " + hit.getTitle();
			double score = 0.0;
			try {
				SentencePairMatchResult matchRes = sm.assessRelevance(snapshot,
						searchQuery);
				List<List<ParseTreeChunk>> match = matchRes.getMatchResult(); // we need the second member
				// so that when scores are the same, original order is maintained
				score = parseTreeChunkListScorer.getParseTreeChunkListScore(match)+0.001/(double)count;
			} catch (Exception e) {
				LOG.info(e.getMessage());
				e.printStackTrace();
			}
			hit.setGenerWithQueryScore(score);
			newHitList.add(hit);
			count++;
		}
		newHitList.sort(new HitBaseComparable());
		LOG.info(newHitList.toString());

		return newHitList;
	}


	public static class HitBaseComparable implements Comparator<HitBase> {

		@Override
		public int compare(HitBase o1, HitBase o2) {
			return (o1.getGenerWithQueryScore() > o2.getGenerWithQueryScore() ? -1
					: (o1 == o2 ? 0 : 1));
		}
	}

}

/*

http://dev1.exava.us:8086/solr/collection1/reranker/?q=search_keywords:I+want+style+in+my+every+day+fresh+design+iphone+cases
&t1=Personalized+iPhone+4+Cases&d1=spend+a+day+with+a+custom+iPhone+case
&t2=iPhone+Cases+to+spend+a+day&d2=Add+style+to+your+every+day+fresh+design+with+a+custom+iPhone+case
&t3=Plastic+iPhone+Cases&d3=Add+style+to+your+every+day+with+mobile+case+for+your+family
&t4=Personalized+iPhone+and+iPad+Cases&d4=Add+style+to+your+iPhone+and+iPad
&t5=iPhone+accessories+from+Apple&d5=Add+Apple+fashion+to+your+iPhone+and+iPad

http://dev1.exava.us:8086/solr/collection1/reranker/?q=search_keywords:I+want+style+in+my+every+day+fresh+design+iphone+cases&t1=Personalized+iPhone+4+Cases&d1=spend+a+day+with+a+custom+iPhone+case&t2=iPhone+Cases+to+spend+a+day&d2=Add+style+to+your+every+day+fresh+design+with+a+custom+iPhone+case&t3=Plastic+iPhone+Cases&d3=Add+style+to+your+every+day+with+mobile+case+for+your+family&t4=Personalized+iPhone+and+iPad+Cases&d4=Add+style+to+your+iPhone+and+iPad&t5=iPhone+accessories+from+Apple&d5=Add+Apple+fashion+to+your+iPhone+and+iPad
 */