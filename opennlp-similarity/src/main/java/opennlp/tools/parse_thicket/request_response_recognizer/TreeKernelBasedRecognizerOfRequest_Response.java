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
package opennlp.tools.parse_thicket.request_response_recognizer;


import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.external_rst.MatcherExternalRST;
import opennlp.tools.parse_thicket.external_rst.ParseThicketWithDiscourseTree;
import opennlp.tools.parse_thicket.kernel_interface.TreeKernelBasedClassifierMultiplePara;

/*
 * This class performs TK learning based on parse thicket which includes RST relations only 
 * based on Surdeanu at al RST parser. It does sentence parsing and NLP pipeline of 
 * Surdeanu's wrapper of Stanford NLP
 */
public class TreeKernelBasedRecognizerOfRequest_Response extends TreeKernelBasedClassifierMultiplePara{

	private MatcherExternalRST matcherRST = new MatcherExternalRST();

	protected List<String> formTreeKernelStructuresMultiplePara(List<String> texts, String flag) {
		//TODO
		this.setShortRun();	
		List<String> extendedTreesDumpTotal = new ArrayList<String>();
		try {

			for(String text: texts){
				// get the parses from original documents, and form the training dataset
				try {
					System.out.print("About to build pt with external rst from "+text + "\n...");
					ParseThicket pt = matcherRST.buildParseThicketFromTextWithRST(text);
					if (pt == null)
						continue;
					System.out.print("About to build extended forest with external rst...");
					List<String> extendedTreesDump =  // use direct option (true
							buildReptresentationForDiscourseTreeAndExtensions((ParseThicketWithDiscourseTree)pt, true);
					for(String line: extendedTreesDump)
						extendedTreesDumpTotal.add(flag + " |BT| "+line + " |ET| ");
					System.out.println("DONE");
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return extendedTreesDumpTotal;
	}

	private List<String> buildReptresentationForDiscourseTreeAndExtensions(ParseThicketWithDiscourseTree pt, boolean bDirectDT){
		List<String> extendedTreesDump = new ArrayList<String>();
		if (!bDirectDT)
			// option 1: use RST relation for extended trees 
			extendedTreesDump = treeExtender.buildForestForRSTArcs(pt);
		else {
			// option 2: use DT directly
			extendedTreesDump.add(pt.getDtDump());
		    extendedTreesDump.add(pt.getDtDumpWithPOS());
		    extendedTreesDump.add(pt.getDtDumpWithEmbeddedTrees());
		    extendedTreesDump.add(pt.getDtDumpWithVerbNet());
		}		
		return extendedTreesDump;
	}
	
	public static void main(String[] args){
		VerbNetProcessor p = VerbNetProcessor.
				getInstance("/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources"); 

		TreeKernelBasedRecognizerOfRequest_Response proc = new TreeKernelBasedRecognizerOfRequest_Response();
		proc.setKernelPath("/Users/bgalitsky/Documents/relevance-based-on-parse-trees/src/test/resources/tree_kernel/");
		proc.trainClassifier(
				YahooAnswersTrainingSetCreator.origFilesDir,
				YahooAnswersTrainingSetCreator.origFilesDir.replace("/text", "/neg_text")
				);
	}

}
