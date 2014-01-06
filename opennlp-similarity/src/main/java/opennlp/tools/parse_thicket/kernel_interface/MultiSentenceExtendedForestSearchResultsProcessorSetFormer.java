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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.StringUtils;


import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.apps.MultiSentenceSearchResultsProcessor;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph;
import opennlp.tools.parse_thicket.apps.WebPageContentSentenceExtractor;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.HitBaseComparable;
import opennlp.tools.similarity.apps.WebSearchEngineResultsScraper;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeChunkListScorer;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class MultiSentenceExtendedForestSearchResultsProcessorSetFormer  extends MultiSentenceKernelBasedSearchResultsProcessor{
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.MultiSentenceKernelBasedExtendedForestSearchResultsProcessor");
	protected TreeExtenderByAnotherLinkedTree treeExtender = new TreeExtenderByAnotherLinkedTree();
	
	private TreeKernelRunner tkRunner = new TreeKernelRunner();
	
	protected static final String modelFileName = "model.txt";

	private static final String trainingFileName = "training.txt";

	protected static final String unknownToBeClassified = "unknown.txt";

	private static final String classifierOutput = "classifier_output.txt";
	
	private String path;
	public void setKernelPath (String path){
		this.path=path;
	}
	
	WebPageContentSentenceExtractor extractor = new WebPageContentSentenceExtractor();
	
	private List<HitBase> formTreeForestDataSet(
			List<HitBase> hits, String query, boolean isPositive) {
		List<HitBase> newHitList = new ArrayList<HitBase>(), newHitListReRanked = new ArrayList<HitBase>();
		// form the training set from original documets. Since search results are ranked, we set the first half as positive set,
		//and the second half as negative set.
		// after re-classification, being re-ranked, the search results might end up in a different set
		List<String[]> treeBankBuffer = new ArrayList<String[]>();
		int count = 0;
		for (HitBase hit : hits) {
			count++;
			// if orig content has been already set in HIT object, ok; otherwise set it
			String searchResultText = hit.getPageContent();
			if (searchResultText ==null){
				try {
					HitBase hitWithFullSents = extractor.formTextFromOriginalPageGivenSnippet(hit);
					for(String paragraph: hitWithFullSents.getOriginalSentences()){
						List<String[]> res = formTreeKernelStructure(paragraph, count, hits,  isPositive);
						for(String[] rl : res){
							StringUtils.printToFile(new File(path+trainingFileName), rl[0]+" \n", true);
						}
						//treeBankBuffer.addAll(res);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}			
			newHitList.add(hit);
			
			
		}	
		// write the lits of samples to a file
		ProfileReaderWriter.appendReport(treeBankBuffer, path+trainingFileName, ' ');
		return newHitList;

	}
	
	protected List<String[]> formTreeKernelStructure(String searchResultText, int count, List<HitBase> hits, boolean isPositive) {
		List<String[]> treeBankBuffer = new ArrayList<String[]> ();
		try {
			// get the parses from original documents, and form the training dataset
			ParseThicket pt = matcher.buildParseThicketFromTextWithRST(searchResultText);
			List<Tree> forest = pt.getSentences();
			// if from the first half or ranked docs, then positive, otherwise negative
			String posOrNeg = null;
			if (isPositive)
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
	
	public List<HitBase> runSearchViaAPI(String query, Boolean isPositive) {
		
		try {
			List<HitBase> hits = bingSearcher.runSearch(query, 20, true);
			formTreeForestDataSet(hits, query, isPositive);

		} catch (Exception e) {
			e.printStackTrace();
			LOG.info("No search results for query '" + query);
			return null;
		}


		return null;
	}
	public static void main(String[] args){
		String query = "digital camera for my mother as a gift";
		Boolean isPositive = true;
		if (args!=null && args.length>0){
			query = args[0];
			if (args.length>1 && args[1]!=null && args[1].startsWith("neg"))
				isPositive = false;
		}
		
		MultiSentenceExtendedForestSearchResultsProcessorSetFormer proc = new MultiSentenceExtendedForestSearchResultsProcessorSetFormer();
		proc.setKernelPath("C:\\stanford-corenlp\\tree_kernel_big\\");
		proc.runSearchViaAPI(query, isPositive);
	}

}
