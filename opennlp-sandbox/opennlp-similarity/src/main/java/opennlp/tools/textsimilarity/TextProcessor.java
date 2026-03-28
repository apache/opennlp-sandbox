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

package opennlp.tools.textsimilarity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.Pair;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;

public class TextProcessor {

  private static final Logger LOG = Logger
      .getLogger("opennlp.tools.textsimilarity.TextProcessor");

  static final String[] ABBREVS = { "mr.", "mrs.", "sen.", "rep.", "gov.",
      "miss.", "dr.", "oct.", "nov.", "jan.", "feb.", "mar.", "apr.", "may",
      "jun.", "jul.", "aug.", "sept." };

  public static void removeCommonPhrases(ArrayList<String> segments) {

    ArrayList<Pair<List<String>, Map<String, HashSet<Integer>>>> docs = new ArrayList<>();
    // tokenize each segment
    for (String s : segments) {
      Pair<List<String>, Map<String, HashSet<Integer>>> tokPos = buildTokenPositions(s);
      docs.add(tokPos);
    }

    HashMap<String, HashSet<Integer>> commonSegments = new HashMap<>();
    // now we have all documents and the token positions
    for (int i = 0; i < docs.size(); i++) {
      Pair<List<String>, Map<String, HashSet<Integer>>> objA = docs.get(i);
      for (int k = i + 1; k < docs.size(); k++) {
        Pair<List<String>, Map<String, HashSet<Integer>>> objB = docs.get(k);
        HashSet<String> segs = extractCommonSegments(objA, objB, 4);
        for (String seg : segs) {
          // System.out.println(seg);
          if (commonSegments.containsKey(seg)) {
            HashSet<Integer> docIds = commonSegments.get(seg);
            docIds.add(i);
            docIds.add(k);
            commonSegments.put(seg, docIds);
          } else {
            HashSet<Integer> docIds = new HashSet<>();
            docIds.add(i);
            docIds.add(k);
            commonSegments.put(seg, docIds); // set frequency to two, since both
                                             // these docs contain this
            // segment
          }
        }
      }
    }

    System.out.println(segments.size() + " docs");
    // now we have the segments and their frequencies
    for (String seg : commonSegments.keySet()) {
      System.out.println(seg + ":" + commonSegments.get(seg).size());
    }
  }

  public static HashSet<String> extractCommonSegments(String s1, String s2,
      Integer segSize) {
    Pair<List<String>, Map<String, HashSet<Integer>>> o1 = buildTokenPositions(s1);
    Pair<List<String>, Map<String, HashSet<Integer>>> o2 = buildTokenPositions(s2);

    return extractCommonSegments(o1, o2, segSize);
  }

  private static HashSet<String> extractCommonSegments(
      Pair<List<String>, Map<String, HashSet<Integer>>> objA,
      Pair<List<String>, Map<String, HashSet<Integer>>> objB, Integer segSize) {

    HashSet<String> commonSegments = new HashSet<>();

    List<String> tokensA = objA.getFirst();

    Map<String, HashSet<Integer>> tokenPosB = objB.getSecond();

    HashSet<Integer> lastPositions = null;
    int segLength = 1;
    StringBuilder segmentStr = new StringBuilder();

    for (String token : tokensA) {
      HashSet<Integer> positions;
      // if ((positions = tokenPosB.get(token)) != null &&
      // !token.equals("<punc>") &&
      // !StopList.getInstance().isStopWord(token) && token.length()>1) {
      if ((positions = tokenPosB.get(token)) != null) {
        // we have a list of positions
        if (lastPositions != null) {
          // see if there is overlap in positions
          if (hasNextPosition(lastPositions, positions)) {
            segLength++;

            commonSegments.remove(segmentStr.toString().trim());
            segmentStr.append(" ");
            segmentStr.append(token);
            if (StringUtils.countMatches(segmentStr.toString(), " ") >= segSize) {
              commonSegments.add(segmentStr.toString().trim());
            }
            lastPositions = positions;

          } else {
            // did not find segment, reset
            segLength = 1;
            segmentStr.setLength(0);
            lastPositions = null;
          }
        } else {
          lastPositions = positions;
          segmentStr.append(" ");
          segmentStr.append(token);
        }
      } else {
        // did not find segment, reset
        segLength = 1;
        segmentStr.setLength(0);
        lastPositions = null;
      }
    }

    return commonSegments;
  }

  private static boolean hasNextPosition(HashSet<Integer> positionsA,
      HashSet<Integer> positionsB) {
    boolean retVal = false;
    for (Integer pos : positionsA) {
      Integer nextIndex = pos + 1;
      if (positionsB.contains(nextIndex)) {
        retVal = true;
        break;
      }
    }
    return retVal;
  }

  public static Pair<List<String>, Map<String, HashSet<Integer>>> buildTokenPositions(
      String s) {

    String[] toks = StringUtils.split(s);
    List<String> list = Arrays.asList(toks);
    ArrayList<String> tokens = new ArrayList<>(list);

    Map<String, HashSet<Integer>> theMap = new HashMap<>();
    for (int i = 0; i < tokens.size(); i++) {
      HashSet<Integer> pos;
      String token = tokens.get(i);
      if ((pos = theMap.get(token)) != null) {
        pos.add(i);
      } else {
        pos = new HashSet<>();
        pos.add(i);
      }
      theMap.put(token, pos);
    }

    return new Pair<>(tokens, theMap);
  }

  public static boolean isStringAllPunc(String token) {

    for (int i = 0; i < token.length(); i++) {
      if (Character.isLetterOrDigit(token.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Splits input text into sentences.
   * 
   * @param text
   *          Input text
   * @return List of sentences
   */

  public static ArrayList<String> splitToSentences(String text) {

    ArrayList<String> sentences = new ArrayList<>();
    if (text.trim().length() > 0) {
      String s = "[\\?!\\.]\"?[\\s+][A-Z0-9i]";
      text += " XOXOX.";
      Pattern p = Pattern.compile(s, Pattern.MULTILINE);
      Matcher m = p.matcher(text);
      int idx = 0;
      StringBuilder cand = new StringBuilder();

      // while(m.find()){
      // System.out.println(m.group());
      // }

      while (m.find()) {
        cand.append(" ").append(text.substring(idx, m.end() - 1).trim());
        boolean hasAbbrev = false;

        for (String abbrev : ABBREVS) {
          if (cand.toString().toLowerCase().endsWith(abbrev)) {
            hasAbbrev = true;
            break;
          }
        }

        if (!hasAbbrev) {
          sentences.add(cand.toString().trim());
          cand = new StringBuilder();
        }
        idx = m.end() - 1;
      }

      if (idx < text.length()) {
        sentences.add(text.substring(idx).trim());
      }
      if (sentences.size() > 0) {
        sentences.set(sentences.size() - 1, sentences.get(sentences.size() - 1)
            .replace(" XOXOX.", ""));
      }
    }
    return sentences;
  }

  private static boolean isSafePunc(char[] chars, int idx) {

    if (true) {
      return false;
    }

    boolean retVal = false;
    int c = chars[idx];

    // are we dealing with a safe character
    if (c == 39 || c == 45 || c == 8211 || c == 8212 || c == 145 || c == 146
        || c == 8216 || c == 8217) {
      // if we are at end or start of array, then character is not good
      if (idx == chars.length - 1 || idx == 0) {
        return false;
      }

      // check to see if previous and next character are acceptable
      if (Character.isLetterOrDigit(chars[idx + 1])
          && Character.isLetterOrDigit(chars[idx - 1])) {
        return true;
      }
    }

    return retVal;
  }

  public static String removePunctuation(String sentence) {
    List<String> toks = fastTokenize(sentence, false);
    return toks.toString().replace('[', ' ').replace(']', ' ')
        .replace(',', ' ').replace("  ", " ");
  }

  public static ArrayList<String> fastTokenize(String txt, boolean retainPunc) {
    ArrayList<String> tokens = new ArrayList<>();
    if (StringUtils.isEmpty(txt)) {
      return tokens;
    }

    StringBuilder tok = new StringBuilder();
    char[] chars = txt.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (Character.isLetterOrDigit(c) || isSafePunc(chars, i)) {
        tok.append(c);
      } else if (Character.isWhitespace(c)) {
        if (tok.length() > 0) {
          tokens.add(tok.toString());
          tok.setLength(0);
        }
      } else {
        if (tok.length() > 0) {
          tokens.add(tok.toString());
          tok.setLength(0);
        }
        if (retainPunc) {
          tokens.add("<punc>");
        }
      }
    }

    if (tok.length() > 0) {
      tokens.add(tok.toString());
      tok.setLength(0);
    }
    return tokens;
  }

  public static String convertTokensToString(ArrayList<String> tokens) {
    StringBuilder b = new StringBuilder();
    for (String s : tokens) {
      b.append(s);
      b.append(" ");
    }

    return b.toString().trim();
  }

  public static Hashtable<String, Integer> getAllBigrams(String[] tokens,
      boolean retainPunc) {
    // convert to ArrayList and pass on
    ArrayList<String> f = new ArrayList<>(Arrays.asList(tokens));
    return getAllBigrams(f, retainPunc);
  }

  public static Hashtable<String, Integer> getAllBigrams(
      ArrayList<String> tokens, boolean retainPunc) {
    Hashtable<String, Integer> bGramCandidates = new Hashtable<>();
    ArrayList<String> r = new ArrayList<>();
    for (int i = 0; i < tokens.size() - 1; i++) {
      String b = tokens.get(i) + " " + tokens.get(i + 1);
      b = b.toLowerCase();
      // don't add punc tokens
      if (b.contains("<punc>") && !retainPunc)
        continue;

      int freq = 1;
      if (bGramCandidates.containsKey(b)) {
        freq = bGramCandidates.get(b) + 1;
      }
      bGramCandidates.put(b, freq);
    }
    return bGramCandidates;
  }

  public static Hashtable<String, Float> getAllBigramsStopWord(
      ArrayList<String> tokens, boolean retainPunc) {

    Hashtable<String, Float> bGramCandidates = new Hashtable<>();
    try {
      ArrayList<String> r = new ArrayList<>();
      for (int i = 0; i < tokens.size() - 1; i++) {
        String p1 = tokens.get(i).toLowerCase();
        String p2 = tokens.get(i + 1).toLowerCase();
        // check to see if stopword
        /*
         * if(StopList.getInstance().isStopWord(p1.trim()) ||
         * StopList.getInstance().isStopWord(p2.trim())){ continue; }
         */

        String buf = p1 + " " + p2;
        String b = buf.toLowerCase();
        // don't add punc tokens
        if (b.contains("<punc>") && !retainPunc)
          continue;

        float freq = 1;
        if (bGramCandidates.containsKey(b)) {
          freq = bGramCandidates.get(b) + 1;
        }
        bGramCandidates.put(b, freq);
      }
    } catch (Exception e) {
      LOG.severe("Problem getting stoplist");
    }

    return bGramCandidates;
  }

  public static ArrayList<String> tokenizeAndStemWithPunctuation(String txt) {
    // tokenize
    ArrayList<String> tokens = fastTokenize(txt, true);
    for (int i = 0; i < tokens.size(); i++) {
      if (!tokens.get(i).equals("<punc>")) {
        tokens.set(i, TextProcessor.stemTerm(tokens.get(i)));
      }
    }

    return tokens;
  }

  public static String trimPunctuationFromStart(String text) {
    try {
      int start = 0;
      int end = text.length() - 1;
      // trim from the start
      for (int i = 0; i < text.length(); i++) {
        if (!isPunctuation(text.charAt(i)))
          break;
        start++;
      }
      if (start == text.length()) {
        return "";
      }

      return text.substring(start, end + 1);
    } catch (RuntimeException e) {
      LOG.severe("RuntimeException " + e);
      e.printStackTrace();
      return "";
    }
  }

  public static String trimPunctuation(String text) {
    try {
      int start = 0;
      int end = text.length() - 1;
      // trim from the start
      for (int i = 0; i < text.length(); i++) {
        if (!isPunctuation(text.charAt(i)))
          break;
        start++;
      }
      if (start == text.length()) {
        return "";
      }
      // trim for the end
      for (int i = text.length() - 1; i >= 0; i--) {
        if (!isPunctuation(text.charAt(i)))
          break;
        end--;
      }

      return text.substring(start, end + 1);
    } catch (RuntimeException e) {
      LOG.severe("RuntimeException " + e);
      return "";
    }
  }

  public static boolean isPunctuation(char c) {
    return !Character.isLetterOrDigit(c);
  }

  public static String stemAndClean(String token) {
    token = token.trim();
    token = token.toLowerCase();
    if (token.length() == 0) {
      return "";
    }
    if (isPunctuation(token.substring(token.length() - 1))) {
      if (token.length() == 1) {
        return token;
      }
      token = token.substring(0, token.length() - 1);
      if (token.length() == 0) {
        return "";
      }
    }
    if (isPunctuation(token)) {
      if (token.length() == 1) {
        return token;
      }
      token = token.substring(1);
      if (token.length() == 0) {
        return "";
      }
    }

    return new PorterStemmer().stem(token);
  }

  public static String cleanToken(String token) {
    token = token.trim();
    // token = token.toLowerCase();
    if (token.length() == 0) {
      return "";
    }
    if (isPunctuation(token.substring(token.length() - 1))) {
      if (token.length() == 1) {
        return token;
      }
      token = token.substring(0, token.length() - 1);
      if (token.length() == 0) {
        return "";
      }
    }
    if (isPunctuation(token)) {
      if (token.length() == 1) {
        return token;
      }
      token = token.substring(1);
      if (token.length() == 0) {
        return "";
      }
    }

    return token;
  }

  public static boolean isAllNumbers(String str) {
    return str.matches("^\\d*$");
  }

  private static boolean isPunctuation(String str) {
    if (str.length() < 1) {
      return false;
    } else {
      return str.substring(0, 1).matches("[^\\d\\w\\s]");
    }
  }

  public static String stemTerm(String term) {
    term = stripToken(term);
    Stemmer st = new PorterStemmer();
    return st.stem(term).toString();
  }

  public static String generateFingerPrint(String s) {
    String hash = "";

    if (s.length() > 0) {
      MessageDigest md = null;
      try {
        md = MessageDigest.getInstance("SHA"); // step 2
      } catch (NoSuchAlgorithmException e) {
        LOG.severe("NoSuchAlgorithmException " + 2);
      }
      md.update(s.getBytes(StandardCharsets.UTF_8)); // step 3
      byte[] raw = md.digest();
      hash = null; // (new BASE64Encoder()).encode(raw);
    }
    return hash;
  }

  public static String generateUrlSafeFingerPrint(String s) {
    String signature = TextProcessor.generateFingerPrint(s);
    return signature.replaceAll("[?/]", "+");
  }

  public static String generateFingerPrintForHistogram(String s)
      throws Exception {

    Hashtable<String, Integer> tokenHash = new Hashtable<>();
    // ArrayList tokens = TextProcessor.tokenizeWithPunctuation(s);
    ArrayList<String> tokens = TextProcessor.fastTokenize(s, true);

    for (String t : tokens) {
      String tokenLower = t.toLowerCase();

      if ("<punc>".equals(tokenLower)) {
        continue;
      }
      if ("close_a".equals(tokenLower)) {
        continue;
      }
      if ("open_a".equals(tokenLower)) {
        continue;
      }
      String stemmedToken = TextProcessor.stemTerm(tokenLower);

      if (tokenHash.containsKey(stemmedToken)) {
        int freq = tokenHash.get(stemmedToken);
        freq++;
        tokenHash.put(stemmedToken, freq);
      } else {
        tokenHash.put(stemmedToken, 1);
      }
    }

    // now we have histogram, lets write it out
    StringBuilder hashString = new StringBuilder();
    Enumeration<String> en = tokenHash.keys();
    while (en.hasMoreElements()) {
      String t = en.nextElement();
      int freq = tokenHash.get(t);
      hashString.append(t).append(freq);
    }

    // log.info(hashString);
    String hash = "";

    if (hashString.length() > 0) {
      MessageDigest md;
      try {
        md = MessageDigest.getInstance("SHA"); // step 2
      } catch (NoSuchAlgorithmException e) {
        LOG.severe("NoSuchAlgorithmException " + e);
        throw new Exception(e.getMessage());
      }
      md.update(hashString.toString().getBytes(StandardCharsets.UTF_8)); // step 3
      byte[] raw = md.digest();
      hash = null; // (new BASE64Encoder()).encode(raw);
    }
    return hash;
  }

  public static String stripToken(String token) {
    if (token.endsWith("\'s") || token.endsWith("�s")) {
      token = token.substring(0, token.length() - 2);
    }
    return token;
  }

  public static HashMap<String, Integer> getUniqueTokenIndex(List<String> tokens) {
    HashMap<String, Integer> m = new HashMap<>();

    for (String s : tokens) {
      s = s.toLowerCase();
      if (m.containsKey(s)) {
        Integer f = m.get(s);
        f++;
        m.put(s, f);
      } else {
        m.put(s, 1);
      }
    }

    return m;

  }

  public static String generateSummary(String txt, String title, int numChars,
      boolean truncateInSentence) {
    StringBuilder finalSummary;

    try {
      String[] puncChars = { ":", "--", "PM", "MST", "EST", "CST", "PST", "GMT", "AM", "  " };

      txt = txt.replace(" | ", " ");
      txt = txt.replace(" |", " ");
      ArrayList<String> sentences = TextProcessor.splitToSentences(txt);
      // System.out.println("Sentences are:");
      StringBuilder sum = new StringBuilder();
      int cnt = 0;
      int lCnt = 0;
      for (String s : sentences) {
        cnt++;
        // System.out.println(s + "\n");
        s = trimSentence(s, title);
        // see if sentence has a time in it
        // boolean containsTime = s.co("[0-9]");
        if (s.length() > 60 && !s.contains("By") && !s.contains("Page")
            && !s.contains(">>") && Character.isUpperCase(s.charAt(0))) {
          // System.out.println("cleaned: " + s + "\n");
          if (Math.abs(cnt - lCnt) != 1 && lCnt != 0) {

            if (sum.toString().endsWith(".")) {
              sum.append("..");
            } else {
              sum.append("...");
            }
          } else {
            sum.append(" ");
          }
          sum.append(s.trim());
          lCnt = cnt;
        }
        if (sum.length() > numChars) {
          break;
        }
      }

      finalSummary = new StringBuilder(sum.toString().trim());

      if (truncateInSentence) {
        finalSummary = new StringBuilder(truncateTextOnSpace(finalSummary.toString(), numChars));
        int numPeriods = countTrailingPeriods(finalSummary.toString());

        if (numPeriods < 3 && finalSummary.length() > 0) {
          for (int i = 0; i < 3 - numPeriods; i++) {
            finalSummary.append(".");
          }
        }
      } else {
        // trim final period
        if (finalSummary.toString().endsWith("..")) {
          finalSummary = new StringBuilder(finalSummary.substring(0, finalSummary.length() - 2));
        }
      }
      // check to see if we have anything, if not, return the full content
      if (finalSummary.toString().trim().length() < 5) {
        finalSummary = new StringBuilder(txt);
      }
      // see if we have a punctuation character in the first 30 chars
      int highestIdx = -1;
      int sIdx = Math.min(finalSummary.length() - 1, 45);
      for (String p : puncChars) {
        int idx = finalSummary.toString().trim().substring(0, sIdx).lastIndexOf(p);
        if (idx > highestIdx && idx < 45) {
          highestIdx = idx + p.length();
        }
      }

      if (highestIdx > -1) {
        finalSummary = new StringBuilder(finalSummary.substring(highestIdx));
      }

      int closeParenIdx = finalSummary.indexOf(")");
      int openParenIdx = finalSummary.indexOf("(");
      // if(closeParenIdx < )
      if (closeParenIdx != -1 && closeParenIdx < 15
          && (openParenIdx == -1 || openParenIdx > closeParenIdx)) {
        finalSummary = new StringBuilder(finalSummary.substring(closeParenIdx + 1).trim());
      }

      finalSummary = new StringBuilder(trimPunctuationFromStart(finalSummary.toString()));

      // check to see if we have anything, if not, return the full content
      if (finalSummary.toString().trim().length() < 5) {
        finalSummary = new StringBuilder(txt);
      }

    } catch (Exception e) {
      LOG.severe("Problem forming summary for: " + txt);
      LOG.severe("Using full text for the summary" + e);
      finalSummary = new StringBuilder(txt);
    }

    return finalSummary.toString().trim();
  }

  public static String truncateTextOnSpace(String txt, int numChars) {
    String retVal = txt;
    if (txt.length() > numChars) {
      String temp = txt.substring(0, numChars);
      // loop backwards to find last space
      int lastSpace = -1;
      for (int i = temp.length() - 1; i >= 0; i--) {
        if (Character.isWhitespace(temp.charAt(i))) {
          lastSpace = i;
          break;
        }
      }
      if (lastSpace != -1) {
        retVal = temp.substring(0, lastSpace);
      }
    }
    return retVal;
  }

  public static int countTrailingPeriods(String txt) {
    int retVal = 0;
    if (txt.length() > 0) {
      for (int i = txt.length() - 1; i >= 0; i--) {
        if (String.valueOf(txt.charAt(i)).equals(".")) {
          retVal++;
        } else {
          break;
        }
      }
    }
    return retVal;
  }

  public static String trimSentence(String txt, String title) {

    // iterate backwards looking for the first all cap word.
    int numCapWords = 0;
    int firstIdx = -1;
    String cleaned = txt;
    for (int i = txt.length() - 1; i >= 0; i--) {
      if (Character.isUpperCase(txt.charAt(i))) {
        if (numCapWords == 0) {
          firstIdx = i;
        }
        numCapWords++;
      } else {
        numCapWords = 0;
        firstIdx = -1;
      }
      if (numCapWords > 3) {
        if (firstIdx != -1) {
          cleaned = txt.substring(firstIdx + 1);
          break;
        }
      }
    }

    txt = cleaned;

    // now scrub the start of the string
    int idx = 0;
    for (int i = 0; i < txt.length() - 1; i++) {
      if (!Character.isUpperCase(txt.charAt(i))) {
        idx++;
      } else {
        break;
      }
    }
    txt = txt.substring(idx);

    // scrub the title
    if (title.trim().length() > 0 && txt.contains(title.trim())) {
      txt = txt.substring(txt.indexOf(title.trim()) + title.trim().length() - 1);
    }

    // scrub before first -
    if (txt.contains(" � ")) {
      txt = txt.substring(txt.indexOf(" � ") + 3);
    }
    if (txt.contains(" - ")) {
      txt = txt.substring(txt.indexOf(" - ") + 3);
    }
    if (txt.contains("del.icio.us")) {
      txt = txt.substring(txt.indexOf("del.icio.us") + "del.icio.us".length());
    }

    return txt;
  }

  public static String removeStopListedTermsAndPhrases(String txt) {
    HashSet<String> stopPhrases = null;
    /*
     * try{ StopList sl = StopList.getInstance(); stopPhrases =
     * sl.getStopListMap("EXTRACTOR"); }catch(Exception e){
     * log.severe("Problem loading stoplists"); }
     */
    // segment into top 20% and bottom 20%
    int startIdx = txt.length() / 4;
    String startPart = txt.substring(0, startIdx);

    int endIdx = txt.length() - (txt.length() / 4);
    String endPart = txt.substring(endIdx, txt.length());

    String middlePart = txt.substring(startIdx, endIdx);

    // iterate through the stop words and start removing
    for (Object o : stopPhrases.toArray()) {
      String p = (String) o;
      int idx = startPart.indexOf(p);
      if (idx != -1) {
        startPart = startPart.substring(idx + p.length());
      }
      idx = endPart.indexOf(p);
      if (idx != -1) {
        endPart = endPart.substring(0, idx);
      }
    }

    // combine these sections
    String retVal = startPart + middlePart + endPart;
    return retVal.trim();
  }

  public static List<String> extractUrlsFromText(String txt) {
    List<String> urls = new ArrayList<>();
    // tokenize and iterate
    String[] tokens = txt.split("\\s+");
    for (String t : tokens) {
      if (t.startsWith("http://")) {
        if (!urls.contains(t)) {
          urls.add(t);
        }
      }
    }

    return urls;
  }

  public static List<String> findCommonTokens(List<String> segments) {
    List<String> commonTokens = new ArrayList<>();

    if (segments.size() > 1) {
      List<String> allTokens = new ArrayList<>();
      for (String s : segments) {
        String[] tks = s.split("\\s+");
        List<String> tokens = Arrays.asList(tks);
        HashMap<String, Integer> ut = TextProcessor.getUniqueTokenIndex(tokens);
        allTokens.addAll(ut.keySet());
      }
      Map<String, Integer> uniqueTokens = TextProcessor.getUniqueTokenIndex(allTokens);
      for (String t : uniqueTokens.keySet()) {
        Integer freq = uniqueTokens.get(t);
        if (freq == segments.size()) {
          commonTokens.add(t);
        }
      }
    }
    return commonTokens;
  }

  public static int numTokensInString(String txt) {
    int retVal = 0;
    if (txt != null && txt.trim().length() > 0) {
      retVal = txt.trim().split("\\s+").length;
    }
    return retVal;
  }

  public static String defragmentText(String str) {

    if (StringUtils.isNotEmpty(str)) {
      str = str.replaceAll("&nbsp;", " "); // replace &nbsp; with spaces
      str = str.replaceAll("<br />", "<br/>"); // normalize break tag
      str = str.replaceAll("\\s+", " "); // replace multiple white spaces with
                                         // single space

      // remove empty paragraphs - would be nice to have single regex for this
      str = str.replaceAll("<p> </p>", "");
      str = str.replaceAll("<p></p>", "");
      str = str.replaceAll("<p/>", "");

      str = str.replaceAll("<strong><br/></strong>", "<br/>"); // escape strong
                                                               // tag if
                                                               // surrounding
                                                               // break tag
      str = str.replaceAll("(<br/>)+", "<br/><br/>"); // replace multiple break
                                                      // tags with 2 break tags
      str = str.replaceAll("<p><br/>", "<p>"); // replace paragraph followed by
                                               // break with just a paragraph
      // element
    }

    return str;
  }
}
