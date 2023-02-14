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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import opennlp.tools.parse_thicket.Triple;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.TextProcessor;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

/**
 * Provides content generation by using web mining and syntactic generalization to get sentences from the web,
 * convert and combine them in the form expected to be readable by humans.
 * <p>
 * These are examples of generated articles, given the article title
 * http://www.allvoices.com/contributed-news/9423860/content/81937916-ichie-sings-jazz-blues-contemporary-tunes
 * http://www.allvoices.com/contributed-news/9415063-britney-spears-femme-fatale-in-north-sf-bay-area
 * 
 */
public class RelatedSentenceFinder {
	private static final Logger LOG = Logger.getLogger("opennlp.tools.similarity.apps.RelatedSentenceFinder");
	PageFetcher pFetcher = new PageFetcher();
	ParserChunker2MatcherProcessor sm = ParserChunker2MatcherProcessor.getInstance();
	protected ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	protected ParseTreeChunk parseTreeChunk = new ParseTreeChunk();
	protected static StringDistanceMeasurer stringDistanceMeasurer = new StringDistanceMeasurer();
	protected BingQueryRunner yrunner = new BingQueryRunner();
	protected int MAX_STEPS = 1;
	protected int MAX_SEARCH_RESULTS = 1;
	protected float RELEVANCE_THRESHOLD = 1.1f;
	protected Set<String> visitedURLs = new HashSet<>();

	// used to indicate that a sentence is an opinion, so more appropriate
	static List<String> MENTAL_VERBS = new ArrayList<>(
			Arrays.asList(new String[] { "want", "know", "believe", "appeal", "ask",
					"accept", "agree", "allow", "appeal", "ask", "assume", "believe",
					"check", "confirm", "convince", "deny", "disagree", "explain",
					"ignore", "inform", "remind", "request", "suggest", "suppose",
					"think", "threaten", "try", "understand" }));

	private static final int MAX_FRAGMENT_SENTS = 10;

	public RelatedSentenceFinder(int ms, int msr, float thresh, String key) {
		this.MAX_STEPS = ms;
		this.MAX_SEARCH_RESULTS = msr;
		this.RELEVANCE_THRESHOLD=thresh;
		yrunner.setKey(key);
	}
	
	int generateContentAboutIter = 0;

	public RelatedSentenceFinder() {

	}

	public void setLang(String lang) {
		yrunner.setLang(lang);

	}
	public List<HitBase> findRelatedOpinionsForSentenceFastAndDummy(String word, List<String> sents) {

		return yrunner.runSearch(word, 100);
	}

	public List<HitBase> findRelatedOpinionsForSentence(String sentence, List<String> sents) {
		List<HitBase> opinionSentencesToAdd = new ArrayList<>();
		System.out.println(" \n\n=== Sentence  = " + sentence);
		List<String> nounPhraseQueries = buildSearchEngineQueryFromSentence(sentence);

		BingQueryRunner yrunner = new BingQueryRunner();
		for (String query : nounPhraseQueries) {
			System.out.println("\nquery = " + query);
			// query += " "+join(MENTAL_VERBS, " OR ") ;
			List<HitBase> searchResult = yrunner.runSearch(query, 100);
			if (searchResult != null) {
				for (HitBase item : searchResult) { // got some text from .html
					if (item.getAbstractText() != null
							&& !(item.getUrl().indexOf(".pdf") > 0)) { // exclude
						// pdf
						opinionSentencesToAdd
						.add(augmentWithMinedSentencesAndVerifyRelevance(item,
								sentence, sents));
						
					}
				}
			}
		}

		return removeDuplicatesFromResultantHits(opinionSentencesToAdd);
	}

	/**
	 * Main content generation function which takes a seed as a person, rock
	 * group, or other entity name and produce a list of text fragments by web
	 * mining for <br>
	 * 
	 * @param sentence
	 *          entity name
	 * @return List<HitBase> of text fragment structures which contain approved
	 *         (in terms of relevance) mined sentences, as well as original search
	 *         results objects such as doc titles, abstracts, and urls.
	 */

	public List<HitBase> generateContentAbout(String sentence) throws Exception {
		List<HitBase> opinionSentencesToAdd = new ArrayList<>();
		System.out.println(" \n=== Entity to write about = " + sentence);

		String[] extraKeywords = new StoryDiscourseNavigator().obtainAdditionalKeywordsForAnEntity(sentence);
		System.out.println("Found  extraKeywords "+ Arrays.asList(extraKeywords));
		if (extraKeywords==null || extraKeywords.length<1)
			extraKeywords = StoryDiscourseNavigator.frequentPerformingVerbs;

		int stepCount=0;
		for (String verbAddition : extraKeywords) {
			List<HitBase> searchResult = yrunner.runSearch(sentence + " "
					+ verbAddition, MAX_SEARCH_RESULTS); //100);
			if (MAX_SEARCH_RESULTS<searchResult.size())
				searchResult = searchResult.subList(0, MAX_SEARCH_RESULTS);
			//TODO for shorter run
			if (searchResult != null) {
				for (HitBase item : searchResult) { // got some text from .html
					if (item.getAbstractText() != null
							&& !(item.getUrl().indexOf(".pdf") > 0) && !visitedURLs.contains(item.getUrl())) { // exclude pdf
						opinionSentencesToAdd
						.add(//augmentWithMinedSentencesAndVerifyRelevance(item,
							//	sentence, null));
								buildParagraphOfGeneratedText(item,	sentence, null));
						visitedURLs.add(item.getUrl());
					}
				}
			}
			stepCount++;
			if (stepCount>MAX_STEPS)
				break;
		}
		 
		// if nothing is written, then get first search result and try again
		try {
			if (generateContentAboutIter<4 && ContentGeneratorSupport.problematicHitList(opinionSentencesToAdd)){
				List<HitBase> resultList = yrunner.runSearch(sentence, 10);
				String discoveredSimilarTopic = resultList.get(generateContentAboutIter).getTitle();
				discoveredSimilarTopic = ContentGeneratorSupport.getPortionOfTitleWithoutDelimiters(discoveredSimilarTopic);
				generateContentAboutIter++;
				opinionSentencesToAdd =  generateContentAbout(discoveredSimilarTopic);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return removeDuplicatesFromResultantHits(opinionSentencesToAdd);
	}

	/**
	 * Takes a sentence and extracts noun phrases and entity names to from search
	 * queries for finding relevant sentences on the web, which are then subject
	 * to relevance assessment by Similarity. Search queries should not be too
	 * general (irrelevant search results) or too specific (too few search
	 * results)
	 * 
	 * @param sentence
	 *          input sentence to form queries
	 * @return List<String> of search expressions
	 */
	public static List<String> buildSearchEngineQueryFromSentence(String sentence) {
		ParserChunker2MatcherProcessor pos = ParserChunker2MatcherProcessor.getInstance();

		List<ParseTreeChunk> nPhrases = pos.formGroupedPhrasesFromChunksForSentence(sentence).get(0);
		List<String> queryArrayStr = new ArrayList<>();
		for (ParseTreeChunk ch : nPhrases) {
			StringBuilder query = new StringBuilder();
			int size = ch.getLemmas().size();

			for (int i = 0; i < size; i++) {
				if (ch.getPOSs().get(i).startsWith("N")
						|| ch.getPOSs().get(i).startsWith("J")) {
					query.append(ch.getLemmas().get(i)).append(" ");
				}
			}
			query = new StringBuilder(query.toString().trim());
			int len = query.toString().split(" ").length;
			if (len < 2 || len > 5)
				continue;
			if (len < 4) { // every word should start with capital
				String[] qs = query.toString().split(" ");
				boolean bAccept = true;
				for (String w : qs) {
					if (w.toLowerCase().equals(w)) // idf only two words then
						// has to be person name,
						// title or geolocation
						bAccept = false;
				}
				if (!bAccept)
					continue;
			}

			query = new StringBuilder(query.toString().trim().replace(" ", " +"));
			query.insert(0, " +");

			queryArrayStr.add(query.toString());

		}
		if (queryArrayStr.size() < 1) { // release constraints on NP down to 2
			// keywords
			for (ParseTreeChunk ch : nPhrases) {
				StringBuilder query = new StringBuilder();
				int size = ch.getLemmas().size();

				for (int i = 0; i < size; i++) {
					if (ch.getPOSs().get(i).startsWith("N")
							|| ch.getPOSs().get(i).startsWith("J")) {
						query.append(ch.getLemmas().get(i)).append(" ");
					}
				}
				query = new StringBuilder(query.toString().trim());
				int len = query.toString().split(" ").length;
				if (len < 2)
					continue;

				query = new StringBuilder(query.toString().trim().replace(" ", " +"));
				query.insert(0, " +");

				queryArrayStr.add(query.toString());

			}
		}

		queryArrayStr = removeDuplicatesFromQueries(queryArrayStr);
		queryArrayStr.add(sentence);

		return queryArrayStr;

	}

	/**
	 * remove dupes from queries to easy cleaning dupes and repetitive search
	 * afterwards
	 * 
	 * @param hits List<String> of sentences (search queries, or search results
	 *             abstracts, or titles
	 * @return List<String> of sentences where dupes are removed
	 */
	public static List<String> removeDuplicatesFromQueries(List<String> hits) {
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double dupeThresh = 0.8; // if more similar, then considered dupes was
		// 0.7
		List<Integer> idsToRemove = new ArrayList<>();
		List<String> hitsDedup = new ArrayList<>();
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
	 * @param hits List<HitBase> of search results objects
	 * @return List<String> of search results objects where dupes are removed
	 */
	public static List<HitBase> removeDuplicatesFromResultantHits(
			List<HitBase> hits) {
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double dupeThresh = // 0.8; // if more similar, then considered dupes was
				0.7;
		try {
			for (int i = 0; i < hits.size(); i++)
				for (int j = i + 1; j < hits.size(); j++) {
					HitBase hit2 = hits.get(j);
					List<Fragment> fragmList1 = hits.get(i).getFragments();
					List<Fragment> fragmList2 = hits.get(j).getFragments();
					List<Fragment> fragmList2Results = new ArrayList<>(fragmList2);
					for (Fragment f1 : fragmList1)
						for (Fragment f2 : fragmList2) {
							String sf1 = f1.getResultText();
							String sf2 = f2.getResultText();
							if (StringUtils.isEmpty(sf1))
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

	/**
	 * Takes single search result for an entity which is the subject of the essay
	 * to be written and forms essay sentences from the title, abstract, and
	 * possibly original page
	 * 
	 * @param item The HitBase search result
	 * @param originalSentence The seed for the essay to be written
	 * @param sentsAll
	 *          : list<String> of other sentences in the seed if it is
	 *          multi-sentence
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
					pageContent = ContentGeneratorSupport.cleanSpacesInCleanedHTMLpage(pageContent);
					//pageContent = pageContent.trim().replaceAll("  [A-Z]", ". $0")// .replace("  ",
					//		// ". ")
					//		.replace("..", ".").replace(". . .", " ").trim(); // sometimes   html breaks are converted into ' ' (two spaces), so
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
			StringBuilder followSent = new StringBuilder();
			if (fragment.length() < 50)
				continue;
			String pageSentence = "";
			// try to find original sentence from webpage
			if (fragment.contains("_should_find_orig_") && sents != null && sents.length > 0){
				try { 
					// first try sorted sentences from page by length approach
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
					
					if (mainAndFollowSent!=null || mainAndFollowSent[0]!=null){
						pageSentence = mainAndFollowSent[0];
						for(int i = 1; i< mainAndFollowSent.length; i++)
							if (mainAndFollowSent[i]!=null)
								followSent.append(mainAndFollowSent[i]);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			else
				// or get original snippet
				pageSentence = fragment;
			if (pageSentence != null)
				pageSentence.replace("_should_find_orig_", "");

			// resultant sentence SHOULD NOT be longer than for times the size of
			// snippet fragment
			if (pageSentence != null && pageSentence.length()>50 )
			//		&& (float) pageSentence.length() / (float) fragment.length() < 4.0)
			{ // was 2.0,

				try { // get score from syntactic match between sentence in
					// original text and mined sentence
					double measScore, syntScore, mentalScore = 0.0;

					SentencePairMatchResult matchRes = sm.assessRelevance(pageSentence
							+ " " + title, originalSentence);
					List<List<ParseTreeChunk>> match = matchRes.getMatchResult();
					if (!matchRes.isVerbExists() || matchRes.isImperativeVerb()) {
						System.out
						.println("Rejected Sentence : No verb OR Yes imperative verb :"
								+ pageSentence);
						continue;
					}

					syntScore = parseTreeChunkListScorer
							.getParseTreeChunkListScore(match);
					System.out.println(parseTreeChunk.listToString(match) + " "
							+ syntScore + "\n pre-processed sent = '" + pageSentence);

					if (syntScore < RELEVANCE_THRESHOLD){ // 1.5) { // trying other sents
						for (String currSent : sentsAll) {
							if (currSent.startsWith(originalSentence))
								continue;
							match = sm.assessRelevance(currSent, pageSentence)
									.getMatchResult();
							double syntScoreCurr = parseTreeChunkListScorer
									.getParseTreeChunkListScore(match);
							if (syntScoreCurr > syntScore) {
								syntScore = syntScoreCurr;
							}
						}
						if (syntScore > RELEVANCE_THRESHOLD) {
							System.out.println("Got match with other sent: "
									+ parseTreeChunk.listToString(match) + " " + syntScore);
						}
					}

					measScore = stringDistanceMeasurer.measureStringDistance(
							originalSentence, pageSentence);


					if ((syntScore > RELEVANCE_THRESHOLD || measScore > 0.5)
							&& measScore < 0.8 && pageSentence.length() > 40) // >70
					{
						String pageSentenceProc = GeneratedSentenceProcessor
								.acceptableMinedSentence(pageSentence);
						if (pageSentenceProc != null) {
							pageSentenceProc = GeneratedSentenceProcessor
									.processSentence(pageSentenceProc);
							followSent = new StringBuilder(GeneratedSentenceProcessor.processSentence(followSent.toString()));
							if (followSent != null) {
								pageSentenceProc += " "+ followSent;
							}

							pageSentenceProc = Utils.convertToASCII(pageSentenceProc);
							Fragment f = new Fragment(pageSentenceProc, syntScore + measScore
									+ mentalScore + (double) pageSentenceProc.length()
									/ (double) 50);
							f.setSourceURL(item.getUrl());
							f.fragment = fragment;
							result.add(f);
							System.out.println("Accepted sentence: " + pageSentenceProc + " | "+followSent
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

	

	// given a fragment from snippet, finds an original sentence at a webpage by
	// optimizing alignment score
	public static String[] getFullOriginalSentenceFromWebpageBySnippetFragment(
			String fragment, String[] sents) {
		if (fragment.trim().length() < 15)
			return null;

		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double dist = 0.0;
		String result = null;
		StringBuilder followSent = new StringBuilder();
		for (int i = 0; i < sents.length; i++) {
			String s = sents[i];
			if (s == null || s.length() < 30)
				continue;
			double distCurr = meas.measureStringDistance(s, fragment);
			if (distCurr > dist && distCurr > 0.4) {
				result = s;
				dist = distCurr;
				try {
					if (i < sents.length - 1 && sents[i + 1].length() > 60) { 
						String f1 = GeneratedSentenceProcessor.acceptableMinedSentence(sents[i+1]);
						if (f1!=null){
							followSent = new StringBuilder(f1);
						}
					}

					if (i < sents.length - 2 && sents[i + 2].length() > 60) {
						String f2 = GeneratedSentenceProcessor.acceptableMinedSentence(sents[i+2]);
						if (f2!=null){
							followSent.append(" ").append(f2);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return new String[] { result, followSent.toString()};
	}

	// given a fragment from snippet, finds an original sentence at a webpage by
	// optimizing alignmemt score
	public static String[] getBestFullOriginalSentenceFromWebpageBySnippetFragment(
			String fragment, String[] sents) {
		if (fragment.trim().length() < 15)
			return null;
		int bestSentIndex = -1;
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double distBest = 10.0; // + sup
		String result = null, followSent = null;
		for (int i = 0; i < sents.length; i++) {
			String s = sents[i];
			if (s == null || s.length() < 30)
				continue;
			double distCurr = meas.measureStringDistance(s, fragment);
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

		downloadedPage= downloadedPage.replace("     ", "&");
		downloadedPage = downloadedPage.replaceAll("(?:&)+", "#");
		String[] sents = downloadedPage.split("#");
		List<TextChunk> sentsList = new ArrayList<>();
		for(String s: sents){
			s = ContentGeneratorSupport.cleanSpacesInCleanedHTMLpage(s);
		/*	s = s.trim().replace("  ", ". ").replace("..", ".").replace(". . .", " ")
					.replace(": ", ". ").replace("- ", ". ").
					replace (". .",".").trim(); */
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

	public static class TextChunk {
		public TextChunk(String s, int length) {
			this.text = s;
			this.len = length;
		}
		public String text;
		public int len;
	}

	public static class TextChunkComparable implements Comparator<TextChunk> {
		@Override
		public int compare(TextChunk ch1, TextChunk ch2) {
			return Integer.compare(ch1.len, ch2.len);
		}
	}

	protected String[] cleanSplitListOfSents(String[] longestSents){
		float minFragmentLength = 40, minFragmentLengthSpace=4;

		List<String> sentsClean = new ArrayList<>();
		for (String sentenceOrMultSent : longestSents)
		{
			if (sentenceOrMultSent==null || sentenceOrMultSent.length()<20)
				continue;
			if (GeneratedSentenceProcessor.acceptableMinedSentence(sentenceOrMultSent)==null){
				//System.out.println("Rejected sentence by GeneratedSentenceProcessor.acceptableMinedSentence = "+sentenceOrMultSent);
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
		return sentsClean.toArray(new String[0]);
	}

	public Triple<List<String>, String, String[]> formCandidateFragmentsForPage(HitBase item, String originalSentence, List<String> sentsAll){
		// put orig sentence in structure
		List<String> origs = new ArrayList<>();
		origs.add(originalSentence);
		item.setOriginalSentences(origs);
		String title = item.getTitle().replace("<b>", " ").replace("</b>", " ")
				.replace("  ", " ").replace("  ", " ");
		// generation results for this sentence
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
					pageContent = ContentGeneratorSupport.cleanSpacesInCleanedHTMLpage(pageContent);
					//pageContent = pageContent.trim().replaceAll("    [A-Z]", ". $0")// .replace("  ",
					//		// ". ")
					//		.replace("..", ".").replace(". . .", " ").
					//		replace(".    .",". ").trim(); // sometimes   html breaks are converted into ' ' (two spaces), so
					// we need to put '.'
					sents = sm.splitSentences(pageContent);

					sents = ContentGeneratorSupport.cleanListOfSents(sents);
				}
			}
		} catch (Exception e) {
			System.err.println("Problem downloading  the page and splitting into sentences");
			return new Triple<>(allFragms, downloadedPage, sents);
		}
		return new Triple<>(allFragms, downloadedPage, sents);
	}

	String[] formCandidateSentences(String fragment, Triple<List<String>, String, String[]> fragmentExtractionResults){
		String[] mainAndFollowSent = null;

		String downloadedPage = fragmentExtractionResults.getSecond();
		String[] sents = fragmentExtractionResults.getThird();

		if (fragment.length() < 50)
			return null;
		String pageSentence = "";
		// try to find original sentence from webpage
		if (fragment.contains("_should_find_orig_") && sents != null
				&& sents.length > 0){
			try { 
				// first try sorted sentences from page by length approach
				String[] sentsSortedByLength = extractSentencesFromPage(downloadedPage);


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
		}
		else
			// or get original snippet
			pageSentence = fragment;
		if (pageSentence != null)
			pageSentence.replace("_should_find_orig_", "");

		return mainAndFollowSent;

	}	

	private Fragment verifyCandidateSentencesAndFormParagraph(
			String[] candidateSentences, HitBase item, String fragment, String originalSentence, List<String> sentsAll) {
		Fragment result = null;	

		String pageSentence = candidateSentences[0];
		StringBuilder followSent = new StringBuilder();
		for(int i = 1; i< candidateSentences.length; i++)
			followSent.append(candidateSentences[i]);
		String title = item.getTitle();

		// resultant sentence SHOULD NOT be longer than for times the size of
		// snippet fragment
		if (!(pageSentence != null && pageSentence.length()>50) ){
			System.out.println("Cannot accept the sentence = "+ pageSentence +
					"!(pageSentence != null && pageSentence.length()>50 && (float) pageSentence.length() / (float) fragment.length() < 4.0) )");
			
			return null;
		}


		try { // get score from syntactic match between sentence in
			// original text and mined sentence
			double measScore, syntScore, mentalScore = 0.0;

			SentencePairMatchResult matchRes = sm.assessRelevance(pageSentence
					+ " " + title, originalSentence);
			List<List<ParseTreeChunk>> match = matchRes.getMatchResult();
			if (match==null || match.size()<1){
				System.out
				.println("Rejected Sentence : empty match "+ pageSentence);
				return null;
			}
			
			if (!matchRes.isVerbExists() || matchRes.isImperativeVerb()) {
				System.out
				.println("Rejected Sentence : No verb OR Yes imperative verb :"
						+ pageSentence);
				return null;
			}

			syntScore = parseTreeChunkListScorer
					.getParseTreeChunkListScore(match);
			System.out.println(parseTreeChunk.listToString(match) + " "
					+ syntScore + "\n pre-processed sent = '" + pageSentence);

			try {
				if (sentsAll!=null && syntScore < RELEVANCE_THRESHOLD){ // 1.5) { // trying other sents
					for (String currSent : sentsAll) {
						if (currSent.startsWith(originalSentence))
							continue;
						match = sm.assessRelevance(currSent, pageSentence)
								.getMatchResult();
						double syntScoreCurr = parseTreeChunkListScorer
								.getParseTreeChunkListScore(match);
						if (syntScoreCurr > syntScore) {
							syntScore = syntScoreCurr;
						}
					}
					if (syntScore > RELEVANCE_THRESHOLD) {
						System.out.println("Got match with other sent: "
								+ parseTreeChunk.listToString(match) + " " + syntScore);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			measScore = stringDistanceMeasurer.measureStringDistance(
					originalSentence, pageSentence);


			if ((syntScore > RELEVANCE_THRESHOLD || measScore > 0.5)
					&& measScore < 0.8 && pageSentence.length() > 40) // >70
			{
				String pageSentenceProc = GeneratedSentenceProcessor
						.acceptableMinedSentence(pageSentence);
				if (pageSentenceProc != null) {
					pageSentenceProc = GeneratedSentenceProcessor
							.processSentence(pageSentenceProc);
					followSent = new StringBuilder(GeneratedSentenceProcessor.processSentence(followSent.toString()));
					if (followSent != null) {
						pageSentenceProc += " "+ followSent;
					}

					pageSentenceProc = Utils.convertToASCII(pageSentenceProc);
					result = new Fragment(pageSentenceProc, syntScore + measScore
							+ mentalScore + (double) pageSentenceProc.length()
							/ (double) 50);
					result.setSourceURL(item.getUrl());
					result.fragment = fragment;

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

	return result;
}

public HitBase buildParagraphOfGeneratedText(HitBase item,
		String originalSentence, List<String> sentsAll) {
	List<Fragment> results = new ArrayList<>() ;
	
	Triple<List<String>, String, String[]> fragmentExtractionResults = formCandidateFragmentsForPage(item, originalSentence, sentsAll);

	List<String> allFragms = fragmentExtractionResults.getFirst();

	for (String fragment : allFragms) {
		String[] candidateSentences = formCandidateSentences(fragment, fragmentExtractionResults);
		if (candidateSentences == null)
			continue;
		Fragment res = verifyCandidateSentencesAndFormParagraph(candidateSentences, item, fragment, originalSentence, sentsAll);
		if (res!=null)
			results.add(res);

	}
	
	item.setFragments(results );
	return item;
}




public static void main(String[] args) {
	RelatedSentenceFinder f = new RelatedSentenceFinder();

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