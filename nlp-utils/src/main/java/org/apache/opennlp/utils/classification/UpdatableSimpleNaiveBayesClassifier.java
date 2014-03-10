/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.utils.classification;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;


public class UpdatableSimpleNaiveBayesClassifier implements NaiveBayesClassifier<List<String>, String> {


  private final Collection<String> vocabulary = new TreeSet<String>(); // the bag of all the words in the corpus
  private final Map<String, Integer> classCounts = new LinkedHashMap<String, Integer>();
  private double noDocs = 0d;
  private final Map<String, Map<String, Integer>> nm = new HashMap<String, Map<String, Integer>>();
  private final Map<String, Double> priors = new HashMap<String, Double>();
  private final Map<String, Double> dens = new HashMap<String, Double>();

  public void addExample(String klass, List<String> words) {
    vocabulary.addAll(words);

    Integer integer = classCounts.get(klass);
    Integer f = integer != null ? integer : 0;
    classCounts.put(klass, f + 1);

    noDocs++;

    for (String w : words) {
      Map<String, Integer> wordCountsForClass = nm.get(klass);
      if (wordCountsForClass == null) {
        wordCountsForClass = new HashMap<String, Integer>();
      }
      Integer count = wordCountsForClass.get(w);
      if (count == null) {
        count = 1;
      } else {
        count++;
      }
      wordCountsForClass.put(w, count);
      nm.put(klass, wordCountsForClass);
    }
    for (String c : classCounts.keySet()) {
      priors.put(klass, calculatePrior(c));
    }
    calculateDen(klass);


  }

  private void calculateDen(String c) {
    // den : for the whole dictionary, count the no of times a word appears in documents of class c (+|V|)
    Double den = 0d;
    for (String w : vocabulary) {
      Integer integer = nm.get(c).get(w);
      den += integer != null ? integer : 0;
    }
    den += vocabulary.size() + 1; // +|V| is added because of add 1 smoothing, +1 for unknown words
    dens.put(c, den);
  }

  public String calculateClass(List<String> words) throws Exception {
    Double max = -1000000d;
    String foundClass = null;
    for (String cl : nm.keySet()) {
      double prior = priors.get(cl);
      double likeliHood = calculateLikelihood(words, cl);
      double clVal = prior + likeliHood;
      if (clVal > max) {
        max = clVal;
        foundClass = cl;
      }
    }
    System.err.println("class found: " + foundClass);
    return foundClass;
  }

  private Double calculateLikelihood(List<String> words, String c) {
    Map<String, Integer> wordFreqs = nm.get(c);
    // for each word
    double result = 0d;
    for (String word : words) {
      // num : count the no of times the word appears in documents of class c (+1)
      Integer freq = wordFreqs.get(word) != null ? wordFreqs.get(word) : 0;
      double num = freq + 1d; // +1 is added because of add 1 smoothing

      // P(w|c) = num/den
      double wordProbability = Math.log(num / dens.get(c));

      result += wordProbability;
    }

    // P(d|c) = P(w1|c)*...*P(wn|c)
    return result;
  }

  private Double calculatePrior(String currentClass) {
    return Math.log(docCount(currentClass) / noDocs);
  }

  private double docCount(String countedClass) {
    Integer integer = classCounts.get(countedClass);
    return integer != null ? (double) integer : 0d;
  }

}
