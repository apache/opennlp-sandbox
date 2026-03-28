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

package opennlp.tools.disambiguator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.dictionary.Dictionary;
import net.sf.extjwnl.dictionary.MorphologicalProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.lemmatizer.ThreadSafeLemmatizerME;
import opennlp.tools.models.ModelType;
import opennlp.tools.models.ClassPathModelProvider;
import opennlp.tools.models.DefaultClassPathModelProvider;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormat;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.ThreadSafePOSTaggerME;
import opennlp.tools.tokenize.ThreadSafeTokenizerME;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerModel;

/**
 * A helper class that loads and organizes resources, and provides helper methods
 * to avoid multiple copies of dealing with certain resources.
 */
public class WSDHelper {

  private static final Logger LOG = LoggerFactory.getLogger(WSDHelper.class);

  private static final Pattern NUMBERS_PATTERN = Pattern.compile(".*[0-9].*");

  private static final ClassPathModelProvider MODEL_PROVIDER = new DefaultClassPathModelProvider();

  private static Tokenizer tokenizer;
  private static POSTagger tagger;
  private static Lemmatizer lemmatizer;
  private static Dictionary dictionary;
  private static MorphologicalProcessor morph;

  // local caches for faster lookup
  private static Map<String, Map<String, Object>> stemCache;
  private static Map<String, Object> stopCache;
  private static Map<String, Object> relvCache;

  private static Map<String, Object> nonRelevWordsDef;

  // Lists of word groups
  private static final List<String> TAGS_ADJECTIVE = Arrays.asList("JJ", "JJR", "JJS");
  private static final List<String> TAGS_ADVERB = Arrays.asList("RB", "RBR", "RBS", "UH");
  private static final List<String> TAGS_NOUN = Arrays.asList("NN", "NNS", "NNP", "NNPS");
  private static final List<String> TAGS_VERB = Arrays.asList("VB", "VBD", "VBG", "VBN", "VBP", "VBZ");

  // List of all the PoS tags
  private static final String[] ALL_POS = {"CC", "CD", "DT", "EX", "FW", "IN", "JJ",
          "JJR", "JJS", "LS", "MD", "NN", "NNS", "NNP", "NNPS", "PDT", "POS",
          "PRP", "PRP$", "RB", "RBR", "RBS", "RP", "SYM", "TO", "UH", "VB", "VBD",
          "VBG", "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB"};

  // List of the PoS tags of which the senses are to be extracted
  private static final String[] RELEVANT_POS = {"JJ", "JJR", "JJS", "NN", "NNS", "RB",
          "RBR", "RBS", "UH", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ"};

  // List of Negation Words
  private static final List<String> NEGATION_WORDS = Arrays.asList("not", "no", "never", "none", "nor", "non");

  // List of Stop Words
  protected static final List<String> STOP_WORDS = Arrays.asList(
          "a", "able", "about", "above", "according", "accordingly",
          "across", "actually", "after", "afterwards", "again", "against",
          "ain't", "all", "allow", "allows", "almost", "alone", "along",
          "already", "also", "although", "always", "am", "among", "amongst",
          "an", "and", "another", "any", "anybody", "anyhow", "anyone",
          "anything", "anyway", "anyways", "anywhere", "apart", "appear",
          "appreciate", "appropriate", "are", "aren't", "around", "as",
          "aside", "ask", "asking", "associated", "at", "available", "away",
          "awfully", "be", "became", "because", "become", "becomes",
          "becoming", "been", "before", "beforehand", "behind", "being",
          "believe", "below", "beside", "besides", "best", "better", "between",
          "beyond", "both", "brief", "but", "by", "came", "can", "cannot",
          "cant", "can't", "cause", "causes", "certain", "certainly",
          "changes", "clearly", "c'mon", "co", "com", "come", "comes",
          "concerning", "consequently", "consider", "considering", "contain",
          "containing", "contains", "corresponding", "could", "couldn't",
          "course", "c's", "currently", "definitely", "described", "despite",
          "did", "didn't", "different", "do", "does", "doesn't", "doing",
          "done", "don't", "down", "downwards", "during", "each", "edu", "eg",
          "eight", "either", "else", "elsewhere", "enough", "entirely",
          "especially", "et", "etc", "even", "ever", "every", "everybody",
          "everyone", "everything", "everywhere", "ex", "exactly", "example",
          "except", "far", "few", "fifth", "first", "five", "followed",
          "following", "follows", "for", "former", "formerly", "forth", "four",
          "from", "further", "furthermore", "get", "gets", "getting", "given",
          "gives", "go", "goes", "going", "gone", "got", "gotten", "greetings",
          "had", "hadn't", "happens", "hardly", "has", "hasn't", "have",
          "haven't", "having", "he", "hello", "help", "hence", "her", "here",
          "hereafter", "hereby", "herein", "here's", "hereupon", "hers",
          "herself", "he's", "hi", "him", "himself", "his", "hither",
          "hopefully", "how", "howbeit", "however", "i", "i'd", "ie", "if",
          "ignored", "i'll", "i'm", "immediate", "in", "inasmuch", "inc",
          "indeed", "indicate", "indicated", "indicates", "inner", "insofar",
          "instead", "into", "inward", "is", "isn't", "it", "it'd", "it'll",
          "its", "it's", "itself", "i've", "just", "keep", "keeps", "kept",
          "know", "known", "knows", "last", "lately", "later", "latter",
          "latterly", "least", "less", "lest", "let", "let's", "like", "liked",
          "likely", "little", "look", "looking", "looks", "ltd", "mainly",
          "many", "may", "maybe", "me", "mean", "meanwhile", "merely", "might",
          "more", "moreover", "most", "mostly", "much", "must", "my", "myself",
          "name", "namely", "nd", "near", "nearly", "necessary", "need",
          "needs", "neither", "never", "nevertheless", "new", "next", "nine",
          "no", "nobody", "non", "none", "noone", "nor", "normally", "not",
          "nothing", "novel", "now", "nowhere", "obviously", "of", "off",
          "often", "oh", "ok", "okay", "old", "on", "once", "one", "ones",
          "only", "onto", "or", "other", "others", "otherwise", "ought", "our",
          "ours", "ourselves", "out", "outside", "over", "overall", "own",
          "particular", "particularly", "per", "perhaps", "placed", "please",
          "plus", "possible", "presumably", "probably", "provides", "que",
          "quite", "qv", "rather", "rd", "re", "really", "reasonably",
          "regarding", "regardless", "regards", "relatively", "respectively",
          "right", "said", "same", "saw", "say", "saying", "says", "second",
          "secondly", "see", "seeing", "seem", "seemed", "seeming", "seems",
          "seen", "self", "selves", "sensible", "sent", "serious", "seriously",
          "seven", "several", "shall", "she", "should", "shouldn't", "since",
          "six", "so", "some", "somebody", "somehow", "someone", "something",
          "sometime", "sometimes", "somewhat", "somewhere", "soon", "sorry",
          "specified", "specify", "specifying", "still", "sub", "such", "sup",
          "sure", "take", "taken", "tell", "tends", "th", "than", "thank",
          "thanks", "thanx", "that", "thats", "that's", "the", "their",
          "theirs", "them", "themselves", "then", "thence", "there",
          "thereafter", "thereby", "therefore", "therein", "theres", "there's",
          "thereupon", "these", "they", "they'd", "they'll", "they're",
          "they've", "think", "third", "this", "thorough", "thoroughly",
          "those", "though", "three", "through", "throughout", "thru", "thus",
          "to", "together", "too", "took", "toward", "towards", "tried",
          "tries", "truly", "try", "trying", "t's", "twice", "two", "un",
          "under", "unfortunately", "unless", "unlikely", "until", "unto",
          "up", "upon", "us", "use", "used", "useful", "uses", "using",
          "usually", "value", "various", "very", "via", "viz", "vs", "want",
          "wants", "was", "wasn't", "way", "we", "we'd", "welcome", "well",
          "we'll", "went", "were", "we're", "weren't", "we've", "what",
          "whatever", "what's", "when", "whence", "whenever", "where",
          "whereafter", "whereas", "whereby", "wherein", "where's",
          "whereupon", "wherever", "whether", "which", "while", "whither",
          "who", "whoever", "whole", "whom", "who's", "whose", "why", "will",
          "willing", "wish", "with", "within", "without", "wonder", "won't",
          "would", "wouldn't", "yes", "yet", "you", "you'd", "you'll", "your",
          "you're", "yours", "yourself", "yourselves", "you've", "zero");

  public static Map<String, Object> getRelvCache() {
    if (relvCache == null || relvCache.isEmpty()) {
      relvCache = new HashMap<>();
      for (String t : RELEVANT_POS) {
        relvCache.put(t, null);
      }
    }
    return relvCache;
  }

  public static Map<String, Object> getStopCache() {
    if (stopCache == null || stopCache.isEmpty()) {
      stopCache = new HashMap<>();
      for (String s : STOP_WORDS) {
        stopCache.put(s, null);
      }
    }
    return stopCache;
  }

  public static Map<String, Map<String, Object>> getStemCache() {
    if (stemCache == null || stemCache.isEmpty()) {
      stemCache = new HashMap<>();
      for (POS pos : POS.getAllPOS()) {
        stemCache.put(pos.getKey(), new HashMap<>());
      }
    }
    return stemCache;
  }

  /**
   * This initializes the Hashmap of irrelevant words definitions, and returns
   * the definition of the irrelevant word based on its pos-tag
   *
   * @param posTag the pos-tag of the irrelevant word
   * @return the definition of the word
   */
  public static String getNonRelevWordsDef(String posTag) {
    if (nonRelevWordsDef == null || nonRelevWordsDef.isEmpty()) {
      nonRelevWordsDef = new HashMap<>();

      nonRelevWordsDef.put("CC", "coordinating conjunction");
      nonRelevWordsDef.put("CD", "cardinal number");
      nonRelevWordsDef.put("DT", "determiner");
      nonRelevWordsDef.put("EX", "existential there");
      nonRelevWordsDef.put("FW", "foreign word");
      nonRelevWordsDef.put("IN", "preposition / subordinating conjunction");
      nonRelevWordsDef.put("JJ", "adjective");
      nonRelevWordsDef.put("JJR", "adjective, comparative");
      nonRelevWordsDef.put("JJS", "adjective, superlative");
      nonRelevWordsDef.put("LS", "list marker");
      nonRelevWordsDef.put("MD", "modal");
      nonRelevWordsDef.put("NN", "noun, singular or mass");
      nonRelevWordsDef.put("NNS", "noun plural");
      nonRelevWordsDef.put("NNP", "proper noun, singular");
      nonRelevWordsDef.put("NNPS", "proper noun, plural");
      nonRelevWordsDef.put("PDT", "predeterminer");
      nonRelevWordsDef.put("POS", "possessive ending");
      nonRelevWordsDef.put("PRP", "personal pronoun");
      nonRelevWordsDef.put("PRP$", "possessive pronoun");
      nonRelevWordsDef.put("RB", "adverb");
      nonRelevWordsDef.put("RBR", "adverb, comparative");
      nonRelevWordsDef.put("RBS", "adverb, superlative");
      nonRelevWordsDef.put("RP", "particle");
      nonRelevWordsDef.put("SYM", "Symbol");
      nonRelevWordsDef.put("TO", "to");
      nonRelevWordsDef.put("UH", "interjection");
      nonRelevWordsDef.put("VB", "verb, base form");
      nonRelevWordsDef.put("VBD", "verb, past tense");
      nonRelevWordsDef.put("VBG", "verb, gerund/present participle");
      nonRelevWordsDef.put("VBN", "verb, past participle");
      nonRelevWordsDef.put("VBP", "verb, sing. present, non-3d");
      nonRelevWordsDef.put("VBZ", "verb, 3rd person sing. present");
      nonRelevWordsDef.put("WDT", "wh-determiner");
      nonRelevWordsDef.put("WP", "wh-pronoun");
      nonRelevWordsDef.put("WP$", "possessive wh-pronoun");
      nonRelevWordsDef.put("WRB", "wh-adverb");

    }
    return (String) nonRelevWordsDef.get(posTag);
  }

  public static MorphologicalProcessor getMorph() {
    if (morph == null) {
      getDictionary();
      morph = dictionary.getMorphologicalProcessor();
    }
    return morph;
  }

  public static Dictionary getDictionary() {
    if (dictionary == null) {
      try {
        dictionary = Dictionary.getDefaultResourceInstance();
      } catch (JWNLException e) {
        throw new RuntimeException(e);
      }
    }
    return dictionary;
  }

  public static Lemmatizer getLemmatizer() {
    return getLemmatizer("en");
  }

  private static Lemmatizer getLemmatizer(String lang) {
    if (lemmatizer == null) {
      try {
        final LemmatizerModel lm = MODEL_PROVIDER.load(
                lang, ModelType.LEMMATIZER, LemmatizerModel.class);
        lemmatizer = new ThreadSafeLemmatizerME(lm);
      } catch (IOException e) {
        throw new RuntimeException("Error opening or loading a Lemmatizer from specified resource file!", e);
      }
    }
    return lemmatizer;
  }

  public static POSTagger getTagger() {
    return getTagger("en");
  }

  private static POSTagger getTagger(String lang) {
    if (tagger == null) {
      try {
        final POSModel pm = MODEL_PROVIDER.load(lang, ModelType.POS_GENERIC, POSModel.class);
        tagger = new ThreadSafePOSTaggerME(pm, POSTagFormat.PENN);
      } catch (IOException e) {
        throw new RuntimeException("Error opening or loading a Tokenizer for specified language!", e);
      }
    }
    return tagger;
  }

  public static Tokenizer getTokenizer() {
    return getTokenizer("en");
  }

  private static Tokenizer getTokenizer(String lang) {
    if (tokenizer == null) {
      try {
        final TokenizerModel tm = MODEL_PROVIDER.load(lang, ModelType.TOKENIZER, TokenizerModel.class);
        tokenizer = new ThreadSafeTokenizerME(tm);
      } catch (IOException e) {
        throw new RuntimeException("Error opening or loading a Tokenizer for specified language!", e);
      }
    }
    return tokenizer;
  }

  public static void loadTokenizer(String language) {
    getTokenizer(language);
  }

  public static void loadTagger(String language) {
    getTagger(language);
  }

  public static void loadLemmatizer(String language) {
    getLemmatizer(language);
  }

  /*
   * checks if the word is or contains a number
   */
  public static boolean containsNumbers(String word) {
    return NUMBERS_PATTERN.matcher(word).matches();
  }

  // Print a text in the console
  public static void printResults(Disambiguator disambiguator, String result) {

    if (result != null) {

      String[] parts;
      String sensekey;
      if (disambiguator instanceof Lesk) {

        double score;

          parts = result.split(" ");
          sensekey = parts[1];
          if (parts.length != 3) {
            score = -1.0;
          } else {
            score = Double.parseDouble(parts[2]);
          }
          if (parts[0].equalsIgnoreCase(WSDParameters.SenseSource.WORDNET.name())) {
            try {
              String gloss = getDictionary().getWordBySenseKey(sensekey).getSynset().getGloss();
              LOG.debug("Score : {} for sense  : {} : {}", score, sensekey, gloss);
            } catch (JWNLException e) {
              LOG.error(e.getLocalizedMessage(), e);
            }
          } else {
            if (parts[0].equalsIgnoreCase(WSDParameters.SenseSource.WSDHELPER.name())) {
              LOG.debug("This word is a {} : {}", sensekey, getNonRelevWordsDef(sensekey));
            }
          }
      } else {
          parts = result.split(" ");
          sensekey = parts[1];

          if (parts[0].equalsIgnoreCase(WSDParameters.SenseSource.WORDNET.name())) {
            try {
              String gloss = getDictionary().getWordBySenseKey(sensekey).getSynset().getGloss();
              LOG.debug("Sense  : {} : {}", sensekey, gloss);
            } catch (JWNLException e) {
              LOG.error(e.getLocalizedMessage(), e);
            }
          } else if (parts[0].equalsIgnoreCase(WSDParameters.SenseSource.WSDHELPER.name())) {
            LOG.debug("This word is a {} : {}", sensekey, WSDHelper.getNonRelevWordsDef(sensekey));
          }
      }
    }

  }

  /**
   * return the PoS (Class POS) out of the PoS-tag
   * 
   * @param posTag
   *          PoS tag (e.g., "JJS", "NNP", etc.)
   * @return the Part of Speech (type {@link POS})
   */
  public static POS getPOS(String posTag) {

    if (TAGS_ADJECTIVE.contains(posTag))
      return POS.ADJECTIVE;
    else if (TAGS_ADVERB.contains(posTag))
      return POS.ADVERB;
    else if (TAGS_NOUN.contains(posTag))
      return POS.NOUN;
    else if (TAGS_VERB.contains(posTag))
      return POS.VERB;
    else
      return null;
  }

  /**
   * Check whether a PoS Tag is relevant of not. A PoS Tag is considered
   * relevant when it corresponds to:
   * <ul>
   * <li>VERB</li>
   * <li>ADJECTIVE</li>
   * <li>ADVERB</li>
   * <li>NOUN</li>
   * </ul>
   * 
   * @param posTag
   *          the PoS Tag to verify the relevance.
   * @return whether a PoS Tag corresponds to a relevant Part of Speech (type
   *         {@link POS}) or not ( true} if it is, false} otherwise)
   */
  public static boolean isRelevantPOSTag(String posTag) {
    return getPOS(posTag) != null;
  }

  /**
   * Check whether a PoS Tag is relevant of not. A PoS Tag is considered
   * relevant when it is:
   * <ul>
   * <li>VERB</li>
   * <li>ADJECTIVE</li>
   * <li>ADVERB</li>
   * <li>NOUN</li>
   * </ul>
   * 
   * @param pos
   *          The Part of Speech of Type {@link POS}
   * @return whether a Part of Speech is relevant (true) or not (false)
   */
  public static boolean isRelevantPOS(POS pos) {
    return pos.equals(POS.ADJECTIVE) || pos.equals(POS.ADVERB)
        || pos.equals(POS.NOUN) || pos.equals(POS.VERB);
  }

  public static String getPOSabbreviation(String posTag) {

    if (posTag == null) {
      return null;
    }
    if (posTag.startsWith("JJ")) {
      return "a";
    } else if (posTag.startsWith("RB")) {
      return "r";
    } else if (posTag.startsWith("VB") || posTag.equals("MD")) {
      return "v";
    } else if (posTag.startsWith("NN")) {
      return "n";
    }

    return null;

  }

  /**
   * Check whether a list of arrays contains an array
   * 
   * @param array
   *          The array To check
   * @param fullList
   *          The full list of Arrays
   * @return whether the {@link ArrayList} of arrays contains the array (true)
   *         or not (false)
   */
  public static boolean belongsTo(String[] array, ArrayList<String[]> fullList) {
    for (String[] refArray : fullList) {
      if (areStringArraysEqual(array, refArray))
        return true;
    }
    return false;
  }

  /**
   * Check whether two arrays of strings are equal
   * 
   * @param array1
   *          first array
   * @param array2
   *          second array
   * @return whether the two arrays are identical (true) or not (false)
   */
  public static boolean areStringArraysEqual(String[] array1, String[] array2) {

    if (array1.equals(null) || array2.equals(null))
      return false;

    if (array1.length != array2.length) {
      return false;
    }
    for (int i = 0; i < array1.length; i++) {
      if (!array1[i].equals(array2[i])) {
        return false;
      }
    }

    return true;

  }

  public static List<WordPOS> getAllRelevantWords(String[] sentence) {

    List<WordPOS> relevantWords = new ArrayList<>();

    String[] tags = getTagger().tag(sentence);

    for (int i = 0; i < sentence.length; i++) {
      if (!WSDHelper.getStopCache().containsKey(sentence[i])) {
        if (WSDHelper.getRelvCache().containsKey(tags[i])) {
          relevantWords.add(new WordPOS(sentence[i], tags[i]));
        }

      }
    }
    return relevantWords;
  }

  /**
   * Stem a single word with WordNet dictionary.
   * 
   * @param wordToStem
   *          word to be stemmed
   * @return stemmed list of words
   */
  public static List<String> stemWordWithWordNet(WordPOS wordToStem) {
    if (wordToStem == null)
      return null;
    List<String> stems = new ArrayList<>();
    try {
      for (POS pos : POS.getAllPOS()) {
        stems.addAll(WSDHelper.getMorph().lookupAllBaseForms(pos, wordToStem.getWord()));
      }

      if (!stems.isEmpty())
        return stems;
      else {
        return null;
      }

    } catch (JWNLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Stem a single word tries to look up the word in the stemCache map. If
   * the word is not found, it is stemmed with WordNet and put into stemCache.
   * 
   * @param wordToStem
   *          word to be stemmed
   * @return stemmed word list, {@code null} means the word is incorrect
   */
  public static List<String> stem(WordPOS wordToStem) {
    final POS pos = wordToStem.getPOS();
    final String word = wordToStem.getWord();
    
    if (pos == null) {
      LOG.trace("The word is {}", wordToStem.getWord());
    }

    Map<String, Map<String, Object>> cache = WSDHelper.getStemCache();
    // check if we already cached the stem map
    Map<String, Object> posMap = cache.get(pos.getKey());

    // don't check words with digits in them
    if (WSDHelper.containsNumbers(word)) {
      return null;
    }

    List<String> stemList = (List<String>) posMap.get(word);
    if (stemList != null) { // return it if we already cached it
      return stemList;

    } else { // unCached list try to stem it
      stemList = stemWordWithWordNet(wordToStem);
      if (stemList != null) {
        // word was recognized and stemmed with wordnet:
        // add it to cache and return the stemmed list
        posMap.put(word, stemList);
        cache.put(pos.getKey(), posMap);
        return stemList;
      } else { // could not be stemmed add it anyway (as it is)
        stemList = new ArrayList<>();
        stemList.add(word);
        posMap.put(word, stemList);
        cache.put(pos.getKey(), posMap);
        return null;
      }
    }
  }
}
