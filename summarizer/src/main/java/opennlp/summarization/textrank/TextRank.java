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
 * Implements the TextRank algorithm by Mihalcea et al.
 * <p>
 * This basically applies the page rank algorithm to a graph where each sentence is a node
 * and a connection between sentences indicates that a word is shared between them.
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
  private final StopWords sw;
  private final WordWeight wordWt;
  private final double maxErr = 0.1;
  private final double title_wt = 0;
  private String article;
  private Hashtable<Integer, List<Integer>> links;
  private List<String> sentences = new ArrayList<>();
  private List<String> processedSent = new ArrayList<>();
  private DocProcessor docProc;

  public TextRank(DocProcessor dp) {
    sw = new StopWords();
    setLinks(new Hashtable<>());
    processedSent = new ArrayList<>();
    docProc = dp;
    wordWt = IDFWordWeight.getInstance("/meta/idf.csv");
  }

  public TextRank(StopWords sw, WordWeight wordWts) {
    this.sw = sw;
    this.wordWt = wordWts;
  }

  // Returns similarity of two sentences. Wrd wts contains tf-idf of the words..
  public double getWeightedSimilarity(String sent1, String sent2,
                                      Hashtable<String, Double> wrdWts) {
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
    List<Score> currWtScores = new ArrayList<>();
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
      List<Score> newWtScores = new ArrayList<>();

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
            double wij = this.getWeightedSimilarity(sentences.get(sentId), sentences.get(j), wrdWts);
            double sigmawjk = getScoreFrom(rawScores, j);
            double txtRnkj = getScoreFrom(currWtScores, j);
            sum += wij / sigmawjk * txtRnkj;
          }
        }
        ns.setScore((1d - DF) + sum * DF);// * rs.score
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
      Score s = new Score();
      s.setSentId(i);

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

  public List<Score> getRankedSentences(String doc, List<String> sentences,
                                        Hashtable<String, List<Integer>> iidx, List<String> processedSent) {
    this.sentences = sentences;
    this.processedSent = processedSent;

    Hashtable<String, Double> wrdWts = toWordWtHashtable(this.wordWt, iidx);// new

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

  public String getArticle() {
    return article;
  }

  public void setArticle(String article) {
    this.article = article;
  }

  public Hashtable<Integer, List<Integer>> getLinks() {
    return links;
  }

  private void setLinks(Hashtable<Integer, List<Integer>> links) {
    this.links = links;
  }
}

/*
 * public double getScore(String sent1, String sent2, boolean toPrint) {
 * String[] words1 = sent1.split("\\s+"); String[] words2 = sent2.split("\\s+");
 * double wordsInCommon = 0; for(int i=0;i< words1.length;i++) { for(int
 * j=0;j<words2.length;j++) { if(!sw.isStopWord(words1[i]) &&
 * !words1[i].trim().isEmpty() && words1[i].equals(words2[j])) { wordsInCommon+=
 * wordWt.getWordWeight(words1[i]); } } } return ((double)wordsInCommon) /
 * (Math.log(1+words1.length) + Math.log(1+words2.length)); }
 */