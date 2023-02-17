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

package opennlp.tools.similarity.apps;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.Utils;

public class GeneratedSentenceProcessor {

	public static final String[] occurs = new String[]{ "click here", "wikipedia", "retrieved", "isbn",
		"http", "www.",
		"copyright", "advertise",  "(accessed", "[edit]", "[citation needed]",
		"site map",  "email updates",  "contact us", "rss feeds",  "cite this site",
		"operating hours", "last modified", "product catalog",
		"days per week", "leave a comment", "corporate information",  
		"employment opportunities", "terms of use", "private policy", "parental guidelines", "copyright policy",  "ad choices",
		"about us",  "about our ads",  "privacy policy",  "terms of use",
		"click for", "photos",
		"find the latest",		       
		"terms of service",
		"clicking here",
		"skip to", "sidebar",
		"Tags:", 
		"available online",
		"get online",
		"buy online",
		"not valid", "get discount",
		"official site",
		"this video",
		//"this book",
		"this product",
		"paperback", "hardcover",
		"audio cd",
		"related searches",
		"permission is granted",
		"[edit",
		"edit categories",
		"free license",
		"permission is granted",
		"under the terms",
		"rights reserved",
		"wikipedia", 
		"recipient of", "this message", 
		"mailing list",  "purchase order",
		"mon-fri",  "email us",  "privacy pol",  "back to top", 
		"click here",  "for details",  "assistance?",  "chat live",
		"free shipping",  "company info",  "satisfaction g",  "contact us",
		"menu.", "search.",  "sign in", "home.",
		"additional terms", "may apply"};

	public static final String[] occursStartsWith = new String[]{
		"fax",  "write","email", "contact",  "conditions",  "chat live",
		"we ",  "the recipient",  "day return",  "days return",
		"refund it",  "your money",
		"purchase orders",
		"exchange it ",  "return it",  "day return",  "days return",
		"subscribe","posted by", "below" , "corporate",
		"this book"};
	public static String acceptableMinedSentence(String sent) {
		if (sent==null || sent.length()<40)
			return null;
		// if too many commas => seo text

		String[] commas = StringUtils.split(sent, ',');
		String[] spaces = StringUtils.split(sent, ' ');
		if ((float) commas.length / (float) spaces.length > 0.5) {
			System.out.println("Rejection: too many commas  in sent ='"+sent);
			return null;
		}

		String[] periods = StringUtils.split(sent.replace('.', '#'), '#');
		if ((float) periods.length / (float) spaces.length > 0.2) {
			//System.out.println("Rejection: too many periods in sent ='"+sent);
			return null;
		}
		// commented [x], to avoid rejection sentences with refs[]
		String[] brakets = StringUtils.split(sent.replace('(', '#').replace(')', '#')/*.replace('[', '#').replace(']', '#')*/, '#');
		if ((float) periods.length / (float) spaces.length > 0.2) {
			System.out.println("Rejection: too many brakets in sent ='"+sent);
			return null;
		}
		
		String[] pipes = StringUtils.split(sent, '|');
		if (StringUtils.split(sent, '|').length > 2
				|| StringUtils.split(sent, '>').length > 2) {
			//System.out.println("Rejection: too many |s or >s in sent ='"+sent);
			return null;
		}
		String sentTry = sent.toLowerCase();
		// if too many long spaces
		String sentSpaces = sentTry.replace("   ", "");
		if (sentSpaces.length() - sentTry.length() > 10) // too many spaces -
			// suspicious
			return null;
		if (isProhibitiveWordsOccurOrStartWith(sentTry))
			return null;

		// count symbols indicating wrong parts of page to mine for text
		// if short and contains too many symbols indicating wrong area: reject
		String sentWrongSym = sentTry.replace(">", "&&&").replace("�", "&&&")
				.replace("|", "&&&").replace(":", "&&&").replace("/", "&&&")
				.replace("-", "&&&").replace("%", "&&&");
		if ((sentWrongSym.length() - sentTry.length()) >= 4
				&& sentTry.length() < 200) // twice ot more
			return null;

		sent = sent.replace('[', ' ').replace(']', ' ')
				.replace("_should_find_orig_", "").replace(".   .", ". ")
				.replace("amp;", " ").replace("1.", " ").replace("2.", " ")
				.replace("3.", " ").replace("4.", " ").
			/*	.replace("2009", "2011")
				.replace("2008", "2011").replace("2006", "2011")
				.replace("2007", "2011").
			*/	replace("VIDEO:", " ").replace("Video:", " ")
				.replace("no comments", " ").replace("  ", " ").replace("  ", " ")
				.replace("(more.)", "").replace("more.", "").replace("<more>", "")
				.replace("[more]", "").replace(".,", ".").replace("&lt;", "")
				.replace("p&gt;", "").replace("product description", "");

		//sent = sent.replace("Click here. ","").replace("Share this:.","").replace("Facebook.",""). 
		//		replace("Twitter." Email. Google. Print. Tumblr. Pinterest. More. Digg. LinkedIn. StumbleUpon. Reddit. Like this: Like Loading.. ")

		// TODO .replace("a.", ".");

		int endIndex = sent.indexOf(" posted");
		if (endIndex > 0)
			sent = sent.substring(0, endIndex);

		return sent;
	}

	public static String processSentence(String pageSentence) {
		if (acceptableMinedSentence(pageSentence)==null){
			// TODO OPENNLP-1454 Candidate for logger.debug(...) if required/helpful
			// System.out.println("Rejected sentence by GeneratedSentenceProcessor.processSentence.acceptableMinedSentence()");
			return "";
		}
		if (pageSentence == null)
			return "";
		pageSentence = Utils.fullStripHTML(pageSentence);
		pageSentence = StringUtils.chomp(pageSentence, "..");
		pageSentence = StringUtils.chomp(pageSentence, ". .");
		pageSentence = StringUtils.chomp(pageSentence, " .");
		pageSentence = StringUtils.chomp(pageSentence, ".");
		pageSentence = StringUtils.chomp(pageSentence, "...");
		pageSentence = StringUtils.chomp(pageSentence, " ....");
		pageSentence = pageSentence.replace("::", ":").replace(".,", ". ")
				.replace("(.)", "");

		pageSentence = pageSentence.trim();
		pageSentence = pageSentence.replaceAll("\\s+", " "); // make single
		// spaces
		// everywhere

		String[] pipes = StringUtils.split(pageSentence, '|'); // removed
		// shorter part
		// of sentence
		// at the end
		// after pipe
		if (pipes.length == 2
				&& ((float) pipes[0].length() / (float) pipes[1].length() > 3.0)) {
			int pipePos = pageSentence.indexOf("|");
			if (pipePos > -1)
				pageSentence = pageSentence.substring(0, pipePos - 1).trim();

		}

		if (!StringUtils.contains(pageSentence, '.')
				&& !StringUtils.contains(pageSentence, '?')
				&& !StringUtils.contains(pageSentence, '!'))
			pageSentence = pageSentence + ". ";

		pageSentence = pageSentence.replace(" .", ".").replace("..", ".").trim();
		if (!pageSentence.endsWith(".") && !pageSentence.endsWith(":") 
				&&!pageSentence.endsWith("!") &&!pageSentence.endsWith("."))
			pageSentence += ". ";
		return pageSentence;
	}

	public static boolean isProhibitiveWordsOccurOrStartWith(String sentenceLowercase){
		for(String o: occurs){
			if (sentenceLowercase.contains(o)){
				//System.out.println("Found prohibited occurrence "+ o +" \n in sentence = "+  sentenceLowercase);
				return true;
			}
		}

		for(String o: occursStartsWith){
			if (sentenceLowercase.startsWith(o)){
				//System.out.println("Found prohibited occurrence Start With  "+ o +" \n in sentence = "+  sentenceLowercase);
				return true;
			}
		}
		//  || sentTry.endsWith("the")
		//  || sentTry.endsWith("the.") || sentTry.startsWith("below") 
		return false;
	}

	public static void main(String[] args) {
		
		String sentence = "Accepted sentence: Educational. Video. About Us menu. Home. Nobel Prizes and Laureates. Nobel Prizes and Laureates. Physics Prize. Chemistry Prize. Medicine Prize. Literature Prize. Peace Prize. Prize in Economic Sciences. Quick Facts. Nomination. Nomination. Physics Prize. Chemistry Prize. Medicine Prize. Literature Prize. Peace Prize. Prize in Economic Sciences. Nomination Archive. Ceremonies. Ceremonies. Ceremony Archive. Nobel Banquet Menus. Nobel Banquet Dress Code. The Queen's Gowns. Eyewitness Reports. Alfred Nobel. Alfred Nobel. Alfred Nobel's Will. Alfred Nobel's Life. Private Library of Alfred Nobel. Books on Alfred Nobel. Events. Events. Nobel Week Dialogue. Nobel Prize Inspiration Initiative. Nobel Prize Concert. Exhibitions at the Nobel Museum. Exhibitions at the Nobel Peace Center. About Us. Nobel Prizes and Laureates. Physics PrizesChemistry PrizesMedicine PrizesLiterature PrizesPeace PrizesPrize in Economic Sciences. About the Nobel Prize in Physics 1921. Albert Einstein. Facts. Biographical. Nobel Lecture. Banquet Speech. Documentary. Photo Gallery. Questions and Answers. Other Resources. All Nobel Prizes in Physics. All Nobel Prizes in 1921. The Nobel Prize in Physics 1921. Albert Einstein. Questions and Answers. Question: When was Albert Einstein born . Answer: Albert Einstein was born on 14 March 1879. Question: Where was he born . Answer: He was born in Ulm, Germany. Question: When did he die . Answer: He died 18 April 1955 in Princeton, New Jersey, USA. Question: Who were his parents . Answer: His father was Hermann Einstein and his mother was Pauline Einstein (born Koch). Question: Did he have any sisters and brothers . Answer: He had one sister named Maja. Question: Did he marry and have children . Answer: He was married to Mileva Mari between 1903 and 1919. They had three children, Lieserl (born 1902), Hans Albert (born 1904) and Eduard (born 1910). He married Elsa L Kwenthal in 1919 and they lived together until her death in 1936. Question: Where did he receive his education . Answer: He received his main education at the following schools:. Catholic elementary school in Munich, Germany (1885-1888). Luitpold Gymnasium in Munich, Germany (1888-1894). Cantonal school in Aarau, Switzerland (1895-1896). Swiss Federal Institute of Technology in Zurich, Switzerland (1896-1900). Ph.D. from Zurich University, Switzerland (1905). Question: When was Albert Einstein awarded the Nobel Prize in Physics . Answer: The Nobel Prize Awarding Institution, the Royal Swedish Academy of Sciences, decided to reserve the Nobel Prize in Physics in 1921, and therefore no Physics Prize was awarded that year.";
		
		String res = GeneratedSentenceProcessor.acceptableMinedSentence(sentence);
		String para;
		para = "inventions of albert einstein                            what was albert einsteins invention                            invention of einstein                            what were albert einsteins inventions ";

		para = para.replaceAll("  [A-Z]", ". $0");
		System.out.println(para);

		para = "Page 2 of 93";

		System.exit(0);
		RelatedSentenceFinder f = new RelatedSentenceFinder();
		try {
			List<HitBase> hits = f.findRelatedOpinionsForSentence(
							"Give me a break, there is no reason why you can't retire in ten years if you had been a rational investor and not a crazy trader",
							Arrays.asList("Give me a break there is no reason why you can't retire in ten years if you had been a rational investor and not a crazy trader. " +
											"For example you went to cash in 2008 and stay in cash until now you made nothing. " +
											"Whereas people who rode out the storm are doing fine so let's quit focusing on the loser who think they are so smart and went to 100% cash and are wondering what happen. " +
											"Its a market that always moves unlike your mattress."));
			StringBuilder buf = new StringBuilder();

			for (HitBase h : hits) {
				List<Fragment> frags = h.getFragments();
				for (Fragment fr : frags) {
					if (fr.getResultText() != null && fr.getResultText().length() > 3)
						buf.append(fr.getResultText());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static String normalizeForSentenceSplitting(String pageContent) {
		pageContent.replace("Jan.", "January").replace("Feb.", "February")
		.replace("Mar.", "March").replace("Apr.", "April")
		.replace("Jun.", "June").replace("Jul.", "July")
		.replace("Aug.", "August").replace("Sep.", "September")
		.replace("Oct.", "October").replace("Nov.", "November")
		.replace("Dec.", "December");

		return pageContent;

	}
}

/*

if (sentTry.indexOf("click here")>-1 || sentTry.indexOf(" wikip") > -1
|| sentTry.indexOf("copyright") > -1
|| sentTry.indexOf("operating hours") > -1
|| sentTry.indexOf("days per week") > -1
|| sentTry.indexOf("click for") > -1 || sentTry.indexOf("photos") > -1
|| sentTry.indexOf("find the latest") > -1
|| sentTry.startsWith("subscribe")
|| sentTry.indexOf("Terms of Service") > -1
|| sentTry.indexOf("clicking here") > -1
|| sentTry.indexOf("skip to") > -1 || sentTry.indexOf("sidebar") > -1
|| sentTry.indexOf("Tags:") > -1 || sentTry.startsWith("Posted by")
|| sentTry.indexOf("available online") > -1
|| sentTry.indexOf("get online") > -1
|| sentTry.indexOf("buy online") > -1
|| sentTry.indexOf("not valid") > -1 || sentTry.indexOf("discount") > -1
|| sentTry.indexOf("official site") > -1
|| sentTry.indexOf("this video") > -1
|| sentTry.indexOf("this book") > -1
|| sentTry.indexOf("this product") > -1
|| sentTry.indexOf("paperback") > -1 || sentTry.indexOf("hardcover") > -1
|| sentTry.indexOf("audio cd") > -1
|| sentTry.indexOf("related searches") > -1
|| sentTry.indexOf("permission is granted") > -1
|| sentTry.indexOf("[edit") > -1
|| sentTry.indexOf("edit categories") > -1
|| sentTry.indexOf("free license") > -1
|| sentTry.indexOf("permission is granted") > -1
|| sentTry.indexOf("under the terms") > -1
|| sentTry.indexOf("rights reserved") > -1
|| sentTry.indexOf("wikipedia") > -1 || sentTry.endsWith("the")
|| sentTry.endsWith("the.") || sentTry.startsWith("below") 
|| sentTry.indexOf("recipient of")>-1 || sentTry.indexOf("this message")>-1 
||sentTry.indexOf( "mailing list")>-1 ||sentTry.indexOf( "purchase order")>-1
||sentTry.indexOf( "mon-fri")>-1 ||sentTry.indexOf( "email us")>-1 ||sentTry.indexOf( "privacy pol")>-1 ||sentTry.indexOf( "back to top")>-1 
||sentTry.indexOf( "click here")>-1 ||sentTry.indexOf( "for details")>-1 ||sentTry.indexOf( "assistance?")>-1 ||sentTry.indexOf( "chat live")>-1
||sentTry.indexOf( "free shipping")>-1 ||sentTry.indexOf( "company info")>-1 ||sentTry.indexOf( "satisfaction g")>-1 ||sentTry.indexOf( "contact us")>-1
||sentTry.startsWith( "fax") ||sentTry.startsWith( "write") || sentTry.startsWith( "email")||sentTry.indexOf( "conditions")>-1 ||sentTry.indexOf( "chat live")>-1
||sentTry.startsWith( "we ") ||sentTry.indexOf( "the recipient")>-1 ||sentTry.indexOf( "day return")>-1 ||sentTry.indexOf( "days return")>-1

||sentTry.startsWith( "fax") ||sentTry.indexOf( "refund it")>-1 || sentTry.indexOf( "your money")>-1
||sentTry.indexOf( "cond???")>-1 ||sentTry.indexOf( "purchase orders")>-1
||sentTry.startsWith( "exchange it ") ||sentTry.indexOf( "return it")>-1 ||sentTry.indexOf( "day return")>-1 ||sentTry.indexOf( "days return")>-1
)
return null;

*/