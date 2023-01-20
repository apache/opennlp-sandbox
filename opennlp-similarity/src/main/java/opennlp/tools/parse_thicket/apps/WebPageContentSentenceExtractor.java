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
import java.util.List;

import opennlp.tools.similarity.apps.GeneratedSentenceProcessor;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.TextProcessor;

import org.apache.commons.lang.StringUtils;

public class WebPageContentSentenceExtractor extends WebPageExtractor {
	
	
	

	public List<String> extractSentencesWithPotentialReviewPhrases(String url)
	{
		int maxSentsFromPage = 30;
		String downloadedPage = pageFetcher.fetchPage(url, 20000);
		if (downloadedPage == null || downloadedPage.length() < 100)
		{
			return null;
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
		int j=0;														// -1 removed
		for(int i=sentsList.size()-1 -maxSentsFromPage; i< sentsList.size()-1; i++){
			longestSents[j] = sentsList.get(i).text;
			j++;
		}

		sents = cleanListOfSents(longestSents);
	/*	
		for(int i = 0; i< sents.length; i++){
			sents[i] = sents[i].trim().replace("  ", ". ").replace("..", ".").replace(". . .", " ")
					.replace(": ", ". ").replace("- ", ". ").
					replace (". .",".").trim();
		}
		sents = cleanListOfSents(sents);
	*/	sents = verifyEnforceStartsUpperCase(sents);

		return Arrays.asList(sents);
	}

	private String[] verifyEnforceStartsUpperCase(String[] sents) {
		for(int i=0; i<sents.length; i++){
			String s = sents[i];
			s = StringUtils.trim(s);
			String sFirstChar = s.substring(0, 1);
			if (!sFirstChar.toUpperCase().equals(sFirstChar)){
				s = sFirstChar.toUpperCase()+s.substring(1);
			}
			sents[i] = s;
		}
			return sents;
	}

	private List<String> cleanProductFeatures(List<String> productFeaturesList) {
		List<String> results = new ArrayList<String>();
		for(String feature: productFeaturesList){
			if (feature.startsWith("Unlimited Free") || feature.startsWith("View Larger") || feature.startsWith("View Larger") || feature.indexOf("shipping")>0)
				continue;
			results.add(feature);
		}
		return results;
	}

	// extracts paragraphs from web page
	protected String[] cleanListOfSents(String[] longestSents)
	{
		float minFragmentLength = 40, minFragmentLengthSpace=4;

		List<String> sentsClean = new ArrayList<String>();
		for (String sentenceOrMultSent : longestSents)
		{
			if (GeneratedSentenceProcessor.acceptableMinedSentence(sentenceOrMultSent)==null){
				// System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+sentenceOrMultSent);
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

			sentsClean.add(sentenceOrMultSent);
		}

		return sentsClean.toArray(new String[0]);
	}

	

	private String startWithCapitalSent(String sent) {
		String firstChar = sent.substring(0,1);
		String remainder = sent.substring(1);
		
		return firstChar.toUpperCase()+remainder;
	}

	public HitBase formTextFromOriginalPageGivenSnippet(HitBase hit) {
		List<String> results = extractSentencesWithPotentialReviewPhrases(hit.getUrl());
		hit.setOriginalSentences(results);
		return hit;
	}

	
	
}
