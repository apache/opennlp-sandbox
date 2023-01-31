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
package opennlp.tools.similarity.apps.taxo_builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.StringCleaner;
import opennlp.tools.stemmer.PStemmer;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

/**

 * 
 */

public class DomainTaxonomyExtender {
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.taxo_builder.DomainTaxonomyExtender");

	private BingQueryRunner brunner = new BingQueryRunner();
	private ParserChunker2MatcherProcessor matcher = ParserChunker2MatcherProcessor.getInstance();

	protected static String BING_KEY = "WFoNMM706MMJ5JYfcHaSEDP+faHj3xAxt28CPljUAHA";
	

	private final static String TAXO_FILENAME = "taxo_data.dat";

	private Map<String, List<List<String>>> lemma_ExtendedAssocWords = new HashMap<String, List<List<String>>>();
	private Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords = new HashMap<List<String>, List<List<String>>>();
	private PStemmer ps;

	CsvAdapter adapter = new CsvAdapter();

	public Map<List<String>, List<List<String>>> getAssocWords_ExtendedAssocWords() {
		return assocWords_ExtendedAssocWords;
	}

	public Map<String, List<List<String>>> getLemma_ExtendedAssocWords() {
		return lemma_ExtendedAssocWords;
	}

	public void setLemma_ExtendedAssocWords(
			Map<String, List<List<String>>> lemma_ExtendedAssocWords) {
		this.lemma_ExtendedAssocWords = lemma_ExtendedAssocWords;
	}

	public DomainTaxonomyExtender() {
		ps = new PStemmer();
		adapter.importCSV();
		brunner.setKey(BING_KEY);
	}

	private List<List<String>> getCommonWordsFromList_List_ParseTreeChunk(
			List<List<ParseTreeChunk>> matchList, List<String> queryWordsToRemove,
			List<String> toAddAtEnd) {
		List<List<String>> res = new ArrayList<List<String>>();
		for (List<ParseTreeChunk> chunks : matchList) {
			List<String> wordRes = new ArrayList<String>();
			for (ParseTreeChunk ch : chunks) {
				List<String> lemmas = ch.getLemmas();
				for (int w = 0; w < lemmas.size(); w++)
					if ((!lemmas.get(w).equals("*"))
							&& ((ch.getPOSs().get(w).startsWith("NN") || ch.getPOSs().get(w).startsWith("JJ") || ch.getPOSs().get(w)
									.startsWith("VB"))) && lemmas.get(w).length() > 2) {
						String formedWord = lemmas.get(w);
						String stemmedFormedWord = ps.stem(formedWord);
						if (!stemmedFormedWord.startsWith("invalid"))
							wordRes.add(formedWord);
					}
			}
			wordRes = new ArrayList<String>(new HashSet<String>(wordRes));
			List<String> cleanedRes = new ArrayList<String>();
			for(String s: wordRes){
				if (!queryWordsToRemove.contains(s))
					cleanedRes .add(s);  	  
			}
			//wordRes.removeAll(queryWordsToRemove);
			if (cleanedRes.size() > 0) {
				//cleanedRes.addAll(toAddAtEnd);
				res.add(cleanedRes);
			}
		}
		res = new ArrayList<List<String>>(new HashSet<List<String>>(res));
		return res;
	}

	public void extendTaxonomy(String fileNameWithTaxonomyRoot, String domain, String lang) {


		List<String> entries = new ArrayList<String>((adapter.lemma_AssocWords.keySet()));
		try {
			for (String entity : entries) { // .
				List<List<String>> paths = adapter.lemma_AssocWords.get(entity);
				for (List<String> taxoPath : paths) {
					String query = taxoPath.toString() + " " + entity + " " + domain; 
					query = query.replace('[', ' ').replace(']', ' ').replace(',', ' ')
							.replace('_', ' ');
					List<List<ParseTreeChunk>> matchList = runSearchForTaxonomyPath(
							query, "", lang, 20); //30
					List<String> toRemoveFromExtension = new ArrayList<String>(taxoPath);
					toRemoveFromExtension.add(entity);
					toRemoveFromExtension.add(domain);
					List<List<String>> resList = getCommonWordsFromList_List_ParseTreeChunk(
							matchList, toRemoveFromExtension, taxoPath);
					assocWords_ExtendedAssocWords.put(taxoPath, resList);
					resList.add(taxoPath);
					lemma_ExtendedAssocWords.put(entity, resList);

					TaxonomySerializer ser = new TaxonomySerializer(lemma_ExtendedAssocWords,
							assocWords_ExtendedAssocWords);
					ser.writeTaxonomy(TAXO_FILENAME);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Problem taxonomy matching");
		}


	}

	public List<List<ParseTreeChunk>> runSearchForTaxonomyPath(String query,
			String domain, String lang, int numbOfHits) {
		List<List<ParseTreeChunk>> genResult = new ArrayList<List<ParseTreeChunk>>();
		try {
			List<HitBase> resultList = brunner.runSearch(query, numbOfHits);

			for (int i = 0; i < resultList.size(); i++) {
				{
					for (int j = i + 1; j < resultList.size(); j++) {
						HitBase h1 = resultList.get(i);
						HitBase h2 = resultList.get(j);
						String snapshot1 = StringCleaner.processSnapshotForMatching(h1
								.getTitle() + " " + h1.getAbstractText());
						String snapshot2 = StringCleaner.processSnapshotForMatching(h2
								.getTitle() + " " + h2.getAbstractText());
						 SentencePairMatchResult overlaps = matcher.assessRelevance(snapshot1, snapshot2);
						genResult.addAll(overlaps.matchResult);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.err.print("Problem searching for "+query);
		}

		return genResult;
	}

	public List<String> runSearchForTaxonomyPathFlatten(String query,
			String domain, String lang, int numbOfHits) {
		List<String> genResult = new ArrayList<String>();
		try {
			List<HitBase> resultList = brunner.runSearch(query, numbOfHits);

			for (int i = 0; i < resultList.size(); i++) {
				{
					for (int j = i + 1; j < resultList.size(); j++) {
						HitBase h1 = resultList.get(i);
						HitBase h2 = resultList.get(j);
						String snapshot1 = StringCleaner.processSnapshotForMatching(h1
								.getTitle() + " " + h1.getAbstractText());
						String snapshot2 = StringCleaner.processSnapshotForMatching(h2
								.getTitle() + " " + h2.getAbstractText());
						List<String> overlaps =assessKeywordOverlap(snapshot1, snapshot2);
						genResult.addAll(overlaps);
					}
				}
			}

		} catch (Exception e) {
			System.err.print("Problem searching for "+query);
		}

		return genResult;
	}



	private List<String> assessKeywordOverlap(String snapshot1, String snapshot2) {
		List<String> results = new ArrayList<String>();
		List<String> firstList = TextProcessor.fastTokenize(snapshot1, false), 
				secondList = TextProcessor.fastTokenize(snapshot2, false);	  
		firstList.retainAll(secondList);
		for(String s: firstList){
			if (s.length()<4)
				continue;
			if (!StringUtils.isAlpha(s))
				continue;
			results.add(s);
		}
		return results;
	}

	public static void main(String[] args) {
		DomainTaxonomyExtender self = new DomainTaxonomyExtender();
		self.extendTaxonomy("", "music",
				"en");

	}

}
