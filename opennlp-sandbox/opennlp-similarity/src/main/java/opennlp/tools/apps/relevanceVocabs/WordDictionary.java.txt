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

package opennlp.tools.apps.relevanceVocabs;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.coref.mention.Dictionary;

public class WordDictionary {
	private static final String[][] SPECIAL_CASES = { { "lens", "lenses" } };

	//private static final String WORDNET_PROPERTITES_KEY = "wordnet.propertites.file";
	//private static final String PROPERTIES_FILE = null;;

	// private static final String DATA_DIR;
	private static WordDictionary instance;

	private Dictionary dictionary;
	private Map<String, String> specialCaseMap;

	/*static {
		ConfigProperties config = ConfigFactory.getInstance()
				.getConfigProperties(ConfigFactory.NLP_CONFIG_PATH);
		PROPERTIES_FILE = config.getProperty(WORDNET_PROPERTITES_KEY);
	}*/

	public synchronized static WordDictionary getInstance() {
		if (instance == null)
			instance = new WordDictionary();

		return instance;
	}

	private WordDictionary() {
		// initialize the dictionary by loading the WordNet database
		try {
			dictionary = new TopJWNLDictionary("PROPERTIES_FILE");
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Failed to load the WordNet database: " + e);
		}

		// build the dictionary for special cases
		specialCaseMap = buildSpecialCaseMap();
	}

	public String getLemmaOrWord(String word, String type) {
		String lemma = getLemma(word, type);
		if (lemma != null)
			return lemma;
		else
			return (word == null) ? null : word.trim().toLowerCase();
	}

	public String getLemma(String word, String type) {
		if (word == null)
			return null;
		// skip some long word,avoid dictionary getLemmas dead
		if (word.length() >= 20)
			return word;
		word = word.trim().toLowerCase();
		if (word.length() == 0)
			return null;

		// check special cases first
		String lemma = specialCaseMap.get(word);
		if (lemma != null)
			return lemma;

		// use the dictionary for general cases
		// JWNLDictionary has a bug, and we have to use lower case type
		type = (type == null) ? null : type.toLowerCase();
		String[] lemmas = dictionary.getLemmas(word, type);
		if (lemmas == null || lemmas.length == 0)
			return null;

		return lemmas[0];
	}

	/**
	 * get the lemma for a word of unknown POS type return the word if no lemma
	 * is found
	 * 
	 * @param word
	 * @return
	 */
	public String getLemmaOrWord(String word) {
		if (word == null)
			return null;

		// try noun first
		String lemma = getLemma(word, "NN");
		if (lemma != null)
			return lemma;

		// then try verb
		lemma = getLemma(word, "VB");
		if (lemma != null)
			return lemma;

		// return word now
		return word.trim().toLowerCase();
	}

	private Map<String, String> buildSpecialCaseMap() {

		Map<String, String> specialCaseMap = new HashMap<String, String>();
		for (String[] wordList : SPECIAL_CASES) {
			String lemma = wordList[0];
			for (String word : wordList) {
				specialCaseMap.put(word, lemma);
			}
		}

		return specialCaseMap;
	}

	public static void main(String[] args) {
		String[] verbs = { "is", "has", "were", "likes", "TaKen", "going" };
		String[] nouns = { "efficient", "Cars", "lens", "wives", "lenses",
				"photos" };
		String[] adverbs = { "would", "could", "should", "might" };
		WordDictionary dictionary = WordDictionary.getInstance();

		for (String word : verbs) {
			System.out
					.println(word + " ==> " + dictionary.getLemma(word, "VB"));
		}
		for (String word : nouns) {
			System.out
					.println(word + " ==> " + dictionary.getLemma(word, "NN"));
		}
		for (String word : adverbs) {
			System.out
					.println(word + " ==> " + dictionary.getLemma(word, "JJ"));
		}
	}
}
