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
import java.util.HashSet;
import java.util.Map;


public class SentimentVocab {
	private static final String[] POSITIVE_ADJECTTIVE_LIST = { "accessible",
			"advanced", "affordable", "amazing", "awesome", "beautiful",
			"brilliant", "capable", "classic", "clear", "comfortable",
			"convenient", "cool", "courteous", "cute", "decent", "delight",
			"easy", "elegant", "enjoyable", "enough", "excellent",
			"exceptional", "fabulous", "fancy", "fantastic", "fast",
			"favorable", "fine", "friendly", "fun", "good", "great", "handy",
			"happy", "hefty", "helpful", "high", "immaculate", "impressive",
			"incredible", "interesting", "jealous", "lovely", "lucky",
			"luxurious", "marvelous", "maximum", "memorable", "neat", "nice",
			"outstanding", "perfect", "pleasant", "positive", "pretty",
			"powerful", "quiet", "reasonable", "remarkable", "right", "safe",
			"silky", "sleek", "slick", "stylish", "suitable", "superb",
			"tasteful", "terrific", "top", "unbelievable", "useful",
			"welcoming", "wonderful", "worthwhile" };

	private static final String[] NEGATIVE_ADJECTTIVE_LIST = { "angry",
			"annoyed", "annoying", "anxious", "arrogant", "ashamed", "awful",
			"bad", "bored", "boring", "broke", "broken", "clumsy",
			"complicate", "complicated", "confused", "cranky", "crazy",
			"cumbersome", "defective", "depressed", "dead", "depressing",
			"difficult", "dirty", "disappointed", "disappointing", "disgusted",
			"disgusting", "disheartened", "disheartening", "dissatisfactory",
			"dissatisfying", "distant", "disturbed", "dizzy", "doubtful",
			"down", "drab", "dull", "dysfunctional", "embarrassed", "evil",
			"exhausted", "fatal", "filthy", "flawed", "fragile", "frightened",
			"frustrating", "goofy", "grieving", "hard", "horrific",
			"horrifying", "harsh", "horrible", "impossible", "inconvenient",
			"insane", "lack", "lacking", "lazy", "leaking", "leaky", "lonely",
			"low", "mediocre", "messy", "mysterious", "nasty", "naughty",
			"negative", "noisy", "nonclean", "nutty", "outdated", "outrageous",
			"over priced", "pathetic", "poor", "premature", "pricey", "pricy",
			"problematic", "putrid", "puzzled", "rickety", "ridiculous",
			"ripped off", "rugged", "slow", "stinky", "strange", "stupid",
			"sweaty", "tedious", "terrible", "tired", "tough", "toxic",
			"trubled", "ugly", "unbearable", "unclean", "uncomfortable",
			"unfortunate", "unhelpful", "uninviting", "unpleasent",
			"unsanitary", "upseting", "unusable", "weird", "worn", "worn down",
			"wretched", "wrong" };

	private static final String[] POSITIVE_ADVERB_LIST = { "absolutely",
			"amazingly", "completely", "definitely", "easily", "fairly",
			"highly", "immensely", "incredibly", "nicely", "really", "rich",
			"simply", "surprisingly", "tastefully", "totally", "truly", "very",
			"well" };

	private static final String[] NEGATIVE_ADVERB_LIST = { "badly",
			"deceptfully", "down", "horribly", "oddly", "pathetically",
			"terribly", "too", "unfortunately" };

	private static final String[] POSITIVE_NOUN_LIST = { "ability", "benefit",
			"character", "charm", "comfort", "discount", "dream", "elegance",
			"favourite", "feature", "improvement", "luck", "luxury", "offer",
			 "quality", "requirement", "usability" };

	private static final String[] NEGATIVE_NOUN_LIST = { "blocker",
			"challenge", "complain", "complaint", "compromise", "con",
			"concern", "crap", "disappointment", "disillusion", "doubt",
			"downside", "drawback", "embarrassment", "error", "failure",
			"fault", "garbage", "glitch", "inability", "issue", "junk",
			"long line", "malfunction", "mess", "mistake", "nightmare",
			"noise", "odor", "pain", "pitfall", "problem", "rip off", "roach",
			"rude", "sacrifice", "shame", "shock", "stain", "threat",
			"trouble", "urine", "worry" };

	private static final String[] POSITIVE_VERB_LIST = { "admire", "amaze",
			"assist", "disgust", "enjoy", "help", "guarantee", "impress",
			"improve", "like", "love", "patronize", "prefer", "recommend",
			"want" };

	private static final String[] NEGATIVE_VERB_LIST = { "annoy", "appall",
			"break", "complain", "confuse", "depress", "disappoint",
			"dishearten", "dislike", "dissatisfy", "embarrass", "fail", "fear",
			"flaw", "frustrate", "hate", "ruin", "scare", "stink", "suck",
			"think twice", "thwart", "upset", "vomit" };

	public static final int SENTIMENT_POSITIVE = 1;
	public static final int SENTIMENT_UNKNOWN = 0;
	public static final int SENTIMENT_NEGATIVE = -1;

	private static SentimentVocab instance = new SentimentVocab();

	// complete sentiment word map, key = word, value = sentiment object
	private Map<String, Sentiment> sentimentMap = new HashMap<String, Sentiment>();

	// sentiment word sets, key = POS type, value = word set
	private Map<String, HashSet<String>> wordSetMap = new HashMap<String, HashSet<String>>();

	public static class Sentiment {
		public String posType;
		public int sentimentType;

		Sentiment(String posType, int sentimentType) {
			this.posType = posType;
			this.sentimentType = sentimentType;
		}
	}

	public static SentimentVocab getInstance() {
		return instance;
	}

	public Sentiment getSentiment(String word) {
		if (word == null)
			return null;

		// get the normalized form of the word
		//word = WordDictionary.getInstance().getLemmaOrWord(word);

		return sentimentMap.get(word);
	}

	public Sentiment getSentiment(String word, String posType) {
		if (word == null)
			return null;

		// get the normalized form of the word
		//word = WordDictionary.getInstance().getLemmaOrWord(word, posType);

		return sentimentMap.get(word);
	}

	public boolean isSentimentWord(String word) {
		return (getSentiment(word) != null);
	}

	public boolean isSentimentWord(String word, String posType) {
		Sentiment sentiment = getSentiment(word, posType);
		if (sentiment == null)
			return false;

		return sentiment.posType == posType;
	}

	public HashSet<String> getSentimentWordSet(String posType) {
		if (posType == null)
			return null;

		return wordSetMap.get(posType);
	}

	public static String getSentimentName(int sentimentType) {
		switch (sentimentType) {
		case SENTIMENT_POSITIVE:
			return "positive";
		case SENTIMENT_NEGATIVE:
			return "negative";
		default:
			return "unknown";
		}
	}

	private SentimentVocab() {
		// populate the sentiment map
		addWordsToSentimentMap(POSITIVE_ADJECTTIVE_LIST,
				POStags.TYPE_JJ, SENTIMENT_POSITIVE);
		addWordsToSentimentMap(NEGATIVE_ADJECTTIVE_LIST,
				POStags.TYPE_JJ, SENTIMENT_NEGATIVE);
		addWordsToSentimentMap(POSITIVE_ADVERB_LIST, POStags.TYPE_RB,
				SENTIMENT_POSITIVE);
		addWordsToSentimentMap(NEGATIVE_ADVERB_LIST, POStags.TYPE_RB,
				SENTIMENT_NEGATIVE);
		addWordsToSentimentMap(POSITIVE_NOUN_LIST, POStags.TYPE_NN,
				SENTIMENT_POSITIVE);
		addWordsToSentimentMap(NEGATIVE_NOUN_LIST, POStags.TYPE_NN,
				SENTIMENT_NEGATIVE);
		addWordsToSentimentMap(POSITIVE_VERB_LIST, POStags.TYPE_VB,
				SENTIMENT_POSITIVE);
		addWordsToSentimentMap(NEGATIVE_VERB_LIST, POStags.TYPE_VB,
				SENTIMENT_NEGATIVE);
	}

	private void addWordsToSentimentMap(String[] words, String posType,
			int sentimentType) {

		// add the word to the complete sentiment word map
		for (String word : words) {
			sentimentMap.put(word, new Sentiment(posType, sentimentType));
		}

		// add the word to the corresponding sentiment word set
		HashSet<String> wordSet = wordSetMap.get(posType);
		if (wordSet == null) {
			wordSet = new HashSet<String>();
			wordSetMap.put(posType, wordSet);
		}
		for (String word : words) {
			wordSet.add(word);
		}
	}
}
