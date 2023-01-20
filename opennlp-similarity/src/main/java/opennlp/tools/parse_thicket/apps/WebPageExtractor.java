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
package opennlp.tools.parse_thicket.apps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.GeneratedSentenceProcessor;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class WebPageExtractor {
	protected PageFetcher pageFetcher = new PageFetcher();
	
	protected ParserChunker2MatcherProcessor nlProc;
	protected MostFrequentWordsFromPageGetter mostFrequentWordsFromPageGetter = new MostFrequentWordsFromPageGetter();

	protected static int sentThresholdLength = 70;

	public List<String[]> extractSentencesWithPotentialProductKeywords(String url)
	{
		int maxSentsFromPage= 20;
		List<String[]> results = new ArrayList<String[]>();

		String downloadedPage = pageFetcher.fetchPage(url, 20000);
		if (downloadedPage == null || downloadedPage.length() < 100)
		{
			return null;
		}

		String pageOrigHTML = pageFetcher.fetchOrigHTML(url);
		String pageTitle = StringUtils.substringBetween(pageOrigHTML, "<title>", "</title>" );
		pageTitle = pageTitle.replace("  ", ". ").replace("..", ".").replace(". . .", " ")
				.replace(": ", ". ").replace("- ", ". ").replace(" |", ". ").
				replace (". .",".").trim();
		List<String> pageTitles = new ArrayList<String>();
		pageTitles.addAll(TextProcessor.splitToSentences(pageTitle));
		pageTitles.addAll(Arrays.asList(pageTitle.split(".")));

		String[] headerSections = pageOrigHTML.split("<h2");
		if (headerSections.length<2)
			headerSections = pageOrigHTML.split("<h3");
		for(String section: headerSections){

			String header = StringUtils.substringBetween(section, ">", "<");
			if (header!=null && header.length()>20)
				pageTitles.add(header);
		}


		downloadedPage= downloadedPage.replace("     ", "&");
		downloadedPage = downloadedPage.replaceAll("(?:&)+", "#");
		String[] sents = downloadedPage.split("#");
		List<TextChunk> sentsList = new ArrayList<TextChunk>();
		for(String s: sents){
			s = s.trim().replace("  ", ". ").replace("..", ".").replace(". . .", " ")
					.replace(": ", ". ").replace("- ", ". ").
					replace (". .",".").trim();
			sentsList.add(new TextChunk(s, s.length()));
		}

		Collections.sort(sentsList, new TextChunkComparable());


		String[] longestSents = new String[maxSentsFromPage];
		int j=0;
		for(int i=sentsList.size() -maxSentsFromPage; i< sentsList.size(); i++){
			longestSents[j] = sentsList.get(i).text;
			j++;
		}

		sents = cleanListOfSents(longestSents);

		List<String>  mosFrequentWordsListFromPage = mostFrequentWordsFromPageGetter. getMostFrequentWordsInTextArr(sents);
		// mostFrequentWordsFromPageGetter. getMostFrequentWordsInText(downloadedPage);

		results.add(pageTitles.toArray(new String[0]));
		results.add(mosFrequentWordsListFromPage.toArray(new String[0]));
		results.add(sents);

		return results;
	}

	protected String[] cleanListOfSents(String[] longestSents)
	{
		List<String> sentsClean = new ArrayList<String>();
		for (String sentenceOrMultSent : longestSents)
		{
			List<String> furtherSplit = TextProcessor.splitToSentences(sentenceOrMultSent);
			for(String s : furtherSplit){
				if (s.replace('.','&').split("&").length>3)
					continue;
				if (s.indexOf('|')>-1)
					continue;
				if (s == null || s.trim().length() < sentThresholdLength || s.length() < sentThresholdLength + 10)
					continue;
				if (GeneratedSentenceProcessor.acceptableMinedSentence(s)==null){
					// TODO OPENNLP-1454 Candidate for logger.debug(...) if required/helpful
					// System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+s);
					continue;
				}
				sentsClean.add(s);
			}
		}
		return sentsClean.toArray(new String[0]);
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
	
	public static void main(String[] args){
		WebPageExtractor extractor = new WebPageExtractor();
		List<String[]> res = 
				extractor.extractSentencesWithPotentialProductKeywords("http://www.sitbetter.com/view/chair/ofm-500-l/ofm--high-back-leather-office-chair/");
		System.out.println(res.get(1));
		
	}

}
