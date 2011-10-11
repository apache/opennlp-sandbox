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

import java.io.UnsupportedEncodingException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zvents.ce.common.util.Pair;

public class TextProcessor {

  private static final Logger LOG = LoggerFactory
      .getLogger(TextProcessor.class);

  static final String[] abbrevs = { "mr.", "mrs.", "sen.", "rep.", "gov.",
      "miss.", "dr.", "oct.", "nov.", "jan.", "feb.", "mar.", "apr.", "may",
      "jun.", "jul.", "aug.", "sept." };

  public static void removeCommonPhrases(ArrayList<String> segments) {

    ArrayList<Pair<List<String>, Map<String, HashSet<Integer>>>> docs = new ArrayList<Pair<List<String>, Map<String, HashSet<Integer>>>>();
    // tokenize each segment
    for (int i = 0; i < segments.size(); i++) {
      String s = segments.get(i);

      Pair<List<String>, Map<String, HashSet<Integer>>> tokPos = buildTokenPositions(s);
      docs.add(tokPos);
    }

    HashMap<String, HashSet<Integer>> commonSegments = new HashMap<String, HashSet<Integer>>();
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
            HashSet<Integer> docIds = new HashSet<Integer>();
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

    HashSet<String> commonSegments = new HashSet<String>();

    List<String> tokensA = objA.getFirst();

    Map<String, HashSet<Integer>> tokenPosB = objB.getSecond();

    HashSet<Integer> lastPositions = null;
    int segLength = 1;
    StringBuffer segmentStr = new StringBuffer();

    for (int i = 0; i < tokensA.size(); i++) {
      String token = tokensA.get(i);
      HashSet<Integer> positions = null;
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
    ArrayList<String> tokens = new ArrayList<String>(list);

    Map<String, HashSet<Integer>> theMap = new HashMap<String, HashSet<Integer>>();
    for (int i = 0; i < tokens.size(); i++) {
      HashSet<Integer> pos = null;
      String token = tokens.get(i);
      if ((pos = theMap.get(token)) != null) {
        pos.add(i);
      } else {
        pos = new HashSet<Integer>();
        pos.add(i);
      }
      theMap.put(token, pos);
    }

    return new Pair<List<String>, Map<String, HashSet<Integer>>>(tokens, theMap);
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
   * @param txt
   *          Input text
   * @return List of sentences
   */

  public static ArrayList<String> splitToSentences(String text) {

    ArrayList<String> sentences = new ArrayList<String>();
    if (text.trim().length() > 0) {
      String s = "[\\?!\\.]\"?[\\s+][A-Z0-9i]";
      text += " XOXOX.";
      Pattern p = Pattern.compile(s, Pattern.MULTILINE);
      Matcher m = p.matcher(text);
      int idx = 0;
      String cand = "";

      // while(m.find()){
      // System.out.println(m.group());
      // }

      while (m.find()) {
        cand += " " + text.substring(idx, m.end() - 1).trim();
        boolean hasAbbrev = false;

        for (int i = 0; i < abbrevs.length; i++) {
          if (cand.toLowerCase().endsWith(abbrevs[i])) {
            hasAbbrev = true;
            break;
          }
        }

        if (!hasAbbrev) {
          sentences.add(cand.trim());
          cand = "";
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

  public static ArrayList<String> fastTokenize(String txt, boolean retainPunc) {
    ArrayList<String> tokens = new ArrayList<String>();
    if (StringUtils.isEmpty(txt)) {
      return tokens;
    }

    StringBuffer tok = new StringBuffer();
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
    StringBuffer b = new StringBuffer();
    b.append("");
    for (String s : tokens) {
      b.append(s);
      b.append(" ");
    }

    return b.toString().trim();
  }

  public static Hashtable<String, Integer> getAllBigrams(String[] tokens,
      boolean retainPunc) {
    // convert to ArrayList and pass on
    ArrayList<String> f = new ArrayList<String>();
    for (int i = 0; i < tokens.length; i++) {
      f.add(tokens[i]);
    }
    return getAllBigrams(f, retainPunc);
  }

  public static Hashtable<String, Integer> getAllBigrams(
      ArrayList<String> tokens, boolean retainPunc) {
    Hashtable<String, Integer> bGramCandidates = new Hashtable<String, Integer>();
    ArrayList<String> r = new ArrayList<String>();
    for (int i = 0; i < tokens.size() - 1; i++) {
      String b = (String) tokens.get(i) + " " + (String) tokens.get(i + 1);
      b = b.toLowerCase();
      // don't add punc tokens
      if (b.indexOf("<punc>") != -1 && !retainPunc)
        continue;

      int freq = 1;
      if (bGramCandidates.containsKey(b)) {
        freq = ((Integer) bGramCandidates.get(b)).intValue() + 1;
      }
      bGramCandidates.put(b, new Integer(freq));
    }
    return bGramCandidates;
  }

  public static Hashtable<String, Float> getAllBigramsStopWord(
      ArrayList<String> tokens, boolean retainPunc) {

    Hashtable<String, Float> bGramCandidates = new Hashtable<String, Float>();
    try {
      ArrayList<String> r = new ArrayList<String>();
      for (int i = 0; i < tokens.size() - 1; i++) {
        String p1 = (String) tokens.get(i).toLowerCase();
        String p2 = (String) tokens.get(i + 1).toLowerCase();
        // check to see if stopword
        /*
         * if(StopList.getInstance().isStopWord(p1.trim()) ||
         * StopList.getInstance().isStopWord(p2.trim())){ continue; }
         */

        StringBuffer buf = new StringBuffer();
        buf.append(p1);
        buf.append(" ");
        buf.append(p2);
        String b = buf.toString().toLowerCase();
        // don't add punc tokens
        if (b.indexOf("<punc>") != -1 && !retainPunc)
          continue;

        float freq = 1;
        if (bGramCandidates.containsKey(b)) {
          freq = bGramCandidates.get(b) + 1;
        }
        bGramCandidates.put(b, freq);
      }
    } catch (Exception e) {
      LOG.error("Problem getting stoplist");
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
      LOG.error("RuntimeException ", e);
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
      LOG.error("RuntimeException ", e);
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

    Stemmer st = new Stemmer();
    st.add(token.toCharArray(), token.length());
    st.stem();
    return st.toString();
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
    Stemmer st = new Stemmer();
    st.add(term.toCharArray(), term.length());
    st.stem();
    return st.toString();
  }

  public static String generateFingerPrint(String s) {
    String hash = "";

    if (s.length() > 0) {
      MessageDigest md = null;
      try {
        md = MessageDigest.getInstance("SHA"); // step 2
      } catch (NoSuchAlgorithmException e) {
        LOG.error("NoSuchAlgorithmException", 2);
      }
      try {
        md.update(s.getBytes("UTF-8")); // step 3
      } catch (UnsupportedEncodingException e) {
        LOG.error("UnsupportedEncodingException", e);
      }
      byte raw[] = md.digest();
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

    Hashtable tokenHash = new Hashtable();
    // ArrayList tokens = TextProcessor.tokenizeWithPunctuation(s);
    ArrayList tokens = TextProcessor.fastTokenize(s, true);

    for (Object t : tokens) {
      String tokenLower = ((String) (t)).toLowerCase();

      if (tokenLower == "<punc>") {
        continue;
      }
      if (tokenLower == "close_a") {
        continue;
      }
      if (tokenLower == "open_a") {
        continue;
      }
      String stemmedToken = TextProcessor.stemTerm(tokenLower);

      if (tokenHash.containsKey(stemmedToken)) {
        int freq = ((Integer) tokenHash.get(stemmedToken)).intValue();
        freq++;
        tokenHash.put(stemmedToken, new Integer(freq));
      } else {
        tokenHash.put(stemmedToken, new Integer(1));
      }
    }

    // now we have histogram, lets write it out
    String hashString = "";
    Enumeration en = tokenHash.keys();
    while (en.hasMoreElements()) {
      String t = (String) en.nextElement();
      int freq = (Integer) tokenHash.get(t);
      hashString += t + freq;
    }

    // log.info(hashString);
    String hash = "";

    if (hashString.length() > 0) {
      MessageDigest md = null;
      try {
        md = MessageDigest.getInstance("SHA"); // step 2
      } catch (NoSuchAlgorithmException e) {
        LOG.error("NoSuchAlgorithmException", e);
        throw new Exception(e.getMessage());
      }
      try {
        md.update(hashString.getBytes("UTF-8")); // step 3
      } catch (UnsupportedEncodingException e) {
        LOG.error("UnsupportedEncodingException", e);
        throw new Exception(e.getMessage());
      }
      byte raw[] = md.digest();
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
    HashMap<String, Integer> m = new HashMap<String, Integer>();

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
    String finalSummary = "";

    try {

      String[] puncChars = { ":", "--", "PM", "MST", "EST", "CST", "PST",
          "GMT", "AM", "  " };

      txt = txt.replace(" | ", " ");
      txt = txt.replace(" |", " ");
      ArrayList<String> sentences = TextProcessor.splitToSentences(txt);
      // System.out.println("Sentences are:");
      StringBuffer sum = new StringBuffer();
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

      finalSummary = sum.toString().trim();

      if (truncateInSentence) {
        finalSummary = truncateTextOnSpace(finalSummary, numChars);
        int numPeriods = countTrailingPeriods(finalSummary);

        if (numPeriods < 3 && finalSummary.length() > 0) {
          for (int i = 0; i < 3 - numPeriods; i++) {
            finalSummary += ".";
          }
        }
      } else {
        // trim final period
        if (finalSummary.endsWith("..")) {
          finalSummary = finalSummary.substring(0, finalSummary.length() - 2);
        }
      }
      // check to see if we have anything, if not, return the fullcontent
      if (finalSummary.trim().length() < 5) {
        finalSummary = txt;
      }
      // see if have a punc in the first 30 chars
      int highestIdx = -1;
      int sIdx = Math.min(finalSummary.length() - 1, 45);
      for (String p : puncChars) {
        int idx = finalSummary.trim().substring(0, sIdx).lastIndexOf(p);
        if (idx > highestIdx && idx < 45) {
          highestIdx = idx + p.length();
        }
      }

      if (highestIdx > -1) {
        finalSummary = finalSummary.substring(highestIdx);
      }

      int closeParenIdx = finalSummary.indexOf(")");
      int openParenIdx = finalSummary.indexOf("(");
      // if(closeParenIdx < )
      if (closeParenIdx != -1 && closeParenIdx < 15
          && (openParenIdx == -1 || openParenIdx > closeParenIdx)) {
        finalSummary = finalSummary.substring(closeParenIdx + 1).trim();
      }

      finalSummary = trimPunctuationFromStart(finalSummary);

      // check to see if we have anything, if not, return the fullcontent
      if (finalSummary.trim().length() < 5) {
        finalSummary = txt;
      }

    } catch (Exception e) {
      LOG.error("Problem forming summary for: " + txt);
      LOG.error("Using full text for the summary", e);
      finalSummary = txt;
    }

    return finalSummary.trim();
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
        if (txt.valueOf(txt.charAt(i)).equals(".")) {
          retVal++;
        } else {
          break;
        }
      }
    }
    return retVal;
  }

  public static String trimSentence(String txt, String title) {

    // iterate backwards looking for the first all cap word..
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
    if (title.trim().length() > 0 && txt.indexOf(title.trim()) != -1) {
      txt = txt
          .substring(txt.indexOf(title.trim()) + title.trim().length() - 1);
    }

    // scrub before first -
    if (txt.indexOf(" � ") != -1) {
      txt = txt.substring(txt.indexOf(" � ") + 3);
    }
    if (txt.indexOf(" - ") != -1) {
      txt = txt.substring(txt.indexOf(" - ") + 3);
    }
    if (txt.indexOf("del.icio.us") != -1) {
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
    List<String> urls = new ArrayList<String>();
    // tokenize and iterate
    String[] tokens = txt.split(" ");
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
    List<String> commonTokens = new ArrayList<String>();

    if (segments.size() > 1) {
      List<String> allTokens = new ArrayList<String>();
      for (String s : segments) {
        String[] tks = s.split(" ");
        List<String> tokens = Arrays.asList(tks);
        HashMap<String, Integer> ut = TextProcessor.getUniqueTokenIndex(tokens);
        for (String t : ut.keySet()) {
          allTokens.add(t);
        }
      }
      HashMap<String, Integer> uniqueTokens = TextProcessor
          .getUniqueTokenIndex(allTokens);
      for (String t : uniqueTokens.keySet()) {
        Integer freq = uniqueTokens.get(t);
        if (freq.intValue() == segments.size()) {
          commonTokens.add(t);
        }
      }
    }

    return commonTokens;
  }

  public static int numTokensInString(String txt) {
    int retVal = 0;
    if (txt != null && txt.trim().length() > 0) {
      retVal = txt.trim().split(" ").length;
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
