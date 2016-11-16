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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import opennlp.tools.parse_thicket.apps.SnippetToParagraph;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.HitBaseComparable;
import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;



public class SearchResultsReRankerStanfRequestHandler extends SearchHandler {
	private static Logger LOG = Logger
			.getLogger("com.become.search.requestHandlers.SearchResultsReRankerRequestHandler");
	private final static int MAX_SEARCH_RESULTS = 100;
	private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	private int MAX_QUERY_LENGTH_NOT_TO_RERANK=3;
	private Matcher matcher = new Matcher();
	private BingQueryRunner bingSearcher = new BingQueryRunner();
	private SnippetToParagraph snp = new SnippetToParagraph();
	
	
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
		List<String> keys = new ArrayList<String>();
		while(iter.hasNext()){
			keys.add(iter.next());
		}

		List<HitBase> searchResults = new ArrayList<HitBase>();





		for ( Integer i=0; i< MAX_SEARCH_RESULTS; i++){
			String title = req.getParams().get("t"+i.toString());
			String descr = req.getParams().get("d"+i.toString());

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
					hit.setSource(new Integer(count).toString());
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
		 * 
		 * 
		 */
		StringBuffer buf = new StringBuffer(); 
		buf.append("<scores>");
		for(HitBase hit: reRankedResults){
			buf.append("<score index=\""+hit.getSource()+"\">"+hit.getGenerWithQueryScore()+"</score>");				
		}
		buf.append("</scores>");

		NamedList<Object> scoreNum = new NamedList<Object>();
		for(HitBase hit: reRankedResults){
			scoreNum.add(hit.getSource(), hit.getGenerWithQueryScore());				
		}
		
		StringBuffer bufNums = new StringBuffer(); 
		bufNums.append("order>");
		for(HitBase hit: reRankedResults){
			bufNums.append(hit.getSource()+"_");				
		}
		bufNums.append("/order>");
		
		LOG.info("re-ranking results: "+buf.toString());
		NamedList<Object> values = rsp.getValues();
		values.remove("response");
		values.add("response", scoreNum); 
		values.add("new_order", bufNums.toString().trim());
		rsp.setAllValues(values);
		
	}

	private boolean orQuery(String query) {
		if (query.indexOf('|')>-1)
			return true;

		return false;
	}

	private boolean tooFewKeywords(String query) {
		String[] parts = query.split(" ");
		if (parts!=null && parts.length< MAX_QUERY_LENGTH_NOT_TO_RERANK)
			return true;

		return false;
	}

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
				List<List<ParseTreeChunk>> match = null;
				if (pageSentsAndSnippet!=null && pageSentsAndSnippet[0].length()>50){
					match = matcher.assessRelevanceCache(pageSentsAndSnippet[0] ,
							searchQuery);
					score = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
					hit.setSource(match.toString());
				}
				if (score < 2){ // attempt to match with snippet, if not much luck with original text
					match = matcher.assessRelevanceCache(pageSentsAndSnippet[0] ,
							searchQuery);
					score = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
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


	public class HitBaseComparable implements Comparator<HitBase> {
		// @Override
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