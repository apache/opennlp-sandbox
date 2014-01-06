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

public class MultiSentenceKernelBasedExtendedForestSearchResultsProcessor  extends MultiSentenceKernelBasedSearchResultsProcessor{
	private static Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.MultiSentenceKernelBasedExtendedForestSearchResultsProcessor");
	protected TreeExtenderByAnotherLinkedTree treeExtender = new TreeExtenderByAnotherLinkedTree();
	
	
	

	protected List<String[]> formTreeKernelStructure(String searchResultText, int count, List<HitBase> hits) {
		List<String[]> treeBankBuffer = new ArrayList<String[]> ();
		try {
			// get the parses from original documents, and form the training dataset
			ParseThicket pt = matcher.buildParseThicketFromTextWithRST(searchResultText);
			List<String> extendedTreesDump = treeExtender.buildForestForCorefArcs(pt);
			// if from the first half or ranked docs, then positive, otherwise negative
			String posOrNeg = null;
			if (count<hits.size()/2)
				posOrNeg=" 1 ";
			else 
				posOrNeg=" -1 ";
			// form the list of training samples
			for(String t: extendedTreesDump){
				treeBankBuffer.add(new String[] {posOrNeg+" |BT| "+t+ " |ET|"});
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return treeBankBuffer;
	}

	public static void main(String[] args){
		String query = null;
		
		/*" I see no meaningful distinction between complacency or complicity in the military's latest failure to uphold their own " +
				"standards of conduct. Nor do I see a distinction between the service member who orchestrated this offense and the chain of " +
				"command that was either oblivious to or tolerant of criminal behavior";
		
		query = "I am now living abroad and have health insurance from Russia. How can I avoid penalty for not having health insurance in US";
		
		query = "ECUADOR'S PRESIDENT RAFAEL CORREA SAYS U.S. VP JOE BIDEN WANTS HIM TO REFUSE WHISTLEBLOWER EDWARD SNOWDEN'S BID FOR ASYLUM";
		query = "how to pay tax on foreign income from real estate";
		*/
		if (args!=null && args.length>0)
			query = args[0];
		
		MultiSentenceKernelBasedExtendedForestSearchResultsProcessor proc = new MultiSentenceKernelBasedExtendedForestSearchResultsProcessor();
		proc.setKernelPath("C:\\stanford-corenlp\\tree_kernel\\");
		proc.runSearchViaAPI(query);
	}

}
