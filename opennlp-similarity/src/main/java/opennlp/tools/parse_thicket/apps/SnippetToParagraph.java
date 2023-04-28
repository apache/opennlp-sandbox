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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.ContentGeneratorSupport;
import opennlp.tools.similarity.apps.Fragment;
import opennlp.tools.similarity.apps.GeneratedSentenceProcessor;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.TextProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnippetToParagraph extends ContentGeneratorSupport /*RelatedSentenceFinder */{
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private final PageFetcher pFetcher = new PageFetcher();

	public HitBase formTextFromOriginalPageGivenSnippetDirect(HitBase item) {

		// put orig sentence in structure
		List<String> origs = new ArrayList<>();

		item.setOriginalSentences(origs);
		String title = item.getTitle().replace("<b>", " ").replace("</b>", " ")
				.replace("  ", " ").replace("  ", " ");
		// generation results for this sentence
		List<Fragment> result = new ArrayList<>();
		// form plain text from snippet
		String snapshot = item.getAbstractText().replace("<b>", " ")
				.replace("</b>", " ").replace("  ", " ").replace("  ", " ");

		String snapshotMarked = snapshot.replace("...",
				" _should_find_orig_ . _should_find_orig_");
		List<String> fragments = TextProcessor.splitToSentences(snapshotMarked);
		List<String> allFragms = new ArrayList<>(fragments);

		List<String> sents = new ArrayList<>();
		String downloadedPage;
		try {
			if (snapshotMarked.length() != snapshot.length()) {
				downloadedPage = pFetcher.fetchPage(item.getUrl());
				if (downloadedPage != null && downloadedPage.length() > 100) {
					item.setPageContent(downloadedPage);
					String pageContent = Utils.fullStripHTML(item.getPageContent());
					pageContent = GeneratedSentenceProcessor
							.normalizeForSentenceSplitting(pageContent);
					pageContent = pageContent.trim().replaceAll("  [A-Z]", ". $0")// .replace("  ",
							// ". ")
							.replace("..", ".").replace(". . .", " ").trim(); // sometimes
					// html breaks
					// are converted
					// into ' ' (two
					// spaces), so
					// we need to
					// put '.'
					sents = TextProcessor.splitToSentences(pageContent);

				}
			}
		} catch (Exception e) {
			System.err.println("Problem downloading  the page and splitting into sentences");
			return item;
		}

		for (String fragment : allFragms) {
			String followSent = null;
			if (fragment.length() < 50)
				continue;
			String pageSentence = "";
			// try to find original sentence from webpage
			if (fragment.contains("_should_find_orig_") && sents != null
					&& sents.size() > 0)
				try {
					String[] mainAndFollowSent = getFullOriginalSentenceFromWebpageBySnippetFragment(
							fragment.replace("_should_find_orig_", ""), sents.toArray(new String[]{}));
					pageSentence = mainAndFollowSent[0];
					followSent = mainAndFollowSent[1];

				} catch (Exception e) {
					LOG.error(e.getLocalizedMessage(), e);
				}
			else
				// or get original snippet
				pageSentence = fragment;
			if (pageSentence != null)
				pageSentence = pageSentence.replace("_should_find_orig_", "");
			String pageSentenceProc = GeneratedSentenceProcessor
					.acceptableMinedSentence(pageSentence);
			if (pageSentenceProc != null) {
				pageSentenceProc = GeneratedSentenceProcessor
						.processSentence(pageSentenceProc);
				if (followSent != null) {
					pageSentenceProc += " "
							+ GeneratedSentenceProcessor.processSentence(followSent);
				}

				pageSentenceProc = Utils.convertToASCII(pageSentenceProc);
				Fragment f = new Fragment(pageSentenceProc, 1);
				f.setSourceURL(item.getUrl());
				f.fragment = fragment;
				result.add(f);
				LOG.debug("Accepted sentence: {} | with title = {}", pageSentenceProc, title);
				LOG.debug("For fragment = {}", fragment);
			} else
				LOG.debug("Rejected sentence due to wrong area at webpage: {}", pageSentence);
		} 


		item.setFragments(result);
		return item;
	}

	public HitBase formTextFromOriginalPageGivenSnippet(HitBase item) {

		String[] sents = extractSentencesFromPage(item.getUrl());

		String title = item.getTitle().replace("<b>", " ").replace("</b>", " ")
				.replace("  ", " ").replace("  ", " ");
		// generation results for this sentence
		List<String> result = new ArrayList<>();
		// form plain text from snippet
		String snapshot = item.getAbstractText().replace("<b>", " ")
				.replace("</b>", " ").replace("  ", " ").replace("  ", " ").replace("\"", "");

		String snapshotMarked = snapshot.replace(" ...", ".");
		List<String> fragments = TextProcessor.splitToSentences(snapshotMarked);
		if (fragments.size()<3 && StringUtils.countMatches(snapshotMarked, ".")>1){
			snapshotMarked = snapshotMarked.replace("..", "&").replace(".", "&");
			String[] fragmSents = snapshotMarked.split("&");
			fragments = Arrays.asList(fragmSents);
		}

		for (String f : fragments) {
			String followSent = null;
			if (f.length() < 50)
				continue;
			String pageSentence;
			// try to find original sentence from webpage

			try {
				String[] mainAndFollowSent = getFullOriginalSentenceFromWebpageBySnippetFragment(f, sents);
				pageSentence = mainAndFollowSent[0];
				followSent = mainAndFollowSent[1];
				if (pageSentence!=null)
					result.add(pageSentence);
				else {
					result.add(f);
					LOG.warn("Could not find the original sentence \n {} \n in the page ", f);
				}
				//if (followSent !=null)
				//	result.add(followSent);
			} catch (Exception e) {
				LOG.error(e.getLocalizedMessage(), e);
			}
		}
		item.setOriginalSentences(result);
		return item;
	}

	public  List<String> cleanListOfSents(List<String> sents) {
		List<String> sentsClean = new ArrayList<>();
		for (String s : sents) {
			if (s == null || s.trim().length() < 30 || s.length() < 20)
				continue;
			sentsClean.add(s);
		}
		return sentsClean;
	}

	private String[] removeDuplicates(String[] hits) {
		StringDistanceMeasurer meas = new StringDistanceMeasurer();

		List<Integer> idsToRemove = new ArrayList<>();
		List<String> hitsDedup = new ArrayList<>();
		try {
			for (int i = 0; i < hits.length; i++)
				for (int j = i + 1; j < hits.length; j++) {
					String title1 = hits[i];
					String title2 = hits[j];
					if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
						continue;
					if (meas.measureStringDistance(title1, title2) > 0.7) {
						idsToRemove.add(j); // dupes found, later list member to
						// be deleted
					}
				}
			for (int i = 0; i < hits.length; i++)
				if (!idsToRemove.contains(i))
					hitsDedup.add(hits[i]);
			if (hitsDedup.size() < hits.length) {
				System.out.println("Removed duplicates from relevant search results, including "
						+ hits[idsToRemove.get(0)]);
			}
		}
		catch (Exception e) {
			System.out.println("Problem removing duplicates from relevant images");
		}

		return hitsDedup.toArray(new String[0]);

	}

	public String[] extractSentencesFromPage(String url) {

		int maxSentsFromPage= 100;
		List<String[]> results = new ArrayList<>();

		String downloadedPage = pFetcher.fetchPage(url, 20000);
		if (downloadedPage == null || downloadedPage.length() < 100) {
			return null;
		}

		String pageOrigHTML = pFetcher.fetchOrigHTML(url);

		downloadedPage= downloadedPage.replace("     ", "&");
		downloadedPage = downloadedPage.replaceAll("(?:&)+", "#");
		String[] sents = downloadedPage.split("#");
		List<TextChunk> sentsList = new ArrayList<>();
		for(String s: sents){
			s = s.trim().replace("  ", ". ").replace("..", ".").replace(". . .", " ")
					.replace(": ", ". ").replace("- ", ". ").
					replace (". .",".").trim();
			sentsList.add(new TextChunk(s, s.length()));
		}

		sentsList.sort(new TextChunkComparable());
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
	
	protected String[] cleanSplitListOfSents(String[] longestSents){
	float minFragmentLength = 40, minFragmentLengthSpace=4;

	List<String> sentsClean = new ArrayList<>();
	for (String sentenceOrMultSent : longestSents) {
		if (sentenceOrMultSent==null || sentenceOrMultSent.length()<20)
			continue;
		if (GeneratedSentenceProcessor.acceptableMinedSentence(sentenceOrMultSent)==null){
			LOG.debug("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = {}", sentenceOrMultSent);
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
		for(String s : furtherSplit) {
			if (s.indexOf('|') >- 1)
				continue;
			s = s.replace("<em>"," ").replace("</em>"," ");
			s = Utils.convertToASCII(s);
			sentsClean.add(s);
		}
	}

	return sentsClean.toArray(new String[0]);
}
	private String[] verifyEnforceStartsUpperCase(String[] sents) {
		for(int i=0; i<sents.length; i++) {
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
		List<String> results = new ArrayList<>();
		for(String feature: productFeaturesList){
			if (feature.startsWith("Unlimited Free") || feature.startsWith("View Larger") || feature.startsWith("View Larger") || feature.indexOf("shipping")>0)
				continue;
			results.add(feature);
		}
		return results;
	}
	public static class TextChunk {
		public TextChunk(String s, int length) {
			this.text = s;
			this.len = length;
		}
		public final String text;
		public final int len;
	}

	public static class TextChunkComparable implements Comparator<TextChunk> {

		@Override
		public int compare(TextChunk ch1, TextChunk ch2) {
			return Integer.compare(ch1.len, ch2.len);
		}
	}

}

