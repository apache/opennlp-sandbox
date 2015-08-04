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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import opennlp.tools.disambiguator.lesk.Lesk;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;

public class Constants {

  private static String resourcesFolder = "src\\test\\resources\\";

  private static String englishDict = resourcesFolder
      + "models\\en-lemmatizer.dict";

  public static String osPathChar = "\\";

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

  // Print a text in the console
//Print a text in the console
 public static void printResults(WSDisambiguator disambiguator,
     String[] results) {

   if (results != null) {

     String[] parts;
     String sensekey;
     if (disambiguator instanceof Lesk) {

       Double score;

       for (String result : results) {
         parts = result.split(" ");
         sensekey = parts[1];
         score = Double.parseDouble(parts[2]);
         try {
           Constants.print("score : "
               + score
               + " for : "
               + Loader.getDictionary().getWordBySenseKey(sensekey)
                   .getSynset().getGloss());
         } catch (JWNLException e) {
           e.printStackTrace();
         }
       }
     } else {
       for (String result : results) {
         parts = result.split(" ");
         sensekey = parts[1];
         try {
           Constants.print("sense : "
               + Loader.getDictionary().getWordBySenseKey(sensekey)
                   .getSynset().getGloss());
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

    File file = new File(englishDict);

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
    }

    return words;
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

}
