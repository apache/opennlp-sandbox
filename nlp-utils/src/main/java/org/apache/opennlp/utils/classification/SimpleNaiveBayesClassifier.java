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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * C = argmax( P(d|c) * P(c) )
 * where P(d|c) is called: likelihood
 * and P(c) is called: prior - we can count relative frequencies in a corpus
 * and d is a vector of features
 * <p/>
 * we assume:
 * 1. bag of words assumption: positions don't matter
 * 2. conditional independence: the feature probabilities are independent given a class
 * <p/>
 * thus P(d|c) == P(x1,..,xn|c) == P(x1|c)*...P(xn|c)
 */
public class SimpleNaiveBayesClassifier implements NaiveBayesClassifier<String, String> {

  private static final String UNKNOWN_WORD_TOKEN = "_unk_word_";

  private Collection<String> vocabulary; // the bag of all the words in the corpus
  private final Map<String, String> docsWithClass; // this is the trained corpus holding a the doc as a key and the class as a value
  private Map<String, String> classMegaDocMap; // key is the class, value is the megadoc
  //    private Map<String, String> preComputedWordClasses; // the key is the word, the value is its likelihood
  private Map<String, Double> priors;


  public SimpleNaiveBayesClassifier(Map<String, String> trainedCorpus) {
    this.docsWithClass = trainedCorpus;
    createVocabulary();
    createMegaDocs();
    preComputePriors();
//        preComputeWordClasses();

  }

  private void preComputePriors() {
    priors = new HashMap<String, Double>();
    for (String cl : classMegaDocMap.keySet()) {
      priors.put(cl, calculatePrior(cl));
    }
  }

//    private void preComputeWordClasses() {
//        Set<String> uniqueWordsVocabulary = new HashSet<String>(vocabulary);
//        for (String d : docsWithClass.keySet()) {
//            calculateClass(d);
//        }
//    }

  private void createMegaDocs() {
    classMegaDocMap = new HashMap<String, String>();
    Map<String, StringBuilder> mockClassMegaDocMap = new HashMap<String, StringBuilder>();
    for (String doc : docsWithClass.keySet()) {
      String cl = docsWithClass.get(doc);
      StringBuilder megaDoc = mockClassMegaDocMap.get(cl);
      if (megaDoc == null) {
        megaDoc = new StringBuilder();
        megaDoc.append(doc);
        mockClassMegaDocMap.put(cl, megaDoc);
      } else {
        mockClassMegaDocMap.put(cl, megaDoc.append(" ").append(doc));
      }
    }
    for (String cl : mockClassMegaDocMap.keySet()) {
      classMegaDocMap.put(cl, mockClassMegaDocMap.get(cl).toString());
    }
  }

  private void createVocabulary() {
    vocabulary = new LinkedList<String>();
    for (String doc : docsWithClass.keySet()) {
      String[] split = tokenizeDoc(doc);
      vocabulary.addAll(Arrays.asList(split));
    }
  }

  private String[] tokenizeDoc(String doc) {
    // TODO : this is by far not a tokenization, it should be changed
    return doc.split(" ");
  }

  @Override
  public String calculateClass(String inputDocument) {
    Double max = 0d;
    String foundClass = null;
    for (String cl : classMegaDocMap.keySet()) {
      Double clVal = priors.get(cl) * calculateLikelihood(inputDocument, cl);
      if (clVal > max) {
        max = clVal;
        foundClass = cl;
      }
    }
    return foundClass;
  }


  private Double calculateLikelihood(String document, String c) {
    String megaDoc = classMegaDocMap.get(c);
    // for each word
    Double result = 1d;
    for (String word : tokenizeDoc(document)) {
      // num : count the no of times the word appears in documents of class c (+1)
      double num = count(word, megaDoc) + 1; // +1 is added because of add 1 smoothing

      // den : for the whole dictionary, count the no of times a word appears in documents of class c (+|V|)
      double den = 0;
      for (String w : vocabulary) {
        den += count(w, megaDoc) + 1; // +1 is added because of add 1 smoothing
      }

      // P(w|c) = num/den
      double wordProbability = num / den;
      result *= wordProbability;
    }

    // P(d|c) = P(w1|c)*...*P(wn|c)
    return result;
  }

  private int count(String word, String doc) {
    int count = 0;
    for (String t : tokenizeDoc(doc)) {
      if (t.equals(word))
        count++;
    }
    return count;
  }

  private Double calculatePrior(String currentClass) {
    return (double) docCount(currentClass) / docsWithClass.keySet().size();
  }

  private int docCount(String countedClass) {
    int count = 0;
    for (String c : docsWithClass.values()) {
      if (c.equals(countedClass)) {
        count++;
      }
    }
    return count;
  }
}
