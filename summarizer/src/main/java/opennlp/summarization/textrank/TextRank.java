/*
 	* Licensed to the Apache Software Foundation (ASF) under one or more
 	* contributor license agreements. See the NOTICE file distributed with
 	* this work for additional information regarding copyright ownership.
 	* The ASF licenses this file to You under the Apache License, Version 2.0
 	* (the "License"); you may not use this file except in compliance with
 	* the License. You may obtain a copy of the License at
 	*
 	* http://www.apache.org/licenses/LICENSE-2.0
 	*
 	* Unless required by applicable law or agreed to in writing, software
 	* distributed under the License is distributed on an "AS IS" BASIS,
 	* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 	* See the License for the specific language governing permissions and
 	* limitations under the License.
*/

package opennlp.summarization.textrank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import opennlp.summarization.*;
import opennlp.summarization.preprocess.DefaultDocProcessor;
import opennlp.summarization.preprocess.IDFWordWeight;
import opennlp.summarization.preprocess.PorterStemmer;
import opennlp.summarization.preprocess.StopWords;
import opennlp.summarization.preprocess.WordWeight;

/*
 * Implements the TextRank algorithm by Mihalcea et al. 
 * This basically applies the page rank algorithm to a graph where each sentence is a node and a connection between sentences
 * indicates that a word is shared between them. It returns a ranking of sentences where highest rank means most important etc.
 * Currently only stemming is done to the words - a more sophisticated way might use a resource like Wordnet to match synonyms etc.  
 */
public class TextRank {
	private StopWords sw;
	private String article;
	private Hashtable<Integer, List<Integer>> links;
	private List<String> sentences = new ArrayList<String>();
	private List<String> processedSent = new ArrayList<String>();
	private WordWeight wordWt;
	private int NO_OF_IT = 100;
	private double maxErr = 0.1;
	private DocProcessor docProc;

	private double title_wt = 0;
	private Hashtable<Integer, String[]> wordsInSent;

	// DAMPING FACTOR..
	private static double df = 0.15;
	private boolean HIGHER_TITLE_WEIGHT = true;
	private static double TITLE_WRD_WT = 2d;
	private String resources = "./resources";

	public TextRank(DocProcessor dp) {
		sw = new StopWords();
		setLinks(new Hashtable<Integer, List<Integer>>());
		processedSent = new ArrayList<String>();
		docProc = dp;
		wordWt = IDFWordWeight.getInstance(resources + "/idf.csv");
	}

	public TextRank(StopWords sw, WordWeight wordWts) {
		this.sw = sw;
		this.wordWt = wordWts;
	}

	// Returns similarity of two sentences. Wrd wts contains tf-idf of the
	// words..
	public double getWeightedSimilarity(String sent1, String sent2,
			Hashtable<String, Double> wrdWts) {
		String[] words1 = sent1.split(" ");
		String[] words2 = sent2.split(" ");
		double wordsInCommon = 0;
		Hashtable<String, Boolean> dups = new Hashtable<String, Boolean>();
		for (int i = 0; i < words1.length; i++) {
			String currWrd1 = words1[i].trim();
			// skip over duplicate words of sentence
			if (dups.get(currWrd1) == null) {
				dups.put(currWrd1, true);
				for (int j = 0; j < words2.length; j++) {
					if (!sw.isStopWord(currWrd1) && !currWrd1.isEmpty()
							&& words1[i].equals(words2[j])) {
						Double wt;

						wt = wrdWts.get(currWrd1);
						if (wt != null)
							wordsInCommon += wt.doubleValue();
						else
							wordsInCommon++;
					}
				}
			}
		}
		return ((double) ((wordsInCommon)))
				/  (words1.length  +  words2.length);
	}

	// Gets the current score from the list of scores passed ...
	public double getScoreFrom(List<Score> scores, int id) {
		for (Score s : scores) {
			if (s.getSentId() == id)
				return s.getScore();
		}
		return 1;
	}

	// This method runs the page rank algorithm for the sentences.
	// TR(Vi) = (1-d) + d * sigma over neighbors Vj( wij/sigma over k neighbor
	// of j(wjk) * PR(Vj) )
	public List<Score> getTextRankScore(List<Score> rawScores,
			List<String> sentences, Hashtable<String, Double> wrdWts) {
		List<Score> currWtScores = new ArrayList<Score>();
		// Start with equal weights for all sentences
		for (int i = 0; i < rawScores.size(); i++) {
			Score ns = new Score();
			ns.setSentId(rawScores.get(i).getSentId());
			ns.setScore((1 - title_wt) / (rawScores.size()));// this.getSimilarity();
			currWtScores.add(ns);
		}
		// currWtScores.get(0).score = this.title_wt;

		// Page rank..
		for (int i = 0; i < NO_OF_IT; i++) {
			double totErr = 0;
			List<Score> newWtScores = new ArrayList<Score>();

			// Update the scores for the current iteration..
			for (Score rs : rawScores) {
				int sentId = rs.getSentId();
				Score ns = new Score();
				ns.setSentId(sentId);

				List<Integer> neighbors = getLinks().get(sentId);
				double sum = 0;
				if (neighbors != null) {
					for (Integer j : neighbors) {
						// sum += getCurrentScore(rawScores,
						// sentId)/(getCurrentScore(rawScores, neigh)) *
						// getCurrentScore(currWtScores, neigh);
						double wij = this.getWeightedSimilarity(sentences
								.get(sentId), sentences.get(j), wrdWts);
						double sigmawjk = getScoreFrom(rawScores, j);
						double txtRnkj = getScoreFrom(currWtScores, j);
						sum += wij / sigmawjk * txtRnkj;
					}
				}
				ns.setScore((1d - df) + sum * df);// * rs.score
				totErr += ns.getScore() - getScoreFrom(rawScores, sentId);
				newWtScores.add(ns);
			}
			currWtScores = newWtScores;
			if (i > 2 && totErr / rawScores.size() < maxErr)
				break;
		}

		for (int i = 0; i < currWtScores.size(); i++) {
			Score s = currWtScores.get(i);
			s.setScore(s.getScore() * getScoreFrom(rawScores, s.getSentId()));
		}
		return currWtScores;
	}

	// Raw score is sigma wtsimilarity of neighbors..
	// Used in the denominator of the Text rank formula..
	public List<Score> getNeighborsSigmaWtSim(List<String> sentences,
			Hashtable<String, List<Integer>> iidx, Hashtable<String, Double> wts) {
		List<Score> allScores = new ArrayList<Score>();

		for (int i = 0; i < sentences.size(); i++) {
			String nextSent = sentences.get(i);
			String[] words = nextSent.split(" ");
			List<Integer> processed = new ArrayList<Integer>();
			Score s = new Score();
			s.setSentId(i);

			for (int j = 0; j < words.length; j++) {
				String currWrd = docProc.getStemmer().stem(words[j]).toString();//stemmer.toString();
				
				List<Integer> otherSents = iidx.get(currWrd);
				if (otherSents == null)
					continue;

				for (int k = 0; k < otherSents.size(); k++) {
					int idx = otherSents.get(k);

					if (idx != i && !processed.contains(idx)) {
						double currS = getWeightedSimilarity(sentences.get(i),
								sentences.get(idx), wts);
						s.setScore(s.getScore() + currS);

						if (currS > 0) {
							addLink(i, idx);
						}
						processed.add(idx);
					}
				}
			}
			allScores.add(s);
		}
		return allScores;
	}

	public List<Score> getWeightedScores(List<Score> rawScores,
			List<String> sentences, Hashtable<String, Double> wordWts) {
		List<Score> weightedScores = this.getTextRankScore(rawScores,
				sentences, wordWts);
		Collections.sort(weightedScores);
		return weightedScores;
	}

	private Hashtable<String, Double> toWordWtHashtable(WordWeight wwt,
			Hashtable<String, List<Integer>> iidx) {
		Hashtable<String, Double> wrdWt = new Hashtable<String, Double>();
		Enumeration<String> keys = iidx.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			wrdWt.put(key, wwt.getWordWeight(key));
		}
		return wrdWt;
	}

	public List<Score> getRankedSentences(String doc, List<String> sentences,
			Hashtable<String, List<Integer>> iidx, List<String> processedSent) {
		this.sentences = sentences;
		this.processedSent = processedSent;

		List<Integer> chosenOnes = new ArrayList<Integer>();

		Hashtable<String, Double> wrdWts = toWordWtHashtable(this.wordWt, iidx);// new
																				// Hashtable<String,
																				// Double>();

		if (HIGHER_TITLE_WEIGHT && getSentences().size()>0) {
			String sent = getSentences().get(0);
			String[] wrds = sent.split(" ");
			for (String wrd : wrds)
				wrdWts.put(wrd, new Double(TITLE_WRD_WT));
		}

		List<Score> rawScores = getNeighborsSigmaWtSim(getSentences(), iidx,
				wrdWts);
		List<Score> finalScores = getWeightedScores(rawScores, getSentences(),
				wrdWts);

		Score bestScr = null;
		int next = 0;

		return finalScores;
	}

	// Set a link between two sentences..
	private void addLink(int i, int idx) {
		List<Integer> endNodes = getLinks().get(i);
		if (endNodes == null)
			endNodes = new ArrayList<Integer>();
		endNodes.add(idx);
		getLinks().put(i, endNodes);
	}

	public void setSentences(List<String> sentences) {
		this.sentences = sentences;
	}

	public List<String> getSentences() {
		return sentences;
	}

	public void setArticle(String article) {
		this.article = article;
	}

	public String getArticle() {
		return article;
	}

	private void setLinks(Hashtable<Integer, List<Integer>> links) {
		this.links = links;
	}

	public Hashtable<Integer, List<Integer>> getLinks() {
		return links;
	}
}

/*
 * public double getScore(String sent1, String sent2, boolean toPrint) {
 * String[] words1 = sent1.split(" "); String[] words2 = sent2.split(" ");
 * double wordsInCommon = 0; for(int i=0;i< words1.length;i++) { for(int
 * j=0;j<words2.length;j++) { if(!sw.isStopWord(words1[i]) &&
 * !words1[i].trim().isEmpty() && words1[i].equals(words2[j])) { wordsInCommon+=
 * wordWt.getWordWeight(words1[i]); } } } return ((double)wordsInCommon) /
 * (Math.log(1+words1.length) + Math.log(1+words2.length)); }
 */