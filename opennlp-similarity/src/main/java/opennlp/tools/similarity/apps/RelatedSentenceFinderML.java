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
import java.util.List;

import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.TextProcessor;

/**
 * This class does content generation in ES, DE etc
 */
public class RelatedSentenceFinderML extends RelatedSentenceFinder {

	public RelatedSentenceFinderML(int ms, int msr, float thresh, String key) {
		this.MAX_STEPS = ms;
		this.MAX_SEARCH_RESULTS = msr;
		this.RELEVANCE_THRESHOLD=thresh;
	}

	public RelatedSentenceFinderML() {
	}

	public List<HitBase> generateContentAbout(String sentence) throws Exception {
		List<HitBase> opinionSentencesToAdd = new ArrayList<>();
		System.out.println(" \n=== Entity to write about = " + sentence);

		List<HitBase> searchResult = new ArrayList<>(); //yrunner.runSearch(sentence, 100);
		if (MAX_SEARCH_RESULTS<searchResult.size())
			searchResult = searchResult.subList(0, MAX_SEARCH_RESULTS);
		//TODO for shorter run
		if (searchResult != null) {
			for (HitBase item : searchResult) { // got some text from .html
				if (item.getAbstractText() != null
						&& !(item.getUrl().indexOf(".pdf") > 0)) { // exclude pdf
					opinionSentencesToAdd
					.add(augmentWithMinedSentencesAndVerifyRelevance(item,
							sentence, null));
				}
			}
		}

		opinionSentencesToAdd = removeDuplicatesFromResultantHits(opinionSentencesToAdd);
		return opinionSentencesToAdd;
	}


	/**
	 * Takes single search result for an entity which is the subject of the essay
	 * to be written and forms essey sentences from the title, abstract, and
	 * possibly original page
	 * 
	 * @param item The search result
	 * @param originalSentence The seed for the essay to be written
	 * @param sentsAll A list<String> of other sentences in the seed if it is multi-sentence
	 * @return search result
	 */
	public HitBase augmentWithMinedSentencesAndVerifyRelevance(HitBase item,
			String originalSentence, List<String> sentsAll) {
		if (sentsAll == null)
			sentsAll = new ArrayList<>();
		// put orig sentence in structure
		List<String> origs = new ArrayList<>();
		origs.add(originalSentence);
		item.setOriginalSentences(origs);
		String title = item.getTitle().replace("<b>", " ").replace("</b>", " ")
				.replace("  ", " ").replace("  ", " ");
		// generation results for this sentence
		List<Fragment> result = new ArrayList<>();
		// form plain text from snippet
		String snapshot = item.getAbstractText().replace("<b>", " ")
				.replace("</b>", " ").replace("  ", " ").replace("  ", " ");


		// fix a template expression which can be substituted by original if
		// relevant
		String snapshotMarked = snapshot.replace("...",
				" _should_find_orig_ . _should_find_orig_");
		String[] fragments = sm.splitSentences(snapshotMarked);
		List<String> allFragms = new ArrayList<>(Arrays.asList(fragments));

		String[] sents = null;
		String downloadedPage = null;
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
							.replace("..", ".").replace(". . .", " ").trim(); // sometimes   html breaks are converted into ' ' (two spaces), so
					// we need to put '.'
					sents = sm.splitSentences(pageContent);

					sents = ContentGeneratorSupport.cleanListOfSents(sents);
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
					&& sents.length > 0)
				try { 
					// first try sorted sentences from page by lenght approach
					String[] sentsSortedByLength = extractSentencesFromPage(downloadedPage);
					String[] mainAndFollowSent = null;

					try {
						mainAndFollowSent = getFullOriginalSentenceFromWebpageBySnippetFragment(
								fragment.replace("_should_find_orig_", ""), sentsSortedByLength);
					} catch (Exception e) {
						e.printStackTrace();
					}
					// if the above gives null than try to match all sentences from snippet fragment
					if (mainAndFollowSent==null || mainAndFollowSent[0]==null){
						mainAndFollowSent = getFullOriginalSentenceFromWebpageBySnippetFragment(
								fragment.replace("_should_find_orig_", ""), sents);
					}


				} catch (Exception e) {
					e.printStackTrace();
				}
			else
				// or get original snippet
				pageSentence = fragment;
			if (pageSentence != null)
				pageSentence.replace("_should_find_orig_", "");

			// resultant sentence SHOULD NOT be longer than twice the size of
			// snippet fragment
			if (pageSentence != null
					&& (float) pageSentence.length() / (float) fragment.length() < 4.0) { // was 2.0,

				try { // get score from syntactic match between sentence in
					// original text and mined sentence
					double measScore, syntScore, mentalScore = 0.0;

					syntScore = calculateKeywordScore(pageSentence + " " + title, originalSentence);


					if (syntScore < RELEVANCE_THRESHOLD){ // 1.5) { // trying other sents
						for (String currSent : sentsAll) {
							if (currSent.startsWith(originalSentence))
								continue;
							double syntScoreCurr = calculateKeywordScore(currSent, pageSentence);
							if (syntScoreCurr > syntScore) {
								syntScore = syntScoreCurr;
							}
						}
						if (syntScore > RELEVANCE_THRESHOLD) {
							System.out.println("Got match with other sent: " + syntScore);
						}
					}

					measScore = STRING_DISTANCE_MEASURER.measureStringDistance(
							originalSentence, pageSentence);

					// now possibly increase score by finding mental verbs
					// indicating opinions
					for (String s : MENTAL_VERBS) {
						if (pageSentence.contains(s)) {
							mentalScore += 0.3;
							break;
						}
					}

					if ((syntScore > RELEVANCE_THRESHOLD || measScore > 0.5 || mentalScore > 0.5)
							&& measScore < 0.8 && pageSentence.length() > 40) // >70
					{
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
							Fragment f = new Fragment(pageSentenceProc, syntScore + measScore
									+ mentalScore + (double) pageSentenceProc.length()
									/ (double) 50);
							f.setSourceURL(item.getUrl());
							f.fragment = fragment;
							result.add(f);
							System.out.println("Accepted sentence: " + pageSentenceProc
									+ "| with title= " + title);
							System.out.println("For fragment = " + fragment);
						} else
							System.out
							.println("Rejected sentence due to wrong area at webpage: "
									+ pageSentence);
					} else
						System.out.println("Rejected sentence due to low score: "
								+ pageSentence);
					// }
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}
		item.setFragments(result);
		return item;
	}

	private double calculateKeywordScore(String currSent, String pageSentence) {
		List<String>  list1 =TextProcessor.fastTokenize(currSent, false);
		List<String>  list2 =TextProcessor.fastTokenize(pageSentence, false);
		List<String> overlap1 = new ArrayList<>(list1);
		overlap1.retainAll(list2);
		return overlap1.size();

	}


	public static void main(String[] args) {
		RelatedSentenceFinderML f = new RelatedSentenceFinderML();

		List<HitBase> hits;
		try {
			// uncomment the sentence you would like to serve as a seed sentence for
			// content generation for an event description

			// uncomment the sentence you would like to serve as a seed sentence for
			// content generation for an event description
			hits = f.generateContentAbout("Albert Einstein"
					// "Britney Spears - The Femme Fatale Tour"
					// "Rush Time Machine",
					// "Blue Man Group" ,
					// "Belly Dance With Zaharah",
					// "Hollander Musicology Lecture: Danielle Fosler-Lussier, Guest Lecturer",
					// "Jazz Master and arguably the most famous jazz musician alive, trumpeter Wynton Marsalis",
					);
			System.out.println(HitBase.toString(hits));
			System.out.println(HitBase.toResultantString(hits));
			// WordFileGenerator.createWordDoc("Essey about Albert Einstein",
			// hits.get(0).getTitle(), hits);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}