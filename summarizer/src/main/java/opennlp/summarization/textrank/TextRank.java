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

import opennlp.summarization.DocProcessor;
import opennlp.summarization.Score;
import opennlp.summarization.preprocess.IDFWordWeight;
import opennlp.summarization.preprocess.StopWords;
import opennlp.summarization.preprocess.WordWeight;

/**
 * Implements the TextRank algorithm by Rada Mihalcea and Paul Tarau: <br/>
 * <a href="https://aclanthology.org/W04-3252/">TextRank: Bringing Order into Text</a>
 * <br/><br/>
 * This basically applies the page rank algorithm to a graph where each sentence is a node
 * and a connection between sentences indicates that a word is shared between them.
 * <p>
 * It returns a ranking of sentences where the highest rank means most important etc.
 * Currently, only stemming is done to the words; a more sophisticated way might use a
 * resource like Wordnet to match synonyms etc.
 */
public class TextRank {

  private static final int NO_OF_IT = 100;
  // DAMPING FACTOR..
  private static final double DF = 0.15;
  private static final boolean HIGHER_TITLE_WEIGHT = true;
  private static final double TITLE_WRD_WT = 2d;

  private final DocProcessor docProc;
  private final StopWords sw;
  private final WordWeight wordWt;

  private final double maxErr = 0.1;
  private final double title_wt = 0;

  private Hashtable<Integer, List<Integer>> links = new Hashtable<>();
  private List<String> sentences = new ArrayList<>();
  private List<String> processedSent = new ArrayList<>();

  /**
   * Instantiates a {@link TextRank} with the specified {@link DocProcessor}.
   *
   * @param dp A valid {@link DocProcessor}. Must not be {@code null}.
   *
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public TextRank(DocProcessor dp) {
    this(dp, new StopWords(), IDFWordWeight.getInstance("/idf.csv"));
  }

  /**
   * Instantiates a {@link TextRank} with the specified {@link DocProcessor}.
   *
   * @param dp A valid {@link DocProcessor}. Must not be {@code null}.
   * @param stopWords The {@link StopWords} instance to use. Must not be {@code null}.
   * @param wordWeights The {@link WordWeight} instance to use. Must not be {@code null}.
   *                    
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public TextRank(DocProcessor dp, StopWords stopWords, WordWeight wordWeights) {
    if (dp == null) throw new IllegalArgumentException("parameter 'dp' must not be null");
    if (stopWords == null) throw new IllegalArgumentException("parameter 'stopWords' must not be null");
    if (wordWeights == null) throw new IllegalArgumentException("parameter 'wordWeights' must not be null");
    this.docProc = dp;
    this.sw = stopWords;
    this.wordWt = wordWeights;
  }

  /**
   * Computes the similarity of two sentences.
   *
   * @param sent1 The first sentence. If {@code null} or empty the computation will result in {@code 0.0}.
   * @param sent2 The second sentence. If {@code null} or empty the computation will result in {@code 0.0}.
   * @param wrdWts The mapping table contains tf-idf of the words.
   * @return The computed similarity. If no similarity exist, the resulting value equals {@code 0.0}.
   */
  public double getWeightedSimilarity(String sent1, String sent2, Hashtable<String, Double> wrdWts) {

    String[] words1 = docProc.getWords(sent1);
    String[] words2 = docProc.getWords(sent2);
    double wordsInCommon = 0;
    Hashtable<String, Boolean> dups = new Hashtable<>();
    for (String s : words1) {
      String currWrd1 = s.trim();
      boolean emptyWrd1 = currWrd1.isEmpty();
      boolean stopWordWrd1 = sw.isStopWord(currWrd1);
      // skip over duplicate words of sentence
      if (dups.get(currWrd1) == null) {
        dups.put(currWrd1, true);
        for (String value : words2) {
          if (!stopWordWrd1 && !emptyWrd1 && s.equals(value)) {
            Double wt;

            wt = wrdWts.get(currWrd1);
            if (wt != null)
              wordsInCommon += wt;
            else
              wordsInCommon++;
          }
        }
      }
    }
    return (wordsInCommon) / (words1.length + words2.length);
  }

  /**
   * @param scores A list of {@link Score} instances.
   * @param id The sentence id to check for.
   * @return Gets the element from {@code scores} that matches the passed sentence {@code id}.
   */
  public double getScoreFrom(List<Score> scores, int id) {
    for (Score s : scores) {
      if (s.getSentId() == id)
        return s.getScore();
    }
    return 1; // Why is the default score "1" here?
  }

  // This method runs the page rank algorithm for the sentences.
  // TR(Vi) = (1-d) + d * sigma over neighbors Vj( wij/sigma over k neighbor
  // of j(wjk) * PR(Vj) )
  public List<Score> getTextRankScore(List<Score> rawScores,
                                      List<String> sentences, Hashtable<String, Double> wrdWts) {
    List<Score> currWtScores = new ArrayList<>();
    // Start with equal weights for all sentences
    for (int i = 0; i < rawScores.size(); i++) {
      Score ns = new Score(rawScores.get(i).getSentId(), (1 - title_wt) / (rawScores.size())); // this.getSimilarity();
      currWtScores.add(ns);
    }
    // currWtScores.get(0).score = this.title_wt;

    // Page rank..
    for (int i = 0; i < NO_OF_IT; i++) {
      double totErr = 0;
      List<Score> newWtScores = new ArrayList<>();

      // Update the scores for the current iteration..
      for (Score rs : rawScores) {
        int sentId = rs.getSentId();

        List<Integer> neighbors = getLinks().get(sentId);
        double sum = 0;
        if (neighbors != null) {
          for (Integer j : neighbors) {
            // sum += getCurrentScore(rawScores,
            // sentId)/(getCurrentScore(rawScores, neigh)) *
            // getCurrentScore(currWtScores, neigh);
            double wij = this.getWeightedSimilarity(sentences.get(sentId), sentences.get(j), wrdWts);
            double sigmawjk = getScoreFrom(rawScores, j);
            double txtRnkj = getScoreFrom(currWtScores, j);
            sum += wij / sigmawjk * txtRnkj;
          }
        }
        Score ns = new Score(sentId, (1d - DF) + sum * DF); // * rs.score
        totErr += ns.getScore() - getScoreFrom(rawScores, sentId);
        newWtScores.add(ns);
      }
      currWtScores = newWtScores;
      if (i > 2 && totErr / rawScores.size() < maxErr)
        break;
    }

    for (Score s : currWtScores) {
      s.setScore(s.getScore() * getScoreFrom(rawScores, s.getSentId()));
    }
    return currWtScores;
  }

  // Raw score is sigma wtsimilarity of neighbors.
  // Used in the denominator of the Text rank formula.
  public List<Score> getNeighborsSigmaWtSim(List<String> sentences,
                                            Hashtable<String, List<Integer>> iidx, Hashtable<String, Double> wts) {
    List<Score> allScores = new ArrayList<>();

    for (int i = 0; i < sentences.size(); i++) {
      String nextSent = sentences.get(i);
      String[] words = docProc.getWords(nextSent);
      Score s = new Score(i, 0d);

      for (String word : words) {
        String currWrd = docProc.getStemmer().stem(word).toString(); //stemmer.toString();

        List<Integer> otherSents = iidx.get(currWrd);
        if (otherSents == null)
          continue;

        List<Integer> processed = new ArrayList<>();
        for (int idx : otherSents) {
          if (idx != i && !processed.contains(idx)) {
            double currS = getWeightedSimilarity(sentences.get(i), sentences.get(idx), wts);
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
    List<Score> weightedScores = this.getTextRankScore(rawScores, sentences, wordWts);
    Collections.sort(weightedScores);
    return weightedScores;
  }

  private Hashtable<String, Double> toWordWtHashtable(WordWeight wwt,
                                                      Hashtable<String, List<Integer>> iidx) {
    Hashtable<String, Double> wrdWt = new Hashtable<>();
    Enumeration<String> keys = iidx.keys();
    while (keys.hasMoreElements()) {
      String key = keys.nextElement();
      wrdWt.put(key, wwt.getWordWeight(key));
    }
    return wrdWt;
  }

  public List<Score> getRankedSentences(List<String> sentences,
                                        Hashtable<String, List<Integer>> iidx, List<String> processedSent) {
    this.sentences = sentences;
    this.processedSent = processedSent;

    Hashtable<String, Double> wrdWts = toWordWtHashtable(this.wordWt, iidx); // new

    if (HIGHER_TITLE_WEIGHT && !getSentences().isEmpty()) {
      String sent = getSentences().get(0);
      String[] wrds = sent.trim().split("\\s+");
      for (String wrd : wrds)
        wrdWts.put(wrd, TITLE_WRD_WT);
    }

    List<Score> rawScores = getNeighborsSigmaWtSim(getSentences(), iidx, wrdWts);
    return getWeightedScores(rawScores, getSentences(), wrdWts);
  }

  // Set a link between two sentences..
  private void addLink(int i, int idx) {
    List<Integer> endNodes = getLinks().get(i);
    if (endNodes == null)
      endNodes = new ArrayList<>();
    endNodes.add(idx);
    getLinks().put(i, endNodes);
  }

  public List<String> getSentences() {
    return sentences;
  }

  public void setSentences(List<String> sentences) {
    this.sentences = sentences;
  }

  public Hashtable<Integer, List<Integer>> getLinks() {
    return links;
  }

  private void setLinks(Hashtable<Integer, List<Integer>> links) {
    this.links = links;
  }

}
