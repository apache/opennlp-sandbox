package opennlp.tools.disambiguator.lesk;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import java.util.Collections;

import opennlp.tools.disambiguator.Constants;
import opennlp.tools.disambiguator.Loader;
import opennlp.tools.disambiguator.Node;
import opennlp.tools.disambiguator.PreProcessor;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.disambiguator.WordPOS;
import opennlp.tools.disambiguator.WordSense;
import opennlp.tools.util.Span;
import net.sf.extjwnl.data.Synset;

/**
 * Class for the Lesk algorithm and variants.
 */

public class Lesk implements WSDisambiguator {

  protected LeskParameters params;

  public Loader loader;

  public Lesk() {
    this(null);
  }

  public Lesk(LeskParameters params) throws InvalidParameterException {
    loader = new Loader();
    this.setParams(params);
  }

  public void setParams(LeskParameters params) throws InvalidParameterException {
    if (params == null) {
      this.params = new LeskParameters();
    } else {
      if (params.isValid()) {
        this.params = params;
      } else {
        throw new InvalidParameterException("wrong params");
      }
    }
  }

  public ArrayList<WordSense> basic(WTDLesk wtd) {

    ArrayList<WordPOS> relvWords = PreProcessor.getAllRelevantWords(wtd);
    WordPOS word = new WordPOS(wtd.getWord(), Constants.getPOS(wtd.getPosTag()));

    ArrayList<Synset> synsets = word.getSynsets();
    ArrayList<Node> nodes = new ArrayList<Node>();

    for (Synset synset : synsets) {
      Node node = new Node(synset, relvWords);
      nodes.add(node);
    }

    ArrayList<WordSense> scoredSenses = updateSenses(nodes);

    for (WordSense wordSense : scoredSenses) {
      wordSense.setWTDLesk(wtd);
      int count = 0;
      for (WordPOS senseWordPOS : wordSense.getNode().getSenseRelevantWords()) {
        ArrayList stems = (ArrayList) PreProcessor.Stem(senseWordPOS);
        for (WordPOS sentenceWordPOS : relvWords) {
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

  public ArrayList<WordSense> basicContextual(WTDLesk wtd) {
    return this.basicContextual(wtd, LeskParameters.DFLT_WIN_SIZE);
  }

  public ArrayList<WordSense> basicContextual(WTDLesk wtd, int windowSize) {
    return this.basicContextual(wtd, windowSize, windowSize);
  }

  public ArrayList<WordSense> basicContextual(WTDLesk wtd, int windowBackward,
      int windowForward) {

    ArrayList<WordPOS> relvWords = PreProcessor.getRelevantWords(wtd,
        windowBackward, windowForward);
    WordPOS word = new WordPOS(wtd.getWord(), Constants.getPOS(wtd.getPosTag()));

    ArrayList<Synset> synsets = word.getSynsets();
    ArrayList<Node> nodes = new ArrayList<Node>();

    for (Synset synset : synsets) {
      Node node = new Node(synset, relvWords);
      nodes.add(node);
    }

    ArrayList<WordSense> scoredSenses = updateSenses(nodes);

    for (WordSense wordSense : scoredSenses) {
      wordSense.setWTDLesk(wtd);

      int count = 0;
      for (WordPOS senseWordPOS : wordSense.getNode().getSenseRelevantWords()) {

        for (WordPOS sentenceWordPOS : relvWords) {
          // TODO change to lemma check
          if (sentenceWordPOS.isStemEquivalent(senseWordPOS)) {
            count = count + 1;
          }
        }

      }
      wordSense.setScore(count);

    }

    Collections.sort(scoredSenses);

    return scoredSenses;
  }

  public ArrayList<WordSense> extended(WTDLesk wtd, int depth,
      double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    return extendedContextual(wtd, 0, depth, depthScoreWeight, includeSynonyms,
        includeHypernyms, includeHyponyms, includeMeronyms, includeHolonyms);

  }

  public ArrayList<WordSense> extendedContextual(WTDLesk wtd, int depth,
      double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    return extendedContextual(wtd, LeskParameters.DFLT_WIN_SIZE, depth,
        depthScoreWeight, includeSynonyms, includeHypernyms, includeHyponyms,
        includeMeronyms, includeHolonyms);

  }

  public ArrayList<WordSense> extendedContextual(WTDLesk wtd, int windowSize,
      int depth, double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    return extendedContextual(wtd, windowSize, windowSize, depth,
        depthScoreWeight, includeSynonyms, includeHypernyms, includeHyponyms,
        includeMeronyms, includeHolonyms);
  }

  public ArrayList<WordSense> extendedContextual(WTDLesk wtd,
      int windowBackward, int windowForward, int depth,
      double depthScoreWeight, boolean includeSynonyms,
      boolean includeHypernyms, boolean includeHyponyms,
      boolean includeMeronyms, boolean includeHolonyms) {

    ArrayList<WordPOS> relvWords = PreProcessor.getRelevantWords(wtd,
        windowBackward, windowForward);
    WordPOS word = new WordPOS(wtd.getWord(), Constants.getPOS(wtd.getPosTag()));

    ArrayList<Synset> synsets = word.getSynsets();
    ArrayList<Node> nodes = new ArrayList<Node>();

    for (Synset synset : synsets) {
      Node node = new Node(synset, relvWords);
      nodes.add(node);
    }

    ArrayList<WordSense> scoredSenses = basicContextual(wtd, windowBackward,
        windowForward);

    for (WordSense wordSense : scoredSenses) {

      if (includeSynonyms) {
        wordSense.setScore(wordSense.getScore() + depthScoreWeight
            * assessSynonyms(wordSense.getNode().getSynonyms(), relvWords));
      }

      if (includeHypernyms) {
        fathomHypernyms(wordSense, wordSense.getNode().synset, relvWords,
            depth, depth, depthScoreWeight);
      }

      if (includeHyponyms) {

        fathomHyponyms(wordSense, wordSense.getNode().synset, relvWords, depth,
            depth, depthScoreWeight);
      }

      if (includeMeronyms) {

        fathomMeronyms(wordSense, wordSense.getNode().synset, relvWords, depth,
            depth, depthScoreWeight);

      }

      if (includeHolonyms) {

        fathomHolonyms(wordSense, wordSense.getNode().synset, relvWords, depth,
            depth, depthScoreWeight);

      }

    }

    return scoredSenses;

  }

  public ArrayList<WordSense> extendedExponential(WTDLesk wtd, int depth,
      double intersectionExponent, double depthExponent,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedExponentialContextual(wtd, 0, depth, intersectionExponent,
        depthExponent, includeSynonyms, includeHypernyms, includeHyponyms,
        includeMeronyms, includeHolonyms);

  }

  public ArrayList<WordSense> extendedExponentialContextual(WTDLesk wtd,
      int depth, double intersectionExponent, double depthExponent,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedExponentialContextual(wtd, LeskParameters.DFLT_WIN_SIZE,
        depth, intersectionExponent, depthExponent, includeSynonyms,
        includeHypernyms, includeHyponyms, includeMeronyms, includeHolonyms);
  }

  public ArrayList<WordSense> extendedExponentialContextual(WTDLesk wtd,
      int windowSize, int depth, double intersectionExponent,
      double depthExponent, boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {

    return extendedExponentialContextual(wtd, windowSize, windowSize, depth,
        intersectionExponent, depthExponent, includeSynonyms, includeHypernyms,
        includeHyponyms, includeMeronyms, includeHolonyms);
  }

  public ArrayList<WordSense> extendedExponentialContextual(WTDLesk wtd,
      int windowBackward, int windowForward, int depth,
      double intersectionExponent, double depthExponent,
      boolean includeSynonyms, boolean includeHypernyms,
      boolean includeHyponyms, boolean includeMeronyms, boolean includeHolonyms) {
    ArrayList<WordPOS> relvWords = PreProcessor.getRelevantWords(wtd,
        windowBackward, windowForward);
    WordPOS word = new WordPOS(wtd.getWord(), Constants.getPOS(wtd.getPosTag()));

    ArrayList<Synset> synsets = word.getSynsets();
    ArrayList<Node> nodes = new ArrayList<Node>();

    for (Synset synset : synsets) {
      Node node = new Node(synset, relvWords);
      nodes.add(node);
    }

    ArrayList<WordSense> scoredSenses = basicContextual(wtd, windowForward,
        windowBackward);

    for (WordSense wordSense : scoredSenses) {

      if (includeSynonyms) {
        wordSense.setScore(wordSense.getScore()
            + Math.pow(
                assessSynonyms(wordSense.getNode().getSynonyms(), relvWords),
                intersectionExponent));
      }

      if (includeHypernyms) {
        fathomHypernymsExponential(wordSense, wordSense.getNode().synset,
            relvWords, depth, depth, intersectionExponent, depthExponent);
      }

      if (includeHyponyms) {

        fathomHyponymsExponential(wordSense, wordSense.getNode().synset,
            relvWords, depth, depth, intersectionExponent, depthExponent);
      }

      if (includeMeronyms) {

        fathomMeronymsExponential(wordSense, wordSense.getNode().synset,
            relvWords, depth, depth, intersectionExponent, depthExponent);

      }

      if (includeHolonyms) {

        fathomHolonymsExponential(wordSense, wordSense.getNode().synset,
            relvWords, depth, depth, intersectionExponent, depthExponent);

      }

    }

    return scoredSenses;

  }

  private void fathomHypernyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setHypernyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getHypernyms(), relvWords));
    for (Synset hypernym : childNode.getHypernyms()) {
      fathomHypernyms(wordSense, hypernym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  private void fathomHypernymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setHypernyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHypernyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset hypernym : childNode.getHypernyms()) {

      fathomHypernymsExponential(wordSense, hypernym, relvGlossWords,
          depth - 1, maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  private void fathomHyponyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setHyponyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getHyponyms(), relvWords));
    for (Synset hyponym : childNode.getHyponyms()) {

      fathomHyponyms(wordSense, hyponym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  private void fathomHyponymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setHyponyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHyponyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset hyponym : childNode.getHyponyms()) {

      fathomHyponymsExponential(wordSense, hyponym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  private void fathomMeronyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setMeronyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getMeronyms(), relvWords));
    for (Synset meronym : childNode.getMeronyms()) {

      fathomMeronyms(wordSense, meronym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  private void fathomMeronymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setMeronyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getMeronyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset meronym : childNode.getMeronyms()) {

      fathomMeronymsExponential(wordSense, meronym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  private void fathomHolonyms(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double depthScoreWeight) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setHolonyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(depthScoreWeight, maxDepth - depth + 1)
        * assessFeature(childNode.getHolonyms(), relvWords));
    for (Synset holonym : childNode.getHolonyms()) {

      fathomHolonyms(wordSense, holonym, relvGlossWords, depth - 1, maxDepth,
          depthScoreWeight);
    }
  }

  private void fathomHolonymsExponential(WordSense wordSense, Synset child,
      ArrayList<WordPOS> relvWords, int depth, int maxDepth,
      double intersectionExponent, double depthScoreExponent) {
    if (depth == 0)
      return;

    String[] tokenizedGloss = Loader.getTokenizer().tokenize(
        child.getGloss().toString());
    ArrayList<WordPOS> relvGlossWords = PreProcessor
        .getAllRelevantWords(tokenizedGloss);

    Node childNode = new Node(child, relvGlossWords);

    childNode.setHolonyms();
    wordSense.setScore(wordSense.getScore()
        + Math.pow(assessFeature(childNode.getHolonyms(), relvWords),
            intersectionExponent) / Math.pow(depth, depthScoreExponent));
    for (Synset holonym : childNode.getHolonyms()) {

      fathomHolonymsExponential(wordSense, holonym, relvGlossWords, depth - 1,
          maxDepth, intersectionExponent, depthScoreExponent);
    }
  }

  private int assessFeature(ArrayList<Synset> featureSynsets,
      ArrayList<WordPOS> relevantWords) {
    int count = 0;
    for (Synset synset : featureSynsets) {
      Node subNode = new Node(synset, relevantWords);

      String[] tokenizedSense = Loader.getTokenizer().tokenize(
          subNode.getSense());
      ArrayList<WordPOS> relvSenseWords = PreProcessor
          .getAllRelevantWords(tokenizedSense);

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

  private int assessSynonyms(ArrayList<WordPOS> synonyms,
      ArrayList<WordPOS> relevantWords) {
    int count = 0;

    for (WordPOS synonym : synonyms) {
      for (WordPOS sentenceWord : relevantWords) {
        // TODO try to switch to lemmatizer
        if (sentenceWord.isStemEquivalent(synonym)) {
          count = count + 1;
        }
      }

    }

    return count;
  }

  public ArrayList<WordSense> updateSenses(ArrayList<Node> nodes) {

    ArrayList<WordSense> scoredSenses = new ArrayList<WordSense>();

    for (int i = 0; i < nodes.size(); i++) {
      ArrayList<WordPOS> sensesComponents = PreProcessor
          .getAllRelevantWords(PreProcessor.tokenize(nodes.get(i).getSense()));
      WordSense wordSense = new WordSense();
      nodes.get(i).setSenseRelevantWords(sensesComponents);
      wordSense.setNode(nodes.get(i));
      wordSense.setId(i);
      scoredSenses.add(wordSense);
    }
    return scoredSenses;

  }

  // disambiguates a WTDLesk and returns an array of sense indexes from WordNet
  // ordered by their score
  @Override
  public String[] disambiguate(String[] inputText, int inputWordIndex) {
    WTDLesk wtd = new WTDLesk(inputText, inputWordIndex);
    ArrayList<WordSense> wsenses = null;

    switch (this.params.leskType) {
    case LESK_BASIC:
      wsenses = basic(wtd);
      break;
    case LESK_BASIC_CTXT:
      wsenses = basicContextual(wtd);
      break;
    case LESK_BASIC_CTXT_WIN:
      wsenses = basicContextual(wtd, this.params.win_b_size);
      break;
    case LESK_BASIC_CTXT_WIN_BF:
      wsenses = basicContextual(wtd, this.params.win_b_size,
          this.params.win_f_size);
      break;
    case LESK_EXT:
      wsenses = extended(wtd, this.params.depth, this.params.depth_weight,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_CTXT:
      wsenses = extendedContextual(wtd, this.params.depth,
          this.params.depth_weight, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    case LESK_EXT_CTXT_WIN:
      wsenses = extendedContextual(wtd, this.params.win_b_size,
          this.params.depth, this.params.depth_weight,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_CTXT_WIN_BF:
      wsenses = extendedContextual(wtd, this.params.win_b_size,
          this.params.win_f_size, this.params.depth, this.params.depth_weight,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP:
      wsenses = extendedExponential(wtd, this.params.depth, this.params.iexp,
          this.params.dexp, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP_CTXT:
      wsenses = extendedExponentialContextual(wtd, this.params.depth,
          this.params.iexp, this.params.dexp, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP_CTXT_WIN:
      wsenses = extendedExponentialContextual(wtd, this.params.win_b_size,
          this.params.depth, this.params.iexp, this.params.dexp,
          this.params.fathom_synonyms, this.params.fathom_hypernyms,
          this.params.fathom_hyponyms, this.params.fathom_meronyms,
          this.params.fathom_holonyms);
      break;
    case LESK_EXT_EXP_CTXT_WIN_BF:
      wsenses = extendedExponentialContextual(wtd, this.params.win_b_size,
          this.params.win_f_size, this.params.depth, this.params.iexp,
          this.params.dexp, this.params.fathom_synonyms,
          this.params.fathom_hypernyms, this.params.fathom_hyponyms,
          this.params.fathom_meronyms, this.params.fathom_holonyms);
      break;
    }

    wsenses = extendedExponentialContextual(wtd, LeskParameters.DFLT_WIN_SIZE,
        LeskParameters.DFLT_DEPTH, LeskParameters.DFLT_IEXP,
        LeskParameters.DFLT_DEXP, true, true, true, true, true);
    Collections.sort(wsenses);

    String[] senses = new String[wsenses.size()];
    for (int i = 0; i < wsenses.size(); i++) {
      senses[i] = wsenses.get(i).getSense();
    }
    return senses;
  }

  @Override
  public String[] disambiguate(String[] inputText, Span[] inputWordSpans) {
    // TODO need to work on spans
    return null;
  }

}
