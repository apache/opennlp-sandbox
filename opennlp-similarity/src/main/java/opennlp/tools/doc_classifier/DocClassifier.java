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
package opennlp.tools.doc_classifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.similarity.apps.utils.CountItemsList;
import opennlp.tools.similarity.apps.utils.ValueSortMap;
import opennlp.tools.textsimilarity.TextProcessor;

public class DocClassifier {

	private static final Logger LOGGER = LoggerFactory.getLogger(DocClassifier.class);
	public static final String DOC_CLASSIFIER_KEY = "doc_class";
	public static final String RESOURCE_DIR = null;
	private Map<String, Float> scoredClasses;
	
	public static final Float MIN_TOTAL_SCORE_FOR_CATEGORY = 0.3f; //3.0f;
	protected static IndexReader indexReader = null;
	protected static IndexSearcher indexSearcher = null;
	// resource directory plus the index folder
	private static final String INDEX_PATH = RESOURCE_DIR + ClassifierTrainingSetIndexer.INDEX_PATH;

	// http://en.wikipedia.org/wiki/K-nearest_neighbors_algorithm
	private static final int MAX_DOCS_TO_USE_FOR_CLASSIFY = 10, // 10 similar
	// docs for nearest neighbor settings
	MAX_CATEG_RESULTS = 2;
	private static final float BEST_TO_NEX_BEST_RATIO = 2.0f;
	// to accumulate classif results
	private final CountItemsList<String> localCats = new CountItemsList<>();
	private static final int MAX_TOKENS_TO_FORM = 30;
	private final String CAT_COMPUTING = "computing";
	public static final String DOC_CLASSIFIER_MAP = "doc_classifier_map";
	private static final int MIN_SENTENCE_LENGTH_TO_CATEGORIZE = 60; // if
	// sentence
	// is
	// shorter,
	// should
	// not
	// be
	// used
	// for
	// classification
	private static final int MIN_CHARS_IN_QUERY = 30; // if combination of
	// keywords are shorter,
	// should not be used
	// for classification

	// these are categories from the index
	public static final String[] CATEGORIES = new String[]
					{ "legal", "health", "finance", "computing", "engineering", "business" };

	static {
		synchronized (DocClassifier.class) {
			Directory indexDirectory = null;

			try {
				indexDirectory = FSDirectory.open(new File(INDEX_PATH).toPath());
			} catch (IOException e2) {
				LOGGER.error("problem opening index " + e2);
			}
			try {
				indexReader = DirectoryReader.open(indexDirectory);
				indexSearcher = new IndexSearcher(indexReader);
			} catch (IOException e2) {
				LOGGER.error("problem reading index \n" + e2);
			}
		}
	}

	public DocClassifier(String inputFilename) {
		scoredClasses = new HashMap<>();
	}

	/* returns the class name for a sentence */
	private List<String> classifySentence(String queryStr) {

		List<String> results = new ArrayList<>();
		// too short of a query
		if (queryStr.length() < MIN_CHARS_IN_QUERY) {
			return results;
		}

		Analyzer std = new StandardAnalyzer();
		QueryParser parser = new QueryParser("text", std);
		parser.setDefaultOperator(QueryParser.Operator.OR);
		Query query;
		try {
			query = parser.parse(queryStr);
		} catch (ParseException e2) {
			return results;
		}
		TopDocs hits = null; // TopDocs search(Query, int)
		// Finds the top n hits for query.
		try {
			hits = indexSearcher.search(query, MAX_DOCS_TO_USE_FOR_CLASSIFY + 2);
		} catch (IOException e1) {
			LOGGER.error("problem searching index \n", e1);
		}
		LOGGER.debug("Found " + hits.totalHits + " hits for " + queryStr);
		int count = 0;
		

		for (ScoreDoc scoreDoc : hits.scoreDocs) {
			Document doc;
			try {
				doc = indexSearcher.doc(scoreDoc.doc);
			} catch (IOException e) {
				LOGGER.error("Problem searching training set for classif \n"
						+ e);
				continue;
			}
			String flag = doc.get("class");

			Float scoreForClass = scoredClasses.get(flag);
			if (scoreForClass == null)
				scoredClasses.put(flag, scoreDoc.score);
			else
				scoredClasses.put(flag, scoreForClass + scoreDoc.score);

			LOGGER.debug(" <<categorized as>> " + flag + " | score="
					+ scoreDoc.score + " \n text =" + doc.get("text") + "\n");

			if (count > MAX_DOCS_TO_USE_FOR_CLASSIFY) {
				break;
			}
			count++;
		}
		try {
			scoredClasses = ValueSortMap.sortMapByValue(scoredClasses, false);
			List<String> resultsAll = new ArrayList<>(scoredClasses.keySet()), resultsAboveThresh = new ArrayList<>();
			for (String key : resultsAll) {
				if (scoredClasses.get(key) > MIN_TOTAL_SCORE_FOR_CATEGORY)
					resultsAboveThresh.add(key);
				else
					LOGGER.debug("Too low score of " + scoredClasses.get(key)
							+ " for category = " + key);
			}

			int len = resultsAboveThresh.size();
			if (len > MAX_CATEG_RESULTS)
				results = resultsAboveThresh.subList(0, MAX_CATEG_RESULTS); // get
			// maxRes
			// elements
			else
				results = resultsAboveThresh;
		} catch (Exception e) {
			LOGGER.error("Problem aggregating search results\n" + e);
		}
		if (results.size() < 2)
			return results;

		// if two categories, one is very high and another is relatively low
		if (scoredClasses.get(results.get(0))
				/ scoredClasses.get(results.get(1)) > BEST_TO_NEX_BEST_RATIO) // second
			// best
			// is
			// much
			// worse
			return results.subList(0, 1);
		else
			return results;

	}

	public static String formClassifQuery(String pageContentReader, int maxRes) {

		// We want to control which delimiters we substitute. For example '_' &
		// \n we retain
		pageContentReader = pageContentReader.replaceAll("[^A-Za-z0-9 _\\n]", "");

		Scanner in = new Scanner(pageContentReader);
		in.useDelimiter("\\s+");
		Map<String, Integer> words = new HashMap<>();

		while (in.hasNext()) {
			String word = in.next();
			if (!StringUtils.isAlpha(word) || word.length() < 4)
				continue;

			if (!words.containsKey(word)) {
				words.put(word, 1);
			} else {
				words.put(word, words.get(word) + 1);
			}
		}
		in.close();
		words = ValueSortMap.sortMapByValue(words, false);
		List<String> resultsAll = new ArrayList<>(words.keySet()), results;

		int len = resultsAll.size();
		if (len > maxRes)
			results = resultsAll.subList(len - maxRes, len - 1); // get maxRes
			// elements
			else
				results = resultsAll;

		return results.toString().replaceAll("(\\[|\\]|,)", " ").trim();
	}

	public void close() {
		try {
			indexReader.close();
		} catch (IOException e) {
			LOGGER.error("Problem closing index \n" + e);
		}
	}	
	
	/*
	 * Main entry point for classifying sentences
	 */
	public List<String> getEntityOrClassFromText(String content) {

		List<String> sentences = TextProcessor.splitToSentences(content);
		List<String> classifResults;

		try {
			for (String sentence : sentences) {
				// If sentence is too short, there is a chance it is not form a
				// main text area,
				// but from somewhere else, so it is safer not to use this
				// portion of text for classification

				if (sentence.length() < MIN_SENTENCE_LENGTH_TO_CATEGORIZE)
					continue;
				String query = formClassifQuery(sentence, MAX_TOKENS_TO_FORM);
				classifResults = classifySentence(query);
				if (classifResults != null && classifResults.size() > 0) {
					localCats.addAll(classifResults);
					LOGGER.debug(sentence + " =>  " + classifResults);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Problem classifying sentence\n " + e);
		}
		
		List<String> aggrResults = new ArrayList<>();
		try {

			aggrResults = localCats.getFrequentTags();

			LOGGER.debug(localCats.getFrequentTags().toString());
		} catch (Exception e) {
			LOGGER.error("Problem aggregating search results\n", e);
		}
		return aggrResults;
	}
}
