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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.dictionary.Dictionary;
import net.sf.extjwnl.dictionary.MorphologicalProcessor;
import opennlp.tools.cmdline.postag.POSModelLoader;
import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.lemmatizer.SimpleLemmatizer;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public class WSDHelper {

  protected static TokenizerME tokenizer;
  protected static POSTaggerME tagger;
  protected static SimpleLemmatizer lemmatizer;
  protected static Dictionary dictionary;
  protected static MorphologicalProcessor morph;

  protected static String tokenizerModelPath;
  protected static String taggerModelPath;
  protected static String lemmatizerDictionaryPath;

  // local caches for faster lookup
  private static HashMap<String, Object> stemCache;
  private static HashMap<String, Object> stopCache;
  private static HashMap<String, Object> relvCache;

  private static HashMap<String, Object> englishWords;

  // List of all the PoS tags
  public static String[] allPOS = { "CC", "CD", "DT", "EX", "FW", "IN", "JJ",
      "JJR", "JJS", "LS", "MD", "NN", "NNS", "NNP", "NNPS", "PDT", "POS",
      "PRP", "PRP$", "RB", "RBR", "RBS", "RP", "SYM", "TO", "UH", "VB", "VBD",
      "VBG", "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB" };

  // List of the PoS tags of which the senses are to be extracted
  public static String[] relevantPOS = { "JJ", "JJR", "JJS", "NN", "NNS", "RB",
      "RBR", "RBS", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ" };

  // List of Negation Words
  public static ArrayList<String> negationWords = new ArrayList<String>(
      Arrays.asList("not", "no", "never", "none", "nor", "non"));

  // List of Stop Words
  public static ArrayList<String> stopWords = new ArrayList<String>(
      Arrays.asList("a", "able", "about", "above", "according", "accordingly",
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
          "you're", "yours", "yourself", "yourselves", "you've", "zero"));

  public static HashMap<String, Object> getRelvCache() {
    if (relvCache == null || relvCache.keySet().isEmpty()) {
      relvCache = new HashMap<String, Object>();
      for (String t : relevantPOS) {
        relvCache.put(t, null);
      }
    }
    return relvCache;
  }

  public static HashMap<String, Object> getStopCache() {
    if (stopCache == null || stopCache.keySet().isEmpty()) {
      stopCache = new HashMap<String, Object>();
      for (String s : stopWords) {
        stopCache.put(s, null);
      }
    }
    return stopCache;
  }

  public static HashMap<String, Object> getStemCache() {
    if (stemCache == null || stemCache.keySet().isEmpty()) {
      stemCache = new HashMap<String, Object>();
      for (Object pos : POS.getAllPOS()) {
        stemCache.put(((POS) pos).getKey(), new HashMap());
      }
    }
    return stemCache;
  }

  public static HashMap<String, Object> getEnglishWords() {
    if (englishWords == null || englishWords.keySet().isEmpty()) {
      englishWords = getEnglishWords(lemmatizerDictionaryPath);
    }
    return englishWords;
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
        e.printStackTrace();
      }
    }
    return dictionary;
  }

  public static SimpleLemmatizer getLemmatizer() {
    if (lemmatizer == null) {
      try {
        lemmatizer = new SimpleLemmatizer(new FileInputStream(
            lemmatizerDictionaryPath));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return lemmatizer;
  }

  public static POSTaggerME getTagger() {
    if (tagger == null) {
      tagger = new POSTaggerME(new POSModelLoader().load(new File(
          taggerModelPath)));
    }
    return tagger;
  }

  public static TokenizerME getTokenizer() {
    if (tokenizer == null) {
      try {
        tokenizer = new TokenizerME(new TokenizerModel(new FileInputStream(
            tokenizerModelPath)));
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
    return tokenizer;
  }

  public static TokenizerME loadTokenizer(String path) {
    tokenizerModelPath = path;
    return getTokenizer();
  }

  public static POSTaggerME loadTagger(String path) {
    taggerModelPath = path;
    return getTagger();
  }

  public static SimpleLemmatizer loadLemmatizer(String path) {
    lemmatizerDictionaryPath = path;
    return getLemmatizer();
  }

  /*
   * checks if the word is or contains a number
   */
  public static boolean containsNumbers(String word) {
    return word.matches(".*[0-9].*");
  }

  // Print a text in the console
  public static void printResults(WSDisambiguator disambiguator,
      String[] results) {

    if (results != null) {

      String[] parts;
      String sensekey;
      if (disambiguator instanceof Lesk) {

        Double score;

        for (int i = 0; i < results.length; i++) {
          parts = results[i].split(" ");
          sensekey = parts[1];
          score = Double.parseDouble(parts[2]);
          try {
            print("score : "
                + score
                + " for sense "
                + i
                + " : "
                + sensekey
                + " : "
                + getDictionary().getWordBySenseKey(sensekey).getSynset()
                    .getGloss());
          } catch (JWNLException e) {
            e.printStackTrace();
          }
        }
      } else {
        for (int i = 0; i < results.length; i++) {
          parts = results[i].split(" ");
          sensekey = parts[1];
          try {
            print("sense "
                + i
                + " : "
                + sensekey
                + " : "
                + getDictionary().getWordBySenseKey(sensekey).getSynset()
                    .getGloss());
          } catch (JWNLException e) {
            e.printStackTrace();
          }
        }
      }
    }

  }

  public static void print(Object in) {
    if (in == null) {
      System.out.println("object is null");
    } else {
      System.out.println(in);
    }
  }

  public static void print(Object[] array) {
    if (array == null) {
      System.out.println("object is null");
    } else {
      System.out.println(Arrays.asList(array));
    }
  }

  public static void print(Object[][] array) {
    if (array == null) {
      System.out.println("object is null");
    } else {
      System.out.print("[");
      for (int i = 0; i < array.length; i++) {
        print(array[i]);
        if (i != array.length - 1) {
          System.out.print("\n");
        }
        print("]");
      }
    }
  }

  /**
   * Extract the list of ALL English words
   * 
   * @param dict
   *          this file is the same that is used in the simple Lemmatizer
   *          (i.e.,"en-lemmatizer.dict")
   * 
   * @return a list of all the English words
   */
  public static HashMap<String, Object> getEnglishWords(String dict) {

    HashMap<String, Object> words = new HashMap<String, Object>();

    BufferedReader br = null;

    File file = new File(lemmatizerDictionaryPath);

    if (file.exists()) {

      try {
        br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        while (line != null) {
          line = br.readLine();
          if (line != null) {
            String word = line.split("\\t")[0];
            words.put(word, null);
          }
        }
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        if (br != null) {
          try {
            br.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
      return words;
    } else {
      return null;
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

    ArrayList<String> adjective = new ArrayList<String>(Arrays.asList("JJ",
        "JJR", "JJS"));
    ArrayList<String> adverb = new ArrayList<String>(Arrays.asList("RB", "RBR",
        "RBS"));
    ArrayList<String> noun = new ArrayList<String>(Arrays.asList("NN", "NNS",
        "NNP", "NNPS"));
    ArrayList<String> verb = new ArrayList<String>(Arrays.asList("VB", "VBD",
        "VBG", "VBN", "VBP", "VBZ"));

    if (adjective.contains(posTag))
      return POS.ADJECTIVE;
    else if (adverb.contains(posTag))
      return POS.ADVERB;
    else if (noun.contains(posTag))
      return POS.NOUN;
    else if (verb.contains(posTag))
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
  public static boolean isRelevant(String posTag) {
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
  public static boolean isRelevant(POS pos) {
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

  public static ArrayList<WordPOS> getAllRelevantWords(String[] sentence) {

    ArrayList<WordPOS> relevantWords = new ArrayList<WordPOS>();

    String[] tags = WSDHelper.getTagger().tag(sentence);

    for (int i = 0; i < sentence.length; i++) {
      if (!WSDHelper.getStopCache().containsKey(sentence[i])) {
        if (WSDHelper.getRelvCache().containsKey(tags[i])) {
          relevantWords.add(new WordPOS(sentence[i], tags[i]));
        }

      }
    }
    return relevantWords;
  }

  public static ArrayList<WordPOS> getAllRelevantWords(WordToDisambiguate word) {
    ArrayList<WordPOS> relevantWords = new ArrayList<WordPOS>();

    String[] tags = WSDHelper.getTagger().tag(word.getSentence());

    for (int i = 0; i < word.getSentence().length; i++) {
      if (!WSDHelper.getStopCache().containsKey(word.getSentence()[i])) {
        if (WSDHelper.getRelvCache().containsKey(tags[i])) {
          WordPOS wordpos = new WordPOS(word.getSentence()[i], tags[i]);
          if (i == word.getWordIndex()) {
            wordpos.isTarget = true;
          }
          relevantWords.add(wordpos);
        }

      }
    }
    return relevantWords;
  }

  public static ArrayList<WordPOS> getRelevantWords(WordToDisambiguate word,
      int winBackward, int winForward) {

    ArrayList<WordPOS> relevantWords = new ArrayList<WordPOS>();

    String[] sentence = word.getSentence();
    String[] tags = WSDHelper.getTagger().tag(sentence);

    int index = word.getWordIndex();

    for (int i = index - winBackward; i <= index + winForward; i++) {

      if (i >= 0 && i < sentence.length && i != index) {
        if (!WSDHelper.getStopCache().containsKey(sentence[i])) {

          if (WSDHelper.getRelvCache().containsKey(tags[i])) {
            relevantWords.add(new WordPOS(sentence[i], tags[i]));
          }

        }
      }
    }
    return relevantWords;
  }

  /**
   * Stem a single word with WordNet dictionnary
   * 
   * @param wordToStem
   *          word to be stemmed
   * @return stemmed list of words
   */
  public static ArrayList<String> StemWordWithWordNet(WordPOS wordToStem) {
    if (wordToStem == null)
      return null;
    ArrayList<String> stems = new ArrayList<String>();
    try {
      for (Object pos : POS.getAllPOS()) {
        stems.addAll(WSDHelper.getMorph().lookupAllBaseForms((POS) pos,
            wordToStem.getWord()));
      }

      if (stems.size() > 0)
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
   * Stem a single word tries to look up the word in the stemCache HashMap If
   * the word is not found it is stemmed with WordNet and put into stemCache
   * 
   * @param wordToStem
   *          word to be stemmed
   * @return stemmed word list, null means the word is incorrect
   */
  public static ArrayList<String> Stem(WordPOS wordToStem) {

    // check if we already cached the stem map
    HashMap posMap = (HashMap) WSDHelper.getStemCache().get(
        wordToStem.getPOS().getKey());

    // don't check words with digits in them
    if (WSDHelper.containsNumbers(wordToStem.getWord())) {
      return null;
    }

    ArrayList<String> stemList = (ArrayList<String>) posMap.get(wordToStem
        .getWord());
    if (stemList != null) { // return it if we already cached it
      return stemList;

    } else { // unCached list try to stem it
      stemList = StemWordWithWordNet(wordToStem);
      if (stemList != null) {
        // word was recognized and stemmed with wordnet:
        // add it to cache and return the stemmed list
        posMap.put(wordToStem.getWord(), stemList);
        WSDHelper.getStemCache().put(wordToStem.getPOS().getKey(), posMap);
        return stemList;
      } else { // could not be stemmed add it anyway (as incorrect with null
               // list)
        posMap.put(wordToStem.getWord(), null);
        WSDHelper.getStemCache().put(wordToStem.getPOS().getKey(), posMap);
        return null;
      }
    }
  }
}
