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
package opennlp.tools.disambiguator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import opennlp.tools.tokenize.Tokenizer;

/**
 * A {@link Disambiguator} implementation based on the <b>Overlap Of Senses</b> approach,
 * originally proposed by <i>Lesk</i>.
 * <p>
 * The main idea is to check for word overlaps in the sense definitions
 * of the surrounding context. An overlap is when two words have similar stems.
 * The more overlaps a word has the higher its score. Different variations of
 * the approach are included in this class.
 *
 * @see Disambiguator
 * @see LeskParameters
 */
public class Lesk extends AbstractWSDisambiguator {

  /**
   * The lesk specific parameters
   */
  protected LeskParameters params;

  /**
   * List of filtered context words
   */
  final List<WordPOS> contextWords = new ArrayList<>();

  public Lesk() {
    this(null);
  }

  /**
   * Instantiates a {@link Lesk} instance and sets the input parameters.
   *
   * @param params If the {@link LeskParameters} are {@code null}, set the default ones,
   *               otherwise only set them if they valid.
   * @throws IllegalArgumentException Thrown if specified parameters are invalid.
   */
  public Lesk(LeskParameters params) {
    this.setParams(params);
  }

  /**
   * @param params If the parameters are {@code null}, set the default ones,
   *               else only set them if they valid.
   *
   * @throws IllegalArgumentException Thrown if specified parameters are invalid.
   */
  @Override
  public void setParams(WSDParameters params) {
    if (params == null) {
      this.params = new LeskParameters();
    } else {
      if (params.areValid()) {
        this.params = (LeskParameters) params;
      } else {
        throw new IllegalArgumentException("wrong params");
      }
    }
  }

  /**
   * @return Retrieves the parameter {@link LeskParameters settings}.
   */
  @Override
  public LeskParameters getParams() {
    return params;
  }

  /**
   * The basic Lesk method where the entire context is considered for overlaps
   * 
   * @param sample
   *          the word sample to disambiguate
   * @return The array of WordSenses with their scores
   */
  public List<WordSense> basic(WSDSample sample) {

    WordPOS word = new WordPOS(sample.getTargetWord(), sample.getTargetTag());

    for (int i = 0; i < sample.getSentence().length; i++) {
      String s = sample.getSentence()[i];
      String t = sample.getTags()[i];
      if (!WSDHelper.getStopCache().containsKey(s)) {
        if (WSDHelper.getRelvCache().containsKey(t)) {
          contextWords.add(new WordPOS(s, t));
        }
      }
    }

    List<SynNode> nodes = new ArrayList<>();
    for (Synset synset : word.getSynsets()) {
      SynNode node = new SynNode(synset, contextWords);
      nodes.add(node);
    }

    List<WordSense> scoredSenses = SynNode.updateSenses(nodes);

    for (WordSense wordSense : scoredSenses) {
      wordSense.setWSDSample(sample);
      int count = 0;
      for (WordPOS senseWordPOS : wordSense.getNode().getSenseRelevantWords()) {
        for (WordPOS sentenceWordPOS : contextWords) {
          if (sentenceWordPOS.isStemEquivalent(senseWordPOS)) {
            count = count + 1;
          }
        }
      }
      wordSense.setScore(count);
    }

    return scoredSenses;
  }

  /**
   * The basic Lesk method but applied to a default context windows
   * 
   * @param sample
   *          the word sample to disambiguate
   * @return The array of WordSenses with their scores
   */
  public List<WordSense> basicContextual(WSDSample sample) {

    WordPOS word = new WordPOS(sample.getTargetWord(), sample.getTargetTag());

    List<Synset> synsets = word.getSynsets();
    if (synsets == null) {
      return Collections.emptyList();
    }
    
    int index = sample.getTargetPosition();
    for (int i = index - getParams().win_b_size; i <= index + getParams().win_f_size; i++) {
      if (i >= 0 && i < sample.getSentence().length && i != index) {
        if (!WSDHelper.getStopCache().containsKey(sample.getSentence()[i])) {
          if (WSDHelper.getRelvCache().containsKey(sample.getTags()[i])) {
            contextWords.add(new WordPOS(sample.getSentence()[i], sample.getTags()[i]));
          }
        }
      }
    }

    List<SynNode> nodes = new ArrayList<>();
    for (Synset synset : synsets) {
      SynNode node = new SynNode(synset, contextWords);
      nodes.add(node);
    }

    List<WordSense> scoredSenses = SynNode.updateSenses(nodes);

    for (WordSense wordSense : scoredSenses) {
      wordSense.setWSDSample(sample);

      int count = 0;
      for (WordPOS senseWordPOS : wordSense.getNode().getSenseRelevantWords()) {

        for (WordPOS sentenceWordPOS : contextWords) {
          // TODO change to lemma check
          if (sentenceWordPOS.isStemEquivalent(senseWordPOS)) {
            count = count + 1;
          }
        }
      }
      wordSense.setScore(count);

    }
    return scoredSenses;
  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps across the entire context The scoring
   * function uses linear weights.
   * 
   * @param sample
   *          the word sample to disambiguate
   * @return the list of WordSenses with their scores
   */
  public List<WordSense> extended(WSDSample sample) {
    params.setWin_b_size(0);
    params.setWin_f_size(0);
    return extendedContextual(sample);

  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a default context window The
   * scoring function uses linear weights.
   * 
   * @param sample
   *          the word sample to disambiguate
   * @return the list of WordSenses with their scores
   */
  public List<WordSense> extendedContextual(WSDSample sample) {
    List<WordSense> scoredSenses;
    if (params.getWin_b_size() == 0 && params.getWin_f_size() == 0) {
      scoredSenses = basic(sample);
    } else {
      scoredSenses = basicContextual(sample);
    }
    for (WordSense wordSense : scoredSenses) {

      if (getParams().getFeatures()[0]) {
        wordSense.setScore(wordSense.getScore() + getParams().depth_weight
            * assessSynonyms(wordSense.getNode().getSynonyms(), contextWords));
      }

      if (getParams().getFeatures()[1]) {
        fathomHypernyms(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);
      }

      if (getParams().getFeatures()[2]) {
        fathomHyponyms(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);
      }

      if (getParams().getFeatures()[3]) {
        fathomMeronyms(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);

      }

      if (getParams().getFeatures()[4]) {
        fathomHolonyms(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);

      }

      if (getParams().getFeatures()[5]) {
        fathomEntailments(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);

      }
      if (getParams().getFeatures()[6]) {
        fathomCoordinateTerms(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.depth_weight);

      }
      if (getParams().getFeatures()[7]) {
        fathomCauses(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);

      }
      if (getParams().getFeatures()[8]) {
        fathomAttributes(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);

      }
      if (getParams().getFeatures()[9]) {
        fathomPertainyms(wordSense, wordSense.getNode().synset, contextWords,
            params.depth, params.depth, params.depth_weight);

      }

    }

    return scoredSenses;

  }

  /*
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in all the context. The scoring
   * function uses exponential weights.
   * 
   * @param sample the word sample to disambiguate
   * 
   * @return the array of WordSenses with their scores
   */
  public List<WordSense> extendedExponential(WSDSample sample) {
    params.setWin_b_size(0);
    params.setWin_f_size(0);
    return extendedExponentialContextual(sample);

  }

  /**
   * An extended version of the Lesk approach that takes into consideration
   * semantically related feature overlaps in a custom window in the context.
   * The scoring function uses exponential weights.
   * 
   * @param sample
   *          the word sample to disambiguate
   * @return the list of WordSenses with their scores
   */
  public List<WordSense> extendedExponentialContextual(WSDSample sample) {
    List<WordSense> scoredSenses;
    if (params.getWin_b_size() == 0 && params.getWin_f_size() == 0) {
      scoredSenses = basic(sample);
    } else {
      scoredSenses = basicContextual(sample);
    }

    for (WordSense wordSense : scoredSenses) {

      if (params.features[0]) {
        wordSense.setScore(wordSense.getScore() + Math.pow(
            assessSynonyms(wordSense.getNode().getSynonyms(), contextWords),
            params.iexp));
      }

      if (params.features[1]) {
        fathomHypernymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);
      }

      if (params.features[2]) {
        fathomHyponymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);
      }

      if (params.features[3]) {
        fathomMeronymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);

      }

      if (params.features[4]) {
        fathomHolonymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);

      }

      if (params.features[5]) {
        fathomEntailmentsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);
      }

      if (params.features[6]) {
        fathomCoordinateTermsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);

      }
      if (params.features[7]) {
        fathomCausesExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);

      }
      if (params.features[8]) {
        fathomAttributesExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);

      }
      if (params.features[9]) {
        fathomPertainymsExponential(wordSense, wordSense.getNode().synset,
            contextWords, params.depth, params.depth, params.iexp, params.dexp);

      }

    }

    return scoredSenses;

  }

  /**
   * Recursively score the hypernym tree linearly.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomHypernyms(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                               int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHypernyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getHypernyms(), relvWords));
    for (Synset hypernym : childNode.getHypernyms()) {
      fathomHypernyms(wordSense, hypernym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the hypernym tree exponentially.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomHypernymsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                          int depth, int maxDepth, double intersectionExponent,
                                          double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHypernyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHypernyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset hypernym : childNode.getHypernyms()) {
      fathomHypernymsExponential(wordSense, hypernym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Recursively score the hyponym tree linearly.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomHyponyms(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                              int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHyponyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getHyponyms(), relvWords));
    for (Synset hyponym : childNode.getHyponyms()) {

      fathomHyponyms(wordSense, hyponym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the hyponym tree exponentially.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomHyponymsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                         int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHyponyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHyponyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset hyponym : childNode.getHyponyms()) {

      fathomHyponymsExponential(wordSense, hyponym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Recursively score the meronym tree linearly.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomMeronyms(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                              int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setMeronyms();
    wordSense.setScore(
        wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getMeronyms(), relvWords));
    for (Synset meronym : childNode.getMeronyms()) {

      fathomMeronyms(wordSense, meronym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the meronym tree exponentially.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomMeronymsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                         int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setMeronyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(assessFeature(childNode.getMeronyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset meronym : childNode.getMeronyms()) {
      fathomMeronymsExponential(wordSense, meronym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  /**
   * Recursively score the holonym tree linearly.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param depthScoreWeight
   */
  private void fathomHolonyms(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                              int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHolonyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getHolonyms(), relvWords));
    for (Synset holonym : childNode.getHolonyms()) {
      fathomHolonyms(wordSense, holonym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  /**
   * Recursively score the holonym tree exponentially.
   * 
   * @param wordSense
   * @param child
   * @param relvWords
   * @param depth
   * @param maxDepth
   * @param intersectionExponent
   * @param depthScoreExponent
   */
  private void fathomHolonymsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                         int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setHolonyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(assessFeature(childNode.getHolonyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset holonym : childNode.getHolonyms()) {
      fathomHolonymsExponential(wordSense, holonym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  private void fathomEntailments(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                 int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setEntailements();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getEntailments(), relvWords));
    for (Synset entailment : childNode.getEntailments()) {
      fathomEntailments(wordSense, entailment, relvGlossWords, depth - 1,
          maxDepth, depthScoreWeight);
    }

  }

  private void fathomEntailmentsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                            int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setEntailements();
    wordSense.setScore(wordSense.getScore() + Math.pow(assessFeature(childNode.getEntailments(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset entailment : childNode.getEntailments()) {
      fathomEntailmentsExponential(wordSense, entailment, relvGlossWords,
          depth - 1, maxDepth, intersectionExponent, depthScoreExponent);
    }

  }

  private void fathomCoordinateTerms(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                     int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setCoordinateTerms();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getCoordinateTerms(), relvWords));
    for (Synset coordinate : childNode.getCoordinateTerms()) {
      fathomCoordinateTerms(wordSense, coordinate, relvGlossWords, depth - 1,
          maxDepth, depthScoreWeight);
    }

  }

  private void fathomCoordinateTermsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                                int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setCoordinateTerms();
    wordSense.setScore(wordSense.getScore() + Math.pow(assessFeature(childNode.getCoordinateTerms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset coordinate : childNode.getCoordinateTerms()) {
      fathomCoordinateTermsExponential(wordSense, coordinate, relvGlossWords,
          depth - 1, maxDepth, intersectionExponent, depthScoreExponent);
    }

  }

  private void fathomCauses(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                            int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setCauses();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getCauses(), relvWords));
    for (Synset cause : childNode.getCauses()) {
      fathomEntailments(wordSense, cause, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }

  }

  private void fathomCausesExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                       int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setCauses();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getCauses(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset cause : childNode.getCauses()) {
      fathomCausesExponential(wordSense, cause, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }

  }

  private void fathomAttributes(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setAttributes();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getAttributes(), relvWords));
    for (Synset attribute : childNode.getAttributes()) {
      fathomAttributes(wordSense, attribute, relvGlossWords, depth - 1,
          maxDepth, depthScoreWeight);
    }

  }

  private void fathomAttributesExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                           int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setAttributes();
    wordSense.setScore(wordSense.getScore() + Math.pow(assessFeature(childNode.getAttributes(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset attribute : childNode.getAttributes()) {
      fathomAttributesExponential(wordSense, attribute, relvGlossWords,
          depth - 1, maxDepth, intersectionExponent, depthScoreExponent);
    }

  }

  private void fathomPertainyms(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                int depth, int maxDepth, double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setPertainyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(depthScoreWeight, maxDepth - depth + 1)
            * assessFeature(childNode.getPertainyms(), relvWords));
    for (Synset pertainym : childNode.getPertainyms()) {
      fathomPertainyms(wordSense, pertainym, relvGlossWords, depth - 1,
          maxDepth, depthScoreWeight);
    }

  }

  private void fathomPertainymsExponential(WordSense wordSense, Synset child, List<WordPOS> relvWords,
                                           int depth, int maxDepth, double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = WSDHelper.getTokenizer().tokenize(child.getGloss());
    ArrayList<WordPOS> relvGlossWords = WSDHelper.getAllRelevantWords(tokenizedGloss);

    SynNode childNode = new SynNode(child, relvGlossWords);

    childNode.setPertainyms();
    wordSense.setScore(wordSense.getScore() + Math.pow(assessFeature(childNode.getPertainyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset pertainym : childNode.getPertainyms()) {
      fathomPertainymsExponential(wordSense, pertainym, relvGlossWords,
          depth - 1, maxDepth, intersectionExponent, depthScoreExponent);
    }

  }

  /**
   * Checks if the feature should be counted in the score.
   * 
   * @param featureSynsets
   * @param relevantWords
   * @return count of features to consider
   */
  private int assessFeature(List<Synset> featureSynsets, List<WordPOS> relevantWords) {
    int count = 0;
    Tokenizer tokenizer = WSDHelper.getTokenizer();
    for (Synset synset : featureSynsets) {
      SynNode subNode = new SynNode(synset, relevantWords);

      String[] tokenizedSense = tokenizer.tokenize(subNode.getGloss());
      List<WordPOS> relvSenseWords = WSDHelper.getAllRelevantWords(tokenizedSense);

      for (WordPOS senseWord : relvSenseWords) {
        for (WordPOS sentenceWord : relevantWords) {
          if (sentenceWord.isStemEquivalent(senseWord)) {
            count = count + 1;
          }
        }
      }
    }
    return count;
  }

  /**
   * Checks if the synonyms should be counted in the score.
   * 
   * @param synonyms
   * @param relevantWords
   * @return count of synonyms to consider
   */
  private int assessSynonyms(List<WordPOS> synonyms, List<WordPOS> relevantWords) {
    int count = 0;

    for (WordPOS synonym : synonyms) {
      for (WordPOS sentenceWord : relevantWords) {
        if (sentenceWord.isStemEquivalent(synonym)) {
          count = count + 1;
        }
      }
    }
    return count;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String disambiguate(WSDSample sample) {
    if (!WSDHelper.isRelevantPOSTag(sample.getTargetTag())) {
      if (WSDHelper.getNonRelevWordsDef(sample.getTargetTag()) != null) {
        return WSDParameters.SenseSource.WSDHELPER.name() + " " + sample.getTargetTag();
      } else {
        return null;
      }
    }

    List<WordSense> wsenses = switch (this.params.leskType) {
      case LESK_BASIC -> basic(sample);
      case LESK_BASIC_CTXT -> basicContextual(sample);
      case LESK_EXT -> extended(sample);
      case LESK_EXT_CTXT -> extendedContextual(sample);
      case LESK_EXT_EXP -> extendedExponential(sample);
      case LESK_EXT_EXP_CTXT -> extendedExponentialContextual(sample);
      default -> extendedExponentialContextual(sample);
    };

    Collections.sort(wsenses);

    String sense;
    if (!wsenses.isEmpty() && wsenses.get(0).getScore() > 0) { // if at least one overlap
      List<Word> synsetWords;
      String senseKey = "?";
      synsetWords = wsenses.get(0).getNode().synset.getWords();
      for (Word synWord : synsetWords) {
        if (synWord.getLemma().equals(sample.getLemmas()[sample.getTargetPosition()])) {
          try {
            senseKey = synWord.getSenseKey();
          } catch (JWNLException e) {
            e.printStackTrace();
          }
          break;
        }
      }
      sense = params.source.name() + " " + senseKey + " " + wsenses.get(0).getScore();
    } else { // get the MFS if no overlaps
      sense = MFS.getMostFrequentSense(sample) + " -1";
    }
    return sense;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String disambiguate(String[] tokenizedContext, String[] tokenTags,
                             String[] lemmas, int ambiguousTokenIndex) {
    return disambiguate(new WSDSample(tokenizedContext, tokenTags, lemmas, ambiguousTokenIndex));
  }

}
