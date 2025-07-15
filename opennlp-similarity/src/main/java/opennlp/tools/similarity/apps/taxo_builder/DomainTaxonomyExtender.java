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

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.StringCleaner;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class DomainTaxonomyExtender {

	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	final static String TAXO_FILENAME = "taxo_data.dat";

	private final Map<String, List<List<String>>> lemma_ExtendedAssocWords = new HashMap<>();
	private final Map<List<String>, List<List<String>>> assocWords_ExtendedAssocWords = new HashMap<>();
	private final Stemmer ps = new PorterStemmer();

	private ParserChunker2MatcherProcessor matcher;
	private CsvAdapter adapter;

	public DomainTaxonomyExtender(String fileName) {
    try {
			// XXX eliminate hard-coded resource path
      String resourceDir = new File(".").getCanonicalPath() + "/src/test/resources";
			adapter = new CsvAdapter(resourceDir, fileName);
    } catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
    }
	}

	private List<List<String>> getCommonWordsFromList_List_ParseTreeChunk(List<List<ParseTreeChunk>> matchList,
																																				List<String> queryWordsToRemove,
																																				List<String> toAddAtEnd) {
		List<List<String>> res = new ArrayList<>();
		for (List<ParseTreeChunk> chunks : matchList) {
			List<String> wordRes = new ArrayList<>();
			for (ParseTreeChunk ch : chunks) {
				List<String> lemmas = ch.getLemmas();
				for (int w = 0; w < lemmas.size(); w++)
					if (!lemmas.get(w).equals("*")
							&& (ch.getPOSs().get(w).startsWith("NN") || ch.getPOSs().get(w).startsWith("JJ") || ch.getPOSs().get(w)
									.startsWith("VB")) && lemmas.get(w).length() > 2) {
						String formedWord = lemmas.get(w);
						String stemmedFormedWord = ps.stem(formedWord).toString();
						if (!stemmedFormedWord.startsWith("invalid"))
							wordRes.add(formedWord);
					}
			}
			wordRes = new ArrayList<>(new HashSet<>(wordRes));
			List<String> cleanedRes = new ArrayList<>();
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
		res = new ArrayList<>(new HashSet<>(res));
		return res;
	}

	public void extendTaxonomy(String domain, String lang, Path outputDir) {
		List<String> entries = new ArrayList<>(adapter.getLemmaAssocWords().keySet());
		try {
			for (String entity : entries) {
				List<List<String>> paths = adapter.getLemmaAssocWords().get(entity);
				for (List<String> taxoPath : paths) {
					String query = taxoPath.toString() + " " + entity + " " + domain; 
					query = query.replace('[', ' ').replace(']', ' ').replace(',', ' ')
							.replace('_', ' ');
					List<List<ParseTreeChunk>> matchList = runSearchForTaxonomyPath(query, "", lang, 20); //30
					List<String> toRemove = new ArrayList<>(taxoPath);
					toRemove.add(entity);
					toRemove.add(domain);
					List<List<String>> resList = getCommonWordsFromList_List_ParseTreeChunk(matchList, toRemove, taxoPath);
					assocWords_ExtendedAssocWords.put(taxoPath, resList);
					resList.add(taxoPath);
					lemma_ExtendedAssocWords.put(entity, resList);

					TaxonomySerializer ser = new TaxonomySerializer(lemma_ExtendedAssocWords, assocWords_ExtendedAssocWords);
					ser.writeTaxonomy(outputDir, TAXO_FILENAME);
				}
			}
		} catch (Exception e) {
			LOG.error("Problem taxonomy matching!", e);
		}
	}

	public List<List<ParseTreeChunk>> runSearchForTaxonomyPath(String query, String domain, String lang, int numbOfHits) {
		List<List<ParseTreeChunk>> genResult = new ArrayList<>();
		try {
			List<HitBase> resultList = new ArrayList<>(); // brunner.runSearch(query, numbOfHits);
			for (int i = 0; i < resultList.size(); i++) {
				for (int j = i + 1; j < resultList.size(); j++) {
					HitBase h1 = resultList.get(i);
					HitBase h2 = resultList.get(j);
					String snapshot1 = StringCleaner.processSnapshotForMatching(h1.getTitle() + " " + h1.getAbstractText());
					String snapshot2 = StringCleaner.processSnapshotForMatching(h2.getTitle() + " " + h2.getAbstractText());
					SentencePairMatchResult overlaps = getMatcher().assessRelevance(snapshot1, snapshot2);
					genResult.addAll(overlaps.getMatchResult());
				}
			}
		} catch (Exception e) {
			LOG.error("Problem searching for '{}'", query, e);
		}
		return genResult;
	}

	private ParserChunker2MatcherProcessor getMatcher() {
		if (matcher == null) {
			matcher = ParserChunker2MatcherProcessor.getInstance();
		}
		return matcher;
	}

	public List<String> runSearchForTaxonomyPathFlatten(String query, String domain, String lang, int numbOfHits) {
		List<String> genResult = new ArrayList<>();
		try {
			List<HitBase> resultList = new ArrayList<>(); // brunner.runSearch(query, numbOfHits);
			for (int i = 0; i < resultList.size(); i++) {
				for (int j = i + 1; j < resultList.size(); j++) {
					HitBase h1 = resultList.get(i);
					HitBase h2 = resultList.get(j);
					String snapshot1 = StringCleaner.processSnapshotForMatching(h1.getTitle() + " " + h1.getAbstractText());
					String snapshot2 = StringCleaner.processSnapshotForMatching(h2.getTitle() + " " + h2.getAbstractText());
					List<String> overlaps =assessKeywordOverlap(snapshot1, snapshot2);
					genResult.addAll(overlaps);
				}
			}
		} catch (Exception e) {
			LOG.error("Problem searching for '{}'", query, e);
		}
		return genResult;
	}

	private List<String> assessKeywordOverlap(String snapshot1, String snapshot2) {
		List<String> results = new ArrayList<>();
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

}
