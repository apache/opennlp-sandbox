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
package opennlp.tools.parse_thicket.kernel_interface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import edu.stanford.nlp.trees.Tree;


import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.apps.BingQueryRunnerMultipageSearchResults;
import opennlp.tools.parse_thicket.apps.MultiSentenceSearchResultsProcessor;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.HitBaseComparable;
import opennlp.tools.similarity.apps.WebSearchEngineResultsScraper;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class MultiSentenceKernelBasedSearchResultsProcessor  extends MultiSentenceSearchResultsProcessor{
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.MultiSentenceKernelBasedSearchResultsProcessor");

	private WebSearchEngineResultsScraper scraper = new WebSearchEngineResultsScraper();
	protected Matcher matcher = new Matcher();
	private ParseTreeChunkListScorer parseTreeChunkListScorer = new ParseTreeChunkListScorer();
	protected BingQueryRunnerMultipageSearchResults bingSearcher = new BingQueryRunnerMultipageSearchResults();
	private SnippetToParagraph snp = new SnippetToParagraph();
	private TreeKernelRunner tkRunner = new TreeKernelRunner();

	private String path;
	public void setKernelPath (String path){
		this.path=path;
	}
	protected static final String modelFileName = "model.txt";

	private static final String trainingFileName = "training.txt";

	protected static final String unknownToBeClassified = "unknown.txt";

	private static final String classifierOutput = "classifier_output.txt";


	public List<HitBase> runSearchViaAPI(String query) {
		List<HitBase> hits = null;
		try {
			List<HitBase> resultList = bingSearcher.runSearch(query);
			// now we apply our own relevance filter
			//hits = calculateMatchScoreResortHits(resultList, query);
			
			hits = resultList;
			//once we applied our re-ranking, we set highly ranked as positive set, low-rated as negative set
			//and classify all these search results again
			//training set is formed from original documents for the search results, 
			// and snippets of these search results are classified
			hits = filterOutIrrelevantHitsByTreeKernelLearning(hits, query);

		} catch (Exception e) {
			e.printStackTrace();
			LOG.info("No search results for query '" + query);
			return null;
		}


		return hits;
	}

	private List<HitBase> filterOutIrrelevantHitsByTreeKernelLearning(
			List<HitBase> hits, String query) {
		List<HitBase> newHitList = new ArrayList<HitBase>(), newHitListReRanked = new ArrayList<HitBase>();
		// form the training set from original documents. Since search results are ranked, we set the first half as positive set,
		//and the second half as negative set.
		// after re-classification, being re-ranked, the search results might end up in a different set
		List<String[]> treeBankBuffer = new ArrayList<String[]>();
		int count = 0;
		for (HitBase hit : hits) {
			count++;
			// if orig content has been already set in HIT object, ok; otherwise set it
			String searchResultText = hit.getPageContent();
			if (searchResultText ==null){
				String[] pageSentsAndSnippet = formTextForReRankingFromHit(hit);
				searchResultText = pageSentsAndSnippet[0];
				hit.setPageContent(searchResultText);
			}			
			newHitList.add(hit);
			treeBankBuffer.addAll(formTreeKernelStructure(searchResultText, count, hits));
			
		}	
		// write the lits of samples to a file
		ProfileReaderWriter.writeReport(treeBankBuffer, path+trainingFileName, ' ');
		// build the model
		tkRunner.runLearner(path, trainingFileName, modelFileName);

		// now we preparing the same answers to be classifies in/out
		treeBankBuffer = new ArrayList<String[]>();
		for (HitBase hit : newHitList) {			
			// not original docs now but instead a snippet
			String searchResultTextAbstr = hit.getAbstractText();
			String snippet = searchResultTextAbstr.replace("<b>...</b>", ". ").replace("<span class='best-phrase'>", " ").replace("<span>", " ").replace("<span>", " ")
					.replace("<b>", "").replace("</b>", "");
			snippet = snippet.replace("</B>", "").replace("<B>", "")
					.replace("<br>", "").replace("</br>", "").replace("...", ". ")
					.replace("|", " ").replace(">", " ").replace(". .", ". ");
			snippet =  hit.getTitle() + " " + snippet;
			
			ParseThicket pt = matcher.buildParseThicketFromTextWithRST(snippet);
					//hit.getPageContent());
			List<Tree> forest = pt.getSentences();
			// we consider the snippet as a single sentence to be classified
			if (forest.size()>0){
				treeBankBuffer.add(new String[] {"0 |BT| "+forest.get(0).toString()+ " |ET|"});
				newHitListReRanked .add(hit);
			}

		}	
		// form a file from the snippets to be classified
		ProfileReaderWriter.writeReport(treeBankBuffer, path+unknownToBeClassified, ' ');
		tkRunner.runClassifier(path, unknownToBeClassified, modelFileName, classifierOutput);
		// read classification results
		List<String[]> classifResults = ProfileReaderWriter.readProfiles(path+classifierOutput, ' ');
		// iterate through classification results and set them as scores for hits
		newHitList = new ArrayList<HitBase>();
		for(int i=0; i<newHitListReRanked.size() && i<classifResults.size() ; i++){
			String scoreClassif = classifResults.get(i)[0];
			float val = Float.parseFloat(scoreClassif);
			HitBase hit = newHitListReRanked.get(i);
			hit.setGenerWithQueryScore((double) val);
			newHitList.add(hit);
		}
		
		// sort by SVM classification results
		Collections.sort(newHitList, new HitBaseComparable());
		System.out.println("\n\n ============= NEW ORDER ================= ");
		for (HitBase hit : newHitList) {
			System.out.println(hit.getOriginalSentences().toString() + " => "+hit.getGenerWithQueryScore());
			System.out.println("page content = "+hit.getPageContent());
			System.out.println("title = "+hit.getAbstractText());
			System.out.println("snippet = "+hit.getAbstractText());
			System.out.println("match = "+hit.getSource());
		}
		
		return newHitList;

	}

	protected List<String[]> formTreeKernelStructure(String searchResultText, int count, List<HitBase> hits) {
		List<String[]> treeBankBuffer = new ArrayList<String[]> ();
		try {
			// get the parses from original documents, and form the training dataset
			ParseThicket pt = matcher.buildParseThicketFromTextWithRST(searchResultText);
			List<Tree> forest = pt.getSentences();
			// if from the first half or ranked docs, then positive, otherwise negative
			String posOrNeg = null;
			if (count<hits.size()/2)
				posOrNeg=" 1 ";
			else 
				posOrNeg=" -1 ";
			// form the list of training samples
			for(Tree t: forest){
				treeBankBuffer.add(new String[] {posOrNeg+" |BT| "+t.toString()+ " |ET|"});
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return treeBankBuffer;
	}

	public static void main(String[] args){
		String query = " I see no meaningful distinction between complacency or complicity in the military's latest failure to uphold their own " +
				"standards of conduct. Nor do I see a distinction between the service member who orchestrated this offense and the chain of " +
				"command that was either oblivious to or tolerant of criminal behavior";
		
		query = "I am now living abroad and have health insurance from Russia. How can I avoid penalty for not having health insurance in US";
		
		MultiSentenceKernelBasedSearchResultsProcessor proc = new MultiSentenceKernelBasedSearchResultsProcessor();
		proc.setKernelPath("C:\\stanford-corenlp\\tree_kernel\\");
		proc.runSearchViaAPI(query);
	}

}
