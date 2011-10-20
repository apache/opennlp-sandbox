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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;

public class Utils {

	private static final Logger LOG =  Logger.getLogger("opennlp.tools.similarity.apps.utils.Utils");

	protected static final ArrayList<String[]> characterMappings = new ArrayList<String[]>();

	static {
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã‚Â ÃƒÆ’Ã‚Â¡ÃƒÆ’Ã‚Â¢ÃƒÆ’Ã‚Â£ÃƒÆ’Ã‚Â¤ÃƒÆ’Ã‚Â¥ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€žÃ†â€™Ãƒâ€žÃ¢â‚¬Â¦Ãƒï¿½Ã‚Â°]",
		" " }); // was a
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã¢â€šÂ¬ÃƒÂ¯Ã‚Â¿Ã‚Â½?ÃƒÆ’Ã¢â‚¬Å¡ÃƒÆ’Ã†â€™ÃƒÆ’Ã¢â‚¬Å¾ÃƒÆ’Ã¢â‚¬Â¦Ãƒâ€žÃ¢â€šÂ¬Ãƒâ€žÃ¢â‚¬Å¡Ãƒâ€žÃ¢â‚¬Å¾ÃƒÂ¯Ã‚Â¿Ã‚Â½?]",
		"A" });
		characterMappings.add(new String[] {
				"[ÃƒÆ’Ã‚Â§Ãƒâ€žÃ¢â‚¬Â¡Ãƒâ€žÃ¢â‚¬Â°Ãƒâ€žÃ¢â‚¬Â¹ÃƒÂ¯Ã‚Â¿Ã‚Â½?]", "c" });
		characterMappings.add(new String[] {
				"[ÃƒÆ’Ã¢â‚¬Â¡Ãƒâ€žÃ¢â‚¬Â Ãƒâ€žÃ‹â€ Ãƒâ€žÃ…Â Ãƒâ€žÃ…â€™]", "C" });
		characterMappings.add(new String[] { "[ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€žÃ¢â‚¬Ëœ]", "d" });
		characterMappings.add(new String[] {
				"[ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€žÃ…Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½?]", "D" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã‚Â¨ÃƒÆ’Ã‚Â©ÃƒÆ’Ã‚ÂªÃƒÆ’Ã‚Â«ÃƒÆ’Ã‚Â¦Ãƒâ€žÃ¢â€žÂ¢Ãƒâ€žÃ¢â‚¬Å“Ãƒâ€žÃ¢â‚¬Â¢Ãƒâ€žÃ¢â‚¬â€�Ãƒâ€žÃ¢â€žÂ¢Ãƒâ€žÃ¢â‚¬Âº]",
		" " }); // was e
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã‹â€ ÃƒÆ’Ã¢â‚¬Â°ÃƒÆ’Ã…Â ÃƒÆ’Ã¢â‚¬Â¹ÃƒÆ’Ã¢â‚¬Â Ãƒâ€žÃ¢â‚¬â„¢Ãƒâ€žÃ¢â‚¬ï¿½Ãƒâ€žÃ¢â‚¬â€œÃƒâ€žÃ‹Å“Ãƒâ€žÃ…Â¡]",
		"'" }); // was E
		characterMappings.add(new String[] {
				"[ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€žÃ…Â¸Ãƒâ€žÃ‚Â¡Ãƒâ€žÃ‚Â£]", "g" });
		characterMappings.add(new String[] {
				"[Ãƒâ€žÃ…â€œÃƒâ€žÃ…Â¾Ãƒâ€žÃ‚Â Ãƒâ€žÃ‚Â¢Ãƒâ€ Ã¢â‚¬Å“]", "G" });
		characterMappings.add(new String[] { "[Ãƒâ€žÃ‚Â¥Ãƒâ€žÃ‚Â§]", "h" });
		characterMappings.add(new String[] { "[Ãƒâ€žÃ‚Â¤Ãƒâ€žÃ‚Â¦]", "H" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã‚Â¬ÃƒÆ’Ã‚Â­ÃƒÆ’Ã‚Â®ÃƒÆ’Ã‚Â¯Ãƒâ€žÃ‚Â©Ãƒâ€žÃ‚Â«Ãƒâ€žÃ‚Â­Ãƒâ€žÃ‚Â®Ãƒâ€žÃ‚Â¯Ãƒâ€žÃ‚Â±Ãƒâ€žÃ‚Â³Ãƒâ€žÃ‚Âµ]",
		"i" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã…â€™ÃƒÂ¯Ã‚Â¿Ã‚Â½?ÃƒÆ’Ã…Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€žÃ‚Â¨Ãƒâ€žÃ‚ÂªÃƒâ€žÃ‚Â¬Ãƒâ€žÃ‚Â°Ãƒâ€žÃ‚Â²Ãƒâ€žÃ‚Â´Ãƒâ€žÃ‚Âµ]",
		"I" });
		characterMappings.add(new String[] { "[Ãƒâ€žÃ‚Â·Ãƒâ€žÃ‚Â¸]", "k" });
		characterMappings.add(new String[] { "[Ãƒâ€žÃ‚Â¶]", "K" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã‚Â¸Ãƒâ€¦Ã¢â‚¬ËœÃƒÆ’Ã‚Â°ÃƒÆ’Ã‚Â²ÃƒÆ’Ã‚Â³ÃƒÆ’Ã‚Â´ÃƒÆ’Ã‚ÂµÃƒÆ’Ã‚Â¶ÃƒÂ¯Ã‚Â¿Ã‚Â½?ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€¦Ã¢â‚¬ËœÃƒâ€¦Ã¢â‚¬Å“Ãƒâ€ Ã‚Â¡]",
		"o" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å“ÃƒÆ’Ã¢â‚¬ï¿½ÃƒÆ’Ã¢â‚¬Â¢ÃƒÆ’Ã¢â‚¬â€œÃƒÆ’Ã‹Å“Ãƒâ€¦Ã…â€™Ãƒâ€¦Ã…Â½ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€¦Ã¢â‚¬â„¢Ãƒâ€ Ã‚Â ]",
		"O" });
		characterMappings.add(new String[] {
				"[ÃƒÆ’Ã‚Â±Ãƒâ€¦Ã¢â‚¬Å¾Ãƒâ€¦Ã¢â‚¬Â Ãƒâ€¦Ã‹â€ Ãƒâ€¦Ã¢â‚¬Â°Ãƒâ€¦Ã¢â‚¬Â¹]",
		"n" });
		characterMappings.add(new String[] {
				"[ÃƒÆ’Ã¢â‚¬ËœÃƒâ€¦Ã†â€™Ãƒâ€¦Ã¢â‚¬Â¦Ãƒâ€¦Ã¢â‚¬Â¡Ãƒâ€¦Ã…Â Ãƒâ€¦Ã¢â‚¬Â¹]",
		"N" });
		characterMappings.add(new String[] {
				"[Ãƒâ€žÃ‚ÂºÃƒâ€žÃ‚Â¼Ãƒâ€žÃ‚Â¾Ãƒâ€¦Ã¢â€šÂ¬Ãƒâ€¦Ã¢â‚¬Å¡]", "l" });
		characterMappings.add(new String[] {
				"[Ãƒâ€žÃ‚Â¹Ãƒâ€žÃ‚Â»Ãƒâ€žÃ‚Â½Ãƒâ€žÃ‚Â¿ÃƒÂ¯Ã‚Â¿Ã‚Â½?]", "L" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã‚Â¹ÃƒÆ’Ã‚ÂºÃƒÆ’Ã‚Â»ÃƒÆ’Ã‚Â¼Ãƒâ€¦Ã‚Â©Ãƒâ€¦Ã‚Â«Ãƒâ€¦Ã‚Â­Ãƒâ€¦Ã‚Â¯Ãƒâ€¦Ã‚Â±Ãƒâ€¦Ã‚Â³Ãƒâ€ Ã‚Â°]",
		"u" });
		characterMappings
		.add(new String[] {
				"[ÃƒÆ’Ã¢â€žÂ¢ÃƒÆ’Ã…Â¡ÃƒÆ’Ã¢â‚¬ÂºÃƒÆ’Ã…â€œÃƒâ€¦Ã‚Â¨Ãƒâ€¦Ã‚ÂªÃƒâ€¦Ã‚Â¬Ãƒâ€¦Ã‚Â®Ãƒâ€¦Ã‚Â°Ãƒâ€¦Ã‚Â²Ãƒâ€ Ã‚Â¯]",
		"U" });
		characterMappings.add(new String[] { "[ÃƒÆ’Ã‚Â½ÃƒÆ’Ã‚Â¿Ãƒâ€¦Ã‚Â·]", "y" });
		characterMappings.add(new String[] { "[ÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€¦Ã‚Â¶Ãƒâ€¦Ã‚Â¸]",
		"Y" });
		characterMappings.add(new String[] {
				"[Ãƒâ€¦Ã¢â‚¬Â¢Ãƒâ€¦Ã¢â‚¬â€�Ãƒâ€¦Ã¢â€žÂ¢]", "r" });
		characterMappings.add(new String[] {
				"[Ãƒâ€¦Ã¢â‚¬ï¿½Ãƒâ€¦Ã¢â‚¬â€œÃƒâ€¦Ã‹Å“]", "R" });
		characterMappings
		.add(new String[] {
				"[Ãƒâ€¦Ã‚Â¡Ãƒâ€¦Ã¢â‚¬ÂºÃƒÂ¯Ã‚Â¿Ã‚Â½?Ãƒâ€¦Ã…Â¸Ãƒâ€¦Ã‚Â¡Ãƒâ€¦Ã‚Â¿]",
		"s" });
		characterMappings.add(new String[] {
				"[Ãƒâ€¦Ã‚Â Ãƒâ€¦Ã…Â¡Ãƒâ€¦Ã…â€œÃƒâ€¦Ã…Â¾Ãƒâ€¦Ã‚Â Ãƒâ€¦Ã‚Â¿]", "S" });
		characterMappings.add(new String[] { "ÃƒÆ’Ã…Â¸", "ss" });
		characterMappings.add(new String[] { "ÃƒÆ’Ã…Â¾", "th" });
		characterMappings.add(new String[] { "ÃƒÆ’Ã‚Â¾", "Th" });
		characterMappings
		.add(new String[] { "[Ãƒâ€¦Ã‚Â£Ãƒâ€¦Ã‚Â¥Ãƒâ€¦Ã‚Â§]", "t" });
		characterMappings
		.add(new String[] { "[Ãƒâ€¦Ã‚Â¢Ãƒâ€¦Ã‚Â¤Ãƒâ€¦Ã‚Â¦]", "T" });
		characterMappings.add(new String[] { "[Ãƒâ€¦Ã‚Âµ]", "w" });
		characterMappings.add(new String[] { "[Ãƒâ€¦Ã‚Â´]", "W" });
		characterMappings.add(new String[] {
				"[Ãƒâ€¦Ã‚Â¾Ãƒâ€¦Ã‚ÂºÃƒâ€¦Ã‚Â¼Ãƒâ€¦Ã‚Â¾Ãƒâ€ Ã‚Â¶]", "z" });
		characterMappings.add(new String[] {
				"[Ãƒâ€¦Ã‚Â½Ãƒâ€¦Ã‚Â½Ãƒâ€¦Ã‚Â¹Ãƒâ€¦Ã‚Â»Ãƒâ€¦Ã‚Â½Ãƒâ€ Ã‚Âµ]", "Z" });
		characterMappings.add(new String[] { "[ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢]", "'" });
		characterMappings.add(new String[] { "[ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“]", "'" });
		characterMappings.add(new String[] { "&#39;", "'" });
		characterMappings.add(new String[] { "Ãƒâ€še", "Ã‚Â«" });
		characterMappings.add(new String[] { "'AG", "Ã¢â‚¬Å“" });
		characterMappings.add(new String[] { "AÃ¯Â¿Â½", " " });
		characterMappings.add(new String[] { "&quot;", "\"" });
		characterMappings.add(new String[] { "&amp;", "&" });
		characterMappings.add(new String[] { "&nbsp;", " " });
		characterMappings.add(new String[] { "ÃƒÂ®Ã¢â€šÂ¬Ã¢â€šÂ¬", " " });
		characterMappings.add(new String[] { "ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢", " " });
		characterMappings.add(new String[] { "ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬ï¿½", "" });
		characterMappings.add(new String[] { "â€™", "'" });
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
		s = s.replaceAll("â€™", "__apostrophe__");
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
			LOG.severe("Failed creating thumbnail for image: " + originalImage + e);
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
		System.out.println(isLatinWord("â€œJazz Ageâ€�"));

		System.out
		.println(isLatinWord("Ã£Æ’â€¡Ã£Æ’Â¼Ã£Æ’â€œÃ£Æ’Æ’Ã£Æ’â€°Ã£Æ’Â»Ã£"));
		System.out.println(isLatinWord("ÃƒÂ© ÃƒÂ±ÃƒÂ§ÃƒÂ¸ÃƒÂ¥ÃƒÂ³"));
		System.out.println(isLatinWord("ÃƒÂ¹ÃƒÂ¬ÃƒÂ®ÃƒÂ¤ Ãƒ ÃƒÂ¸ÃƒÂ¶ÃƒÂ©"));
		System.out
		.println(isLatinWord("Ã©â„¢Â³Ã¦Â¸Â¯Ã§â€�Å¸, Ã©â„¢Ë†Ã¦Â¸Â¯Ã§â€�Å¸"));

		System.out
		.println(convertToASCII("Irvine Bay Hotel & Golf Club on Sunday, May 01 duringÃ‚Â Jazz on the Beach,Ã‚Â Tobago Jazz Experience alongsideÃ‚Â The Jazz Singer"));
		System.out
		.println(convertToASCII("This yearâ€™s event, held again at the wonderful Saratoga Performing Arts Center (SPAC)"));
		System.out
		.println(convertToASCII("and the great saxophone playing of Sam Rogers                Rush Hour Blues 2010 Â     .  "));
		System.out
		.println(convertToASCII("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Ron Carter is among the most original, prolific "));
		System.out
		.println(convertToASCII("Â . Â Â Â Â Â Â Â Â Â Â Â  Ron Carter is among the most original, prolific. "));
		// TODO deal with
		// www.wmot.org/program-guide/program-listings/28th_annual_playboy_jazz_festiva_2006.htm
		System.out
		.println(convertToASCII("By the mid 1920â€™s,    during the period referred to as the â€œJazz Ageâ€�, jazz music was heard    in most major cities from the East Coast"));

	}
}
