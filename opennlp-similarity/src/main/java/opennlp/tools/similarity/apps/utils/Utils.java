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

package opennlp.tools.similarity.apps.utils;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

  protected static final ArrayList<String[]> characterMappings = new ArrayList<String[]>();

  static {
    characterMappings
        .add(new String[] {
            "[ÃƒÂ ÃƒÂ¡ÃƒÂ¢ÃƒÂ£ÃƒÂ¤ÃƒÂ¥Ã¯Â¿Â½?Ã„Æ’Ã„â€¦Ã�Â°]",
            " " }); // was a
    characterMappings
        .add(new String[] {
            "[Ãƒâ‚¬Ã¯Â¿Â½?Ãƒâ€šÃƒÆ’Ãƒâ€žÃƒâ€¦Ã„â‚¬Ã„â€šÃ„â€žÃ¯Â¿Â½?]",
            "A" });
    characterMappings.add(new String[] {
        "[ÃƒÂ§Ã„â€¡Ã„â€°Ã„â€¹Ã¯Â¿Â½?]", "c" });
    characterMappings.add(new String[] {
        "[Ãƒâ€¡Ã„â€ Ã„Ë†Ã„Å Ã„Å’]", "C" });
    characterMappings.add(new String[] { "[Ã¯Â¿Â½?Ã„â€˜]", "d" });
    characterMappings.add(new String[] {
        "[Ã¯Â¿Â½?Ã„Å½Ã¯Â¿Â½?]", "D" });
    characterMappings
        .add(new String[] {
            "[ÃƒÂ¨ÃƒÂ©ÃƒÂªÃƒÂ«ÃƒÂ¦Ã„â„¢Ã„â€œÃ„â€¢Ã„â€”Ã„â„¢Ã„â€º]",
            " " }); // was e
    characterMappings
        .add(new String[] {
            "[ÃƒË†Ãƒâ€°ÃƒÅ Ãƒâ€¹Ãƒâ€ Ã„â€™Ã„â€�Ã„â€“Ã„ËœÃ„Å¡]",
            "'" }); // was E
    characterMappings.add(new String[] {
        "[Ã¯Â¿Â½?Ã„Å¸Ã„Â¡Ã„Â£]", "g" });
    characterMappings.add(new String[] {
        "[Ã„Å“Ã„Å¾Ã„Â Ã„Â¢Ã†â€œ]", "G" });
    characterMappings.add(new String[] { "[Ã„Â¥Ã„Â§]", "h" });
    characterMappings.add(new String[] { "[Ã„Â¤Ã„Â¦]", "H" });
    characterMappings
        .add(new String[] {
            "[ÃƒÂ¬ÃƒÂ­ÃƒÂ®ÃƒÂ¯Ã„Â©Ã„Â«Ã„Â­Ã„Â®Ã„Â¯Ã„Â±Ã„Â³Ã„Âµ]",
            "i" });
    characterMappings
        .add(new String[] {
            "[ÃƒÅ’Ã¯Â¿Â½?ÃƒÅ½Ã¯Â¿Â½?Ã„Â¨Ã„ÂªÃ„Â¬Ã„Â°Ã„Â²Ã„Â´Ã„Âµ]",
            "I" });
    characterMappings.add(new String[] { "[Ã„Â·Ã„Â¸]", "k" });
    characterMappings.add(new String[] { "[Ã„Â¶]", "K" });
    characterMappings
        .add(new String[] {
            "[ÃƒÂ¸Ã…â€˜ÃƒÂ°ÃƒÂ²ÃƒÂ³ÃƒÂ´ÃƒÂµÃƒÂ¶Ã¯Â¿Â½?Ã¯Â¿Â½?Ã…â€˜Ã…â€œÃ†Â¡]",
            "o" });
    characterMappings
        .add(new String[] {
            "[Ãƒâ€™Ãƒâ€œÃƒâ€�Ãƒâ€¢Ãƒâ€“ÃƒËœÃ…Å’Ã…Å½Ã¯Â¿Â½?Ã…â€™Ã†Â ]",
            "O" });
    characterMappings.add(new String[] {
        "[ÃƒÂ±Ã…â€žÃ…â€ Ã…Ë†Ã…â€°Ã…â€¹]",
        "n" });
    characterMappings.add(new String[] {
        "[Ãƒâ€˜Ã…Æ’Ã…â€¦Ã…â€¡Ã…Å Ã…â€¹]",
        "N" });
    characterMappings.add(new String[] {
        "[Ã„ÂºÃ„Â¼Ã„Â¾Ã…â‚¬Ã…â€š]", "l" });
    characterMappings.add(new String[] {
        "[Ã„Â¹Ã„Â»Ã„Â½Ã„Â¿Ã¯Â¿Â½?]", "L" });
    characterMappings
        .add(new String[] {
            "[ÃƒÂ¹ÃƒÂºÃƒÂ»ÃƒÂ¼Ã…Â©Ã…Â«Ã…Â­Ã…Â¯Ã…Â±Ã…Â³Ã†Â°]",
            "u" });
    characterMappings
        .add(new String[] {
            "[Ãƒâ„¢ÃƒÅ¡Ãƒâ€ºÃƒÅ“Ã…Â¨Ã…ÂªÃ…Â¬Ã…Â®Ã…Â°Ã…Â²Ã†Â¯]",
            "U" });
    characterMappings.add(new String[] { "[ÃƒÂ½ÃƒÂ¿Ã…Â·]", "y" });
    characterMappings.add(new String[] { "[Ã¯Â¿Â½?Ã…Â¶Ã…Â¸]",
        "Y" });
    characterMappings.add(new String[] {
        "[Ã…â€¢Ã…â€”Ã…â„¢]", "r" });
    characterMappings.add(new String[] {
        "[Ã…â€�Ã…â€“Ã…Ëœ]", "R" });
    characterMappings
        .add(new String[] {
            "[Ã…Â¡Ã…â€ºÃ¯Â¿Â½?Ã…Å¸Ã…Â¡Ã…Â¿]",
            "s" });
    characterMappings.add(new String[] {
        "[Ã…Â Ã…Å¡Ã…Å“Ã…Å¾Ã…Â Ã…Â¿]", "S" });
    characterMappings.add(new String[] { "ÃƒÅ¸", "ss" });
    characterMappings.add(new String[] { "ÃƒÅ¾", "th" });
    characterMappings.add(new String[] { "ÃƒÂ¾", "Th" });
    characterMappings
        .add(new String[] { "[Ã…Â£Ã…Â¥Ã…Â§]", "t" });
    characterMappings
        .add(new String[] { "[Ã…Â¢Ã…Â¤Ã…Â¦]", "T" });
    characterMappings.add(new String[] { "[Ã…Âµ]", "w" });
    characterMappings.add(new String[] { "[Ã…Â´]", "W" });
    characterMappings.add(new String[] {
        "[Ã…Â¾Ã…ÂºÃ…Â¼Ã…Â¾Ã†Â¶]", "z" });
    characterMappings.add(new String[] {
        "[Ã…Â½Ã…Â½Ã…Â¹Ã…Â»Ã…Â½Ã†Âµ]", "Z" });
    characterMappings.add(new String[] { "[Ã¢â‚¬â„¢]", "'" });
    characterMappings.add(new String[] { "[Ã¢â‚¬â€œ]", "'" });
    characterMappings.add(new String[] { "&#39;", "'" });
    characterMappings.add(new String[] { "Ã‚e", "Â«" });
    characterMappings.add(new String[] { "'AG", "â€œ" });
    characterMappings.add(new String[] { "Aï¿½", " " });
    characterMappings.add(new String[] { "&quot;", "\"" });
    characterMappings.add(new String[] { "&amp;", "&" });
    characterMappings.add(new String[] { "&nbsp;", " " });
    characterMappings.add(new String[] { "Ã®â‚¬â‚¬", " " });
    characterMappings.add(new String[] { "Ã¢â€žÂ¢", " " });
    characterMappings.add(new String[] { "Ã¢â‚¬â€�", "" });
    characterMappings.add(new String[] { "’", "'" });
  }

  public static String stripNonAsciiChars(String s) {
    StringBuffer b = new StringBuffer();
    if (s != null) {
      for (int i = 0; i < s.length(); i++) {
        if (((int) s.charAt(i)) <= 256) {
          b.append(s.charAt(i));
        }
      }
    }

    return b.toString().trim().replaceAll("\\s+", " "); // replace any multiple
                                                        // spaces with a single
                                                        // space
  }

  public static String convertToASCII(String s) {
    s = s.replace("&amp", "");
    s = s.replaceAll("’", "__apostrophe__");
    String tmp = s;
    if (tmp != null) {
      for (String[] mapping : characterMappings) {
        tmp = tmp.replaceAll(mapping[0], mapping[1]);
      }
    }
    return stripNonAsciiChars(tmp.replaceAll("__apostrophe__", "'"));
  }

  public static class KeyValue {
    public Object key = null;

    public float value = 0;

    public KeyValue(Object o, Float i) {
      this.key = o;
      this.value = i;
    }

    public static class SortByValue implements Comparator {
      public int compare(Object obj1, Object obj2) {
        float i1 = ((KeyValue) obj1).value;
        float i2 = ((KeyValue) obj2).value;

        if (i1 < i2)
          return 1;
        return -1;
      }
    }
  }

  public static boolean createResizedCopy(String originalImage,
      String newImage, int scaledWidth, int scaledHeight) {
    boolean retVal = true;
    try {
      File o = new File(originalImage);
      BufferedImage bsrc = ImageIO.read(o);
      BufferedImage bdest = new BufferedImage(scaledWidth, scaledHeight,
          BufferedImage.TYPE_INT_RGB);

      Graphics2D g = bdest.createGraphics();
      AffineTransform at = AffineTransform.getScaleInstance(
          (double) scaledWidth / bsrc.getWidth(),
          (double) scaledHeight / bsrc.getHeight());
      g.drawRenderedImage(bsrc, at);
      ImageIO.write(bdest, "jpeg", new File(newImage));

    } catch (Exception e) {
      retVal = false;
      LOG.error("Failed creating thumbnail for image: " + originalImage, e);
    }

    return retVal;
  }

  private static int minimum(int a, int b, int c) {
    int mi;

    mi = a;
    if (b < mi) {
      mi = b;
    }
    if (c < mi) {
      mi = c;
    }
    return mi;

  }

  public static int computeEditDistance(String s, String t) {
    int d[][]; // matrix
    int n; // length of s
    int m; // length of t
    int i; // iterates through s
    int j; // iterates through t
    char s_i; // ith character of s
    char t_j; // jth character of t
    int cost; // cost

    // Step 1
    n = s.length();
    m = t.length();
    if (n == 0) {
      return m;
    }
    if (m == 0) {
      return n;
    }
    d = new int[n + 1][m + 1];
    // Step 2
    for (i = 0; i <= n; i++) {
      d[i][0] = i;
    }
    for (j = 0; j <= m; j++) {
      d[0][j] = j;
    }
    // Step 3
    for (i = 1; i <= n; i++) {
      s_i = s.charAt(i - 1);
      // Step 4
      for (j = 1; j <= m; j++) {
        t_j = t.charAt(j - 1);
        // Step 5
        if (s_i == t_j) {
          cost = 0;
        } else {
          cost = 1;
        }
        // Step 6
        d[i][j] = minimum(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1]
            + cost);
      }
    }
    // Step 7
    return d[n][m];
  }

  public static ArrayList<KeyValue> sortByValue(HashMap<Object, Float> h) {
    ArrayList<KeyValue> res = new ArrayList<KeyValue>();
    for (Object o : h.keySet()) {
      // form a pair
      res.add(new KeyValue(o, h.get(o)));
    }

    Collections.sort(res, new KeyValue.SortByValue());

    return res;
  }

  public static String convertKeyValueToString(ArrayList<KeyValue> l) {
    StringBuffer retVal = new StringBuffer();
    for (KeyValue kv : l) {
      retVal.append(kv.key);
      retVal.append("-");
      retVal.append(kv.value);
      retVal.append(",");
    }

    return retVal.toString();
  }

  public static String convertStringArrayToString(ArrayList<String> l) {
    StringBuffer b = new StringBuffer();
    for (String s : l) {
      b.append(s);
      b.append(", ");
    }

    return b.toString();
  }

  public static String convertStringArrayToPlainString(ArrayList<String> l) {
    StringBuffer b = new StringBuffer();
    for (String s : l) {
      b.append(s);
      b.append(" ");
    }

    return b.toString();
  }

  public static boolean noDomainInUrl(String siteUrl, String url) {
    if (StringUtils.isEmpty(url)) {
      return true;
    }
    if (!url.startsWith("http://")) {
      return true;
    }
    return false;
  }

  public static String addDomainToUrl(String siteUrl, String url) {
    if (StringUtils.isEmpty(url)) {
      return null; // should we return siteUrl here ??
    }
    if (!url.startsWith("http://")) {
      String domain = StringUtils.substringBetween(siteUrl, "http://", "/");
      if (domain == null) {
        url = siteUrl + (url.startsWith("/") ? "" : "/") + url;
      } else {
        if (!url.startsWith("/")) {
          int lastIndex = StringUtils.lastIndexOf(siteUrl, "/");
          url = siteUrl.substring(0, lastIndex) + "/" + url;
        } else {
          url = "http://" + domain + url;
        }
      }
    }
    return url;
  }

  public static int countValues(Hashtable<String, Float> b1) {
    int retVal = 0;
    for (String s : b1.keySet()) {
      retVal += b1.get(s);
    }

    return retVal;
  }

  public static int countValues(HashMap<String, Integer> b1) {
    int retVal = 0;
    for (String s : b1.keySet()) {
      retVal += b1.get(s);
    }

    return retVal;
  }

  public static String convertHashMapToString(HashMap<String, Integer> m) {
    StringBuffer s = new StringBuffer();
    for (String x : m.keySet()) {
      s.append(x);
      s.append("-");
      s.append(m.get(x));
      s.append(",");
    }

    return s.toString();
  }

  public static boolean isTokenAllDigitOrPunc(String token) {
    for (int i = 0; i < token.length(); i++) {
      if (java.lang.Character.isLetter(token.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean containsDigit(String token) {
    for (int i = 0; i < token.length(); i++) {
      if (java.lang.Character.isDigit(token.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  public static String CleanCharacter(String txt, int uValue) {
    StringBuffer retVal = new StringBuffer();
    for (int i = 0; i < txt.length(); i++) {
      int uChar = (txt.charAt(i));
      if (uChar != uValue) {
        retVal.append(txt.charAt(i));
      } else {
        retVal.append(" ");
      }
    }
    return retVal.toString();
  }

  public static String removeHTMLTagsFromStr(String inputStr) {
    String[] removeTags = StringUtils.substringsBetween(inputStr, "<", ">");

    if (removeTags != null && removeTags.length > 0) {
      for (String tag : removeTags) {
        inputStr = StringUtils.remove(inputStr, "<" + tag + ">");
      }
    }

    return inputStr;
  }

  public static String unescapeHTML(String text) {
    return org.apache.commons.lang.StringEscapeUtils.unescapeHtml(text);
  }

  public static String stripHTML(String text) {
    return text.replaceAll("\\<.*?>", "");
  }

  public static String stripScriptTags(String text) {
    Pattern p = java.util.regex.Pattern.compile("\\<SCRIPT.*?</SCRIPT>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    Matcher matcher = p.matcher(text);
    String tmp = matcher.replaceAll("");
    return tmp;
  }

  public static String stripNoScriptTags(String text) {
    Pattern p = java.util.regex.Pattern.compile("\\<NOSCRIPT.*?</NOSCRIPT>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    Matcher matcher = p.matcher(text);
    String tmp = matcher.replaceAll("");
    return tmp;
  }

  public static String stripHTMLMultiLine(String text,
      HashSet<String> allowedHtmlTags, String escGtCh, String escLtCh) {
    if (StringUtils.isNotEmpty(text)) {

      boolean hadAllowedHtmlTags = false;

      if (allowedHtmlTags != null) {
        for (String htmlTag : allowedHtmlTags) {
          String tmp = text.replaceAll("<" + htmlTag + ">", escLtCh + htmlTag
              + escGtCh);
          tmp = tmp.replaceAll("</" + htmlTag + ">", escLtCh + "/" + htmlTag
              + escGtCh);
          if (!tmp.equals(text)) {
            text = tmp;
            hadAllowedHtmlTags = true;
          }
        }
      }

      text = stripHTMLMultiLine(text);

      if (hadAllowedHtmlTags) {
        text = text.replaceAll(escLtCh, "<");
        text = text.replaceAll(escGtCh, ">");
      }
    }

    return text;
  }

  public static String stripHTMLMultiLine(String text) {
    Pattern p = java.util.regex.Pattern.compile("\\<.*?>", Pattern.DOTALL);
    Matcher matcher = p.matcher(text);
    String tmp = matcher.replaceAll("");
    return tmp;
  }

  public static String stripHTMLCommentsMultiLine(String text) {
    Pattern p = java.util.regex.Pattern.compile("\\<!--.*?-->", Pattern.DOTALL);
    Matcher matcher = p.matcher(text);
    String tmp = matcher.replaceAll("");
    return tmp;
  }

  public static boolean isFlagSet(Integer flags, Integer flagToCheck) {
    if (flags != null && flagToCheck != null) {
      return ((flags & flagToCheck) == flagToCheck);
    }
    return false;
  }

  public static Integer updateFlag(Integer flags, Integer flagToCheck,
      boolean shouldSet) {
    if (shouldSet) {
      return setFlag(flags, flagToCheck);
    } else {
      return resetFlag(flags, flagToCheck);
    }
  }

  public static Integer setFlag(Integer flags, Integer flagToCheck) {
    if (flags == null) {
      flags = new Integer(0);
    }
    if (!isFlagSet(flags, flagToCheck)) {
      flags = flags + flagToCheck;
      ;
    }
    return flags;
  }

  public static Integer resetFlag(Integer flags, Integer flagToCheck) {
    if (flags == null) {
      // nothing to reset
      flags = new Integer(0);
      return flags;
    }

    if (isFlagSet(flags, flagToCheck)) {
      flags = flags - flagToCheck;
    }
    return flags;
  }

  public static String truncateOnSpace(String text, Integer length) {
    String retVal = "";
    if (text.length() <= length) {
      retVal = text;
    } else {
      StringBuffer b = new StringBuffer();
      for (int i = 0; i < text.length(); i++) {
        if (b.length() >= length && Character.isWhitespace(text.charAt(i))) { // iterate
                                                                              // until
                                                                              // we
                                                                              // hit
                                                                              // whitespace
          b.append("...");
          break;
        }
        b.append(text.charAt(i));
      }
      retVal = b.toString();
    }

    return retVal.trim();
  }

  public static String sanitizeString(String text) {
    text = Utils.stripHTMLCommentsMultiLine(text);
    text = Utils.stripHTMLMultiLine(text);
    text = Utils.unescapeHTML(text);
    text = StringUtils.trimToEmpty(text);
    text = text.replaceAll("\\s+", " ");
    return text;
  }

  public static String makeStringUrlSafe(String text) {
    StringBuffer b = new StringBuffer();
    for (int i = 0; i < text.length(); i++) {
      if (StringUtils.isAlphanumericSpace(String.valueOf(text.charAt(i)))) {
        b.append(text.charAt(i));
      }
    }
    return Utils.convertToASCII(b.toString().replaceAll("\\s+", " "));
  }

  public static String getEventIdFromNewsUrl(String url) {
    String eventId = null;
    String p = "news/([0-9]+)";
    Pattern pattern = Pattern.compile(p);
    Matcher matcher = pattern.matcher(url);
    while (matcher.find()) {
      // System.out.println("found: " + matcher.group(2));
      eventId = matcher.group(1);
    }
    return eventId;
  }

  public static String buildCommaSeparatedIds(List ids) {

    if (ids != null && ids.size() > 0) {
      StringBuffer sbuf = new StringBuffer();

      for (int count = 0; count < ids.size(); count++) {
        if (count > 0) {
          sbuf.append(",");
        }
        sbuf.append(ids.get(count));
      }
      return sbuf.toString();
    }

    return null;
  }

  public static float computeScoreForRanking(List<Float> scores,
      int desiredRanking) {
    float newScore = 0f;

    if (desiredRanking == 1) {
      newScore = scores.get(0) + 50000;
    } else if (desiredRanking == scores.size()) {
      newScore = scores.get(scores.size() - 1) - 1;
    } else {
      newScore = (scores.get(desiredRanking - 2) + scores
          .get(desiredRanking - 1)) / 2;
    }

    return newScore;
  }

  public static String fullStripHTML(String text) {
    text = Utils.stripScriptTags(text);
    text = Utils.stripNoScriptTags(text);
    text = Utils.stripStyleTags(text);
    return text.replaceAll("\\<.*?>", "");
  }

  public static String stripStyleTags(String text) {
    Pattern p = java.util.regex.Pattern.compile("\\<STYLE.*?</STYLE>",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    Matcher matcher = p.matcher(text);
    String tmp = matcher.replaceAll("");
    return tmp;
  }

  public static boolean isLatinWord(String word) {
    for (int i = 0; i < word.length(); i++) {
      int asciiCode = (int) word.charAt(i);
      if (asciiCode > 128)
        return false;
    }

    return true;
  }

  static public void main(String[] args) {
    System.out.println(isLatinWord("Performing Arts Center (SPAC)"));
    System.out.println(isLatinWord("“Jazz Age”"));

    System.out
        .println(isLatinWord("ãƒ‡ãƒ¼ãƒ“ãƒƒãƒ‰ãƒ»ã"));
    System.out.println(isLatinWord("Ã© Ã±Ã§Ã¸Ã¥Ã³"));
    System.out.println(isLatinWord("Ã¹Ã¬Ã®Ã¤ Ã Ã¸Ã¶Ã©"));
    System.out
        .println(isLatinWord("é™³æ¸¯ç”Ÿ, é™ˆæ¸¯ç”Ÿ"));

    System.out
        .println(convertToASCII("Irvine Bay Hotel & Golf Club on Sunday, May 01 duringÂ Jazz on the Beach,Â Tobago Jazz Experience alongsideÂ The Jazz Singer"));
    System.out
        .println(convertToASCII("This year’s event, held again at the wonderful Saratoga Performing Arts Center (SPAC)"));
    System.out
        .println(convertToASCII("and the great saxophone playing of Sam Rogers                Rush Hour Blues 2010      .  "));
    System.out
        .println(convertToASCII("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Ron Carter is among the most original, prolific "));
    System.out
        .println(convertToASCII(" .             Ron Carter is among the most original, prolific. "));
    // TODO deal with
    // www.wmot.org/program-guide/program-listings/28th_annual_playboy_jazz_festiva_2006.htm
    System.out
        .println(convertToASCII("By the mid 1920’s,    during the period referred to as the “Jazz Age”, jazz music was heard    in most major cities from the East Coast"));

  }
}
