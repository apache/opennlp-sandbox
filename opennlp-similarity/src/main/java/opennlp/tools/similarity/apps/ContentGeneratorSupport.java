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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import opennlp.tools.parse_thicket.Triple;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph.TextChunk;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph.TextChunkComparable;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

import org.apache.commons.lang.StringUtils;

/*
 * This class supports content generation by static functions
 * 
 */

public class ContentGeneratorSupport {
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.ContentGeneratorSupport");

	/**
	 * Takes a sentence and extracts noun phrases and entity names to from search
	 * queries for finding relevant sentences on the web, which are then subject
	 * to relevance assessment by Similarity. Search queries should not be too
	 * general (irrelevant search results) or too specific (too few search
	 * results)
	 * 
	 * @param String
	 *          input sentence to form queries
	 * @return List<String> of search expressions
	 */
	public static List<String> buildSearchEngineQueryFromSentence(String sentence) {
		ParseTreeChunk matcher = new ParseTreeChunk();
		ParserChunker2MatcherProcessor pos = ParserChunker2MatcherProcessor
				.getInstance();
		List<List<ParseTreeChunk>> sent1GrpLst = null;

		List<ParseTreeChunk> nPhrases = pos
				.formGroupedPhrasesFromChunksForSentence(sentence).get(0);
		List<String> queryArrayStr = new ArrayList<String>();
		for (ParseTreeChunk ch : nPhrases) {
			String query = "";
			int size = ch.getLemmas().size();

			for (int i = 0; i < size; i++) {
				if (ch.getPOSs().get(i).startsWith("N")
						|| ch.getPOSs().get(i).startsWith("J")) {
					query += ch.getLemmas().get(i) + " ";
				}
			}
			query = query.trim();
			int len = query.split(" ").length;
			if (len < 2 || len > 5)
				continue;
			if (len < 4) { // every word should start with capital
				String[] qs = query.split(" ");
				boolean bAccept = true;
				for (String w : qs) {
					if (w.toLowerCase().equals(w)) // idf only two words then
						// has to be person name,
						// title or geo location
						bAccept = false;
				}
				if (!bAccept)
					continue;
			}

			query = query.trim().replace(" ", " +");
			query = " +" + query;

			queryArrayStr.add(query);

		}
		if (queryArrayStr.size() < 1) { // release constraints on NP down to 2
			// keywords
			for (ParseTreeChunk ch : nPhrases) {
				String query = "";
				int size = ch.getLemmas().size();

				for (int i = 0; i < size; i++) {
					if (ch.getPOSs().get(i).startsWith("N")
							|| ch.getPOSs().get(i).startsWith("J")) {
						query += ch.getLemmas().get(i) + " ";
					}
				}
				query = query.trim();
				int len = query.split(" ").length;
				if (len < 2)
					continue;

				query = query.trim().replace(" ", " +");
				query = " +" + query;

				queryArrayStr.add(query);

			}
		}

		queryArrayStr = removeDuplicatesFromQueries(queryArrayStr);
		queryArrayStr.add(sentence);

		return queryArrayStr;

	}

	public static String[] cleanListOfSents(String[] sents) {
		List<String> sentsClean = new ArrayList<String>();
		for (String s : sents) {
			if (s == null || s.trim().length() < 30 || s.length() < 20)
				continue;
			sentsClean.add(s);
		}
		return (String[]) sentsClean.toArray(new String[0]);
	}

	public static String cleanSpacesInCleanedHTMLpage(String pageContent){ //was 4 spaces 
		//was 3 spaces => now back to 2
		//TODO - verify regexp!!
		pageContent = pageContent.trim().replaceAll("([a-z])(\\s{2,3})([A-Z])", "$1. $3")
				.replace("..", ".").replace(". . .", " ").
				replace(".    .",". ").trim(); // sometimes   html breaks are converted into ' ' (two spaces), so
		// we need to put '.'
		return pageContent;
	}

	/**
	 * remove dupes from queries to easy cleaning dupes and repetitive search
	 * afterwards
	 * 
	 * @param List
	 *          <String> of sentences (search queries, or search results
	 *          abstracts, or titles
	 * @return List<String> of sentences where dupes are removed
	 */
	public static List<String> removeDuplicatesFromQueries(List<String> hits) {
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double dupeThresh = 0.8; // if more similar, then considered dupes was
		// 0.7
		List<Integer> idsToRemove = new ArrayList<Integer>();
		List<String> hitsDedup = new ArrayList<String>();
		try {
			for (int i = 0; i < hits.size(); i++)
				for (int j = i + 1; j < hits.size(); j++) {
					String title1 = hits.get(i);
					String title2 = hits.get(j);
					if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
						continue;
					if (meas.measureStringDistance(title1, title2) > dupeThresh) {
						idsToRemove.add(j); // dupes found, later list member to
						// be deleted

					}
				}

			for (int i = 0; i < hits.size(); i++)
				if (!idsToRemove.contains(i))
					hitsDedup.add(hits.get(i));

			if (hitsDedup.size() < hits.size()) {
				LOG.info("Removed duplicates from formed query, including "
						+ hits.get(idsToRemove.get(0)));
			}

		} catch (Exception e) {
			LOG.severe("Problem removing duplicates from query list");
		}

		return hitsDedup;

	}

	/**
	 * remove dupes from search results
	 * 
	 * @param List
	 *          <HitBase> of search results objects
	 * @return List<String> of search results objects where dupes are removed
	 */
	public static List<HitBase> removeDuplicatesFromResultantHits(
			List<HitBase> hits) {
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double dupeThresh = // 0.8; // if more similar, then considered dupes was
				0.7;
		List<Integer> idsToRemove = new ArrayList<Integer>();
		List<HitBase> hitsDedup = new ArrayList<HitBase>();
		try {
			for (int i = 0; i < hits.size(); i++)
				for (int j = i + 1; j < hits.size(); j++) {
					HitBase hit2 = hits.get(j);
					List<Fragment> fragmList1 = hits.get(i).getFragments();
					List<Fragment> fragmList2 = hits.get(j).getFragments();
					List<Fragment> fragmList2Results = new ArrayList<Fragment>(fragmList2);
					for (Fragment f1 : fragmList1)
						for (Fragment f2 : fragmList2) {
							String sf1 = f1.getResultText();
							String sf2 = f2.getResultText();
							if (StringUtils.isEmpty(sf1) || StringUtils.isEmpty(sf1))
								continue;
							if (meas.measureStringDistance(sf1, sf2) > dupeThresh) {
								fragmList2Results.remove(f2);
								LOG.info("Removed duplicates from formed fragments list: "
										+ sf2);
							}
						}

					hit2.setFragments(fragmList2Results);
					hits.set(j, hit2);
				}
		} catch (Exception e) {
			LOG.severe("Problem removing duplicates from list of fragment");
		}
		return hits;
	}



	// given a fragment from snippet, finds an original sentence at a webpage by
	// optimizing alignmemt score
	public static String[] getFullOriginalSentenceFromWebpageBySnippetFragment(
			String fragment, String[] sents) {
		if (fragment.trim().length() < 15)
			return null;

		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		Double dist = 0.0;
		String result = null, followSent = "";
		for (int i = 0; i < sents.length; i++) {
			String s = sents[i];
			if (s == null || s.length() < 30)
				continue;
			Double distCurr = meas.measureStringDistance(s, fragment);
			if (distCurr > dist && distCurr > 0.4) {
				result = s;
				dist = distCurr;
				try {
					if (i < sents.length - 1 && sents[i + 1].length() > 60) { 
						String f1 = GeneratedSentenceProcessor.acceptableMinedSentence(sents[i+1]);
						if (f1!=null){
							followSent = f1;
						}
					}

					if (i < sents.length - 2 && sents[i + 2].length() > 60) {
						String f2 = GeneratedSentenceProcessor.acceptableMinedSentence(sents[i+2]);
						if (f2!=null){
							followSent += " "+f2;
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return new String[] { result, followSent };
	}

	// given a fragment from snippet, finds an original sentence at a webpage by
	// optimizing alignmemt score
	public static String[] getBestFullOriginalSentenceFromWebpageBySnippetFragment(
			String fragment, String[] sents) {
		if (fragment.trim().length() < 15)
			return null;
		int bestSentIndex = -1;
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		Double distBest = 10.0; // + sup
		String result = null, followSent = null;
		for (int i = 0; i < sents.length; i++) {
			String s = sents[i];
			if (s == null || s.length() < 30)
				continue;
			Double distCurr = meas.measureStringDistance(s, fragment);
			if (distCurr > distBest) {
				distBest = distCurr;
				bestSentIndex = i;
			}

		}
		if (distBest > 0.4) {
			result = sents[bestSentIndex];

			if (bestSentIndex < sents.length - 1
					&& sents[bestSentIndex + 1].length() > 60) {
				followSent = sents[bestSentIndex + 1];
			}

		}

		return new String[] { result, followSent };
	}

	public String[] extractSentencesFromPage(String downloadedPage)
	{

		int maxSentsFromPage= 100;
		List<String[]> results = new ArrayList<String[]>();

		//String pageOrigHTML = pFetcher.fetchOrigHTML(url);

		downloadedPage= downloadedPage.replace("     ", "&");
		downloadedPage = downloadedPage.replaceAll("(?:&)+", "#");
		String[] sents = downloadedPage.split("#");
		List<TextChunk> sentsList = new ArrayList<TextChunk>();
		for(String s: sents){
			s = ContentGeneratorSupport.cleanSpacesInCleanedHTMLpage(s);
			sentsList.add(new TextChunk(s, s.length()));
		}

		Collections.sort(sentsList, new TextChunkComparable());
		String[] longestSents = new String[maxSentsFromPage];
		int j=0;
		int initIndex = sentsList.size()-1 -maxSentsFromPage;
		if (initIndex<0)
			initIndex = 0;
		for(int i=initIndex; i< sentsList.size() && j<maxSentsFromPage ; i++){
			longestSents[j] = sentsList.get(i).text;
			j++;
		}

		sents = cleanSplitListOfSents(longestSents);

		//sents = removeDuplicates(sents);
		//sents = verifyEnforceStartsUpperCase(sents);

		return sents;
	}

	public class TextChunk {
		public TextChunk(String s, int length) {
			this.text = s;
			this.len = length;
		}
		public String text;
		public int len;
	}

	public class TextChunkComparable implements Comparator<TextChunk>
	{
		public int compare(TextChunk ch1, TextChunk ch2)
		{
			if (ch1.len>ch2.len)
				return 1;
			else if (ch1.len<ch2.len)
				return  -1;
			else return 0;

		}
	}

	protected String[] cleanSplitListOfSents(String[] longestSents){
		float minFragmentLength = 40, minFragmentLengthSpace=4;

		List<String> sentsClean = new ArrayList<String>();
		for (String sentenceOrMultSent : longestSents)
		{
			if (sentenceOrMultSent==null || sentenceOrMultSent.length()<20)
				continue;
			if (GeneratedSentenceProcessor.acceptableMinedSentence(sentenceOrMultSent)==null){
				System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+sentenceOrMultSent);
				continue;
			}
			// aaa. hhh hhh.  kkk . kkk ll hhh. lll kkk n.
			int numOfDots = sentenceOrMultSent.replace('.','&').split("&").length;
			float avgSentenceLengthInTextPortion = (float)sentenceOrMultSent.length() /(float) numOfDots;
			if ( avgSentenceLengthInTextPortion<minFragmentLength)
				continue;
			// o oo o ooo o o o ooo oo ooo o o oo
			numOfDots = sentenceOrMultSent.replace(' ','&').split("&").length;
			avgSentenceLengthInTextPortion = (float)sentenceOrMultSent.length() /(float) numOfDots;
			if ( avgSentenceLengthInTextPortion<minFragmentLengthSpace)
				continue;

			List<String> furtherSplit = TextProcessor.splitToSentences(sentenceOrMultSent);

			// forced split by ',' somewhere in the middle of sentence
			// disused - Feb 26 13
			//furtherSplit = furtherMakeSentencesShorter(furtherSplit);
			furtherSplit.remove(furtherSplit.size()-1);
			for(String s : furtherSplit){
				if (s.indexOf('|')>-1)
					continue;
				s = s.replace("<em>"," ").replace("</em>"," ");
				s = Utils.convertToASCII(s);
				sentsClean.add(s);
			}
		}
		return (String[]) sentsClean.toArray(new String[0]);
	}	

	protected String[] cleanSplitListOfSentsFirstSplit(String[] longestSents){
		float minFragmentLength = 40, minFragmentLengthSpace=4;

		List<String> sentsClean = new ArrayList<String>();
		for (String sentenceOrMultSent : longestSents)
		{
			if (sentenceOrMultSent==null || sentenceOrMultSent.length()<minFragmentLength)
				continue;
			List<String> furtherSplit = TextProcessor.splitToSentences(sentenceOrMultSent);
			for(String sentence: furtherSplit ){
				if (sentence==null || sentence.length()<20)
					continue;
				if (GeneratedSentenceProcessor.acceptableMinedSentence(sentence)==null){
					//System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+sentenceOrMultSent);
					continue;
				}
				// aaa. hhh hhh.  kkk . kkk ll hhh. lll kkk n.
				int numOfDots = sentence.replace('.','&').split("&").length;
				float avgSentenceLengthInTextPortion = (float)sentenceOrMultSent.length() /(float) numOfDots;
				if ( avgSentenceLengthInTextPortion<minFragmentLength)
					continue;
				// o oo o ooo o o o ooo oo ooo o o oo
				numOfDots = sentence.replace(' ','&').split("&").length;
				avgSentenceLengthInTextPortion = (float)sentence.length() /(float) numOfDots;
				if ( avgSentenceLengthInTextPortion<minFragmentLengthSpace)
					continue;



				// forced split by ',' somewhere in the middle of sentence
				// disused - Feb 26 13
				//furtherSplit = furtherMakeSentencesShorter(furtherSplit);
				//furtherSplit.remove(furtherSplit.size()-1);

				if (sentence.indexOf('|')>-1)
					continue;
				sentence = Utils.convertToASCII(sentence);
				sentsClean.add(sentence);
			}
		}
		return (String[]) sentsClean.toArray(new String[0]);
	}

	public static String getPortionOfTitleWithoutDelimiters(String title){
		String[] delimiters = new String[]{"\\+","-", "=", "_", "\\)", "\\|"};
		for(String delim: delimiters ){
			String[] split = title.split(delim);
			if (split.length>1){
				for(String s: split){
					if (s.indexOf(".")<0)
						return s;
				}
			}
		}

		return title;
	}

	public static void main(String[] args){
		String s = "You can grouP   parts  Of your regular expression  In your pattern   You grouP  elements";
		//with round brackets, e.g., ()." +
		//		" This allows you to assign a repetition operator to a complete group.";
		String sr = s.replaceAll("([a-z])(\\s{2,3})([A-Z])", "$1. $3");
		String sr1 = s.replaceAll("  [A-Z]", ". $0");
		sr = s.replaceAll("[a-z]  [A-Z]", ". $1");
		sr1 = s.replaceAll("  [A-Z]", ". $1");
	}

	public static boolean problematicHitList(List<HitBase> hits){
		if (hits.size()<1)
			return true;
		for(HitBase hit: hits){
			if (!hit.getFragments().isEmpty())
				return false;
		}
		return true;		
	}
}



