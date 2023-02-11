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

import opennlp.tools.parse_thicket.Triple;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

/*
 * This class does content generation by using web mining and syntactic generalization to get sentences from the web, convert and combine 
 * them in the form 
 * expected to be readable by humans and not distinguishable from genuine content by search engines
 * 
 */

public class ContentGenerator /*extends RelatedSentenceFinder*/ {
	PageFetcher pFetcher = new PageFetcher();
	ParserChunker2MatcherProcessor sm = ParserChunker2MatcherProcessor.getInstance();
	protected ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	protected ParseTreeChunk parseTreeChunk = new ParseTreeChunk();
	protected static StringDistanceMeasurer stringDistanceMeasurer = new StringDistanceMeasurer();
	protected BingQueryRunner yrunner = new BingQueryRunner();
	protected ContentGeneratorSupport support = new ContentGeneratorSupport();
	protected int MAX_STEPS = 1;
	protected int MAX_SEARCH_RESULTS = 1;
	protected float RELEVANCE_THRESHOLD = 1.1f;

	//private static final int MAX_FRAGMENT_SENTS = 10;

	public ContentGenerator(int ms, int msr, float thresh, String key) {
		this.MAX_STEPS = ms;
		this.MAX_SEARCH_RESULTS = msr;
		this.RELEVANCE_THRESHOLD=thresh;
		yrunner.setKey(key);
	}

	public ContentGenerator() {

	}
	
	public void setLang(String lang) {
		yrunner.setLang(lang);
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
	
		int stepCount=0;
		for (String verbAddition : StoryDiscourseNavigator.frequentPerformingVerbs) {
			List<HitBase> searchResult = yrunner.runSearch(sentence + " "
					+ verbAddition, MAX_SEARCH_RESULTS); //100);
			if (MAX_SEARCH_RESULTS<searchResult.size())
				searchResult = searchResult.subList(0, MAX_SEARCH_RESULTS);
			//TODO for shorter run
			if (searchResult != null) {
				for (HitBase item : searchResult) { // got some text from .html
					if (item.getAbstractText() != null
							&& !(item.getUrl().indexOf(".pdf") > 0)) { // exclude pdf
						opinionSentencesToAdd
						.add(buildParagraphOfGeneratedText(item, sentence, null));
					}
				}
			}
			stepCount++;
			if (stepCount>MAX_STEPS)
				break;
		}

		opinionSentencesToAdd = ContentGeneratorSupport.removeDuplicatesFromResultantHits(opinionSentencesToAdd);
		return opinionSentencesToAdd;
	}

	/**
	 * Takes a sentence and extracts noun phrases and entity names to from search
	 * queries for finding relevant sentences on the web, which are then subject
	 * to relevance assessment by Similarity. Search queries should not be too
	 * general (irrelevant search results) or too specific (too few search
	 * results)
	 * 
	 * @param sentence The input sentence to form queries
	 * @return List<String> of search expressions
	 */
	public static List<String> buildSearchEngineQueryFromSentence(String sentence) {
		ParserChunker2MatcherProcessor pos = ParserChunker2MatcherProcessor.getInstance();

		List<ParseTreeChunk> nPhrases = pos
				.formGroupedPhrasesFromChunksForSentence(sentence).get(0);
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

		queryArrayStr = ContentGeneratorSupport.removeDuplicatesFromQueries(queryArrayStr);
		queryArrayStr.add(sentence);

		return queryArrayStr;

	}

	private Triple<List<String>, String, String[]> formCandidateFragmentsForPage(HitBase item, String originalSentence, List<String> sentsAll){
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

	private String[] formCandidateSentences(String fragment, Triple<List<String>, String, String[]> fragmentExtractionResults){
		String[] mainAndFollowSent = null;

		String downloadedPage = fragmentExtractionResults.getSecond();
		String[] sents = fragmentExtractionResults.getThird();

		if (fragment.length() < 50)
			return null;
		String pageSentence = "";
		// try to find original sentence from webpage
		if (fragment.indexOf("_should_find_orig_") > -1 && sents != null
				&& sents.length > 0){
			try { 
				// first try sorted sentences from page by length approach
				String[] sentsSortedByLength = support.extractSentencesFromPage(downloadedPage);

				try {
					mainAndFollowSent = ContentGeneratorSupport.getFullOriginalSentenceFromWebpageBySnippetFragment(
							fragment.replace("_should_find_orig_", ""), sentsSortedByLength);
				} catch (Exception e) {
					e.printStackTrace();
				}
				// if the above gives null than try to match all sentences from snippet fragment
				if (mainAndFollowSent==null || mainAndFollowSent[0]==null){
					mainAndFollowSent = ContentGeneratorSupport.getFullOriginalSentenceFromWebpageBySnippetFragment(
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
		if (!(pageSentence != null && pageSentence.length()>50 
				&& (float) pageSentence.length() / (float) fragment.length() < 4.0) )
			return null;


		try { // get score from syntactic match between sentence in
			// original text and mined sentence
			double measScore, syntScore, mentalScore = 0.0;

			SentencePairMatchResult matchRes = sm.assessRelevance(pageSentence
					+ " " + title, originalSentence);
			List<List<ParseTreeChunk>> match = matchRes.getMatchResult();
			if (!matchRes.isVerbExists() || matchRes.isImperativeVerb()) {
				System.out.println("Rejected Sentence : No verb OR Yes imperative verb :" + pageSentence);
				return null;
			}

			syntScore = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
			System.out.println(parseTreeChunk.listToString(match) + " "
					+ syntScore + "\n pre-processed sent = '" + pageSentence);

			if (syntScore < RELEVANCE_THRESHOLD){ // 1.5) { // trying other sents
				for (String currSent : sentsAll) {
					if (currSent.startsWith(originalSentence))
						continue;
					match = sm.assessRelevance(currSent, pageSentence).getMatchResult();
					double syntScoreCurr = parseTreeChunkListScorer.getParseTreeChunkListScore(match);
					if (syntScoreCurr > syntScore) {
						syntScore = syntScoreCurr;
					}
				}
				if (syntScore > RELEVANCE_THRESHOLD) {
					System.out.println("Got match with other sent: "
							+ parseTreeChunk.listToString(match) + " " + syntScore);
				}
			}

			measScore = stringDistanceMeasurer.measureStringDistance(originalSentence, pageSentence);


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
					System.out.println("Rejected sentence due to wrong area at webpage: " + pageSentence);
			} else
				System.out.println("Rejected sentence due to low score: " + pageSentence);
			// }
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return result;
	}

	/**
	 * Takes single search result for an entity which is the subject of the essay
	 * to be written and forms essay sentences from the title, abstract, and
	 * possibly original page
	 * 
	 * @param item
	 *          item : search result
	 * @param originalSentence
	 *          : seed for the essay to be written
	 * @param sentsAll
	 *          : list<String> of other sentences in the seed if it is
	 *          multi-sentence
	 * @return search result
	 */
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
		ContentGenerator f = new ContentGenerator();

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