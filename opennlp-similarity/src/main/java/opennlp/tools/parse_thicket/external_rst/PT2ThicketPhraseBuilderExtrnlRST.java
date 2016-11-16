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
package opennlp.tools.parse_thicket.external_rst;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.kernel_interface.TreeKernelRunner;
import opennlp.tools.parse_thicket.matching.PT2ThicketPhraseBuilder;
import opennlp.tools.parse_thicket.rhetoric_structure.RhetoricStructureArcsBuilder;

import org.apache.commons.io.FileUtils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;










import edu.stanford.nlp.trees.Tree;

public class PT2ThicketPhraseBuilderExtrnlRST extends PT2ThicketPhraseBuilder{

	private RhetoricStructureArcsBuilder rstBuilder = new RhetoricStructureArcsBuilder();
	private ExternalRSTImporter externalRstBuilder = new ExternalRSTImporter();
	private static Logger log = Logger
		      .getLogger("opennlp.tools.parse_thicket.external_rst.PT2ThicketPhraseBuilderExtrnlRST");


	/*
	 * Building phrases takes a Parse Thicket and forms phrases for each sentence individually
	 * Then based on built phrases and obtained arcs, it builds arcs for RST
	 * Finally, based on all formed arcs, it extends phrases with thicket phrases
	 */

	public List<List<ParseTreeNode>> buildPT2ptPhrases(ParseThicket pt, String text, String externRSTpath) {
		List<List<ParseTreeNode>> phrasesAllSent = new ArrayList<List<ParseTreeNode>> ();
		Map<Integer, List<List<ParseTreeNode>>> sentNumPhrases = new HashMap<Integer, List<List<ParseTreeNode>>>();
		// build regular phrases
		for(int nSent=0; nSent<pt.getSentences().size(); nSent++){
			List<ParseTreeNode> sentence = pt.getNodesThicket().get(nSent);
			Tree ptree = pt.getSentences().get(nSent);
			//ptree.pennPrint();
			List<List<ParseTreeNode>> phrases = buildPT2ptPhrasesForASentence(ptree, sentence);
			System.out.println(phrases);
			phrasesAllSent.addAll(phrases);
			sentNumPhrases.put(nSent, phrases);

		}
		String filename = "default.txt";
		
		try {
			filename = text.split("/n")[0]+".txt";
			FileUtils.writeStringToFile(new File(externRSTpath+"/"+filename), text, "utf-8");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// discover and add RST arcs
		List<WordWordInterSentenceRelationArc> arcsRST =
				rstBuilder.buildRSTArcsFromMarkersAndCorefs(pt.getArcs(), sentNumPhrases, pt);
		String[] commandLine1 = new String[]{"python", "Discourse_Segmenter.py",  filename}, commandLine2=
				new String[]{"python", "Discourse_Parser.py", "tmp.edu"};
		new TreeKernelRunner().runEXE(commandLine1, externRSTpath);
		new TreeKernelRunner().runEXE(commandLine2, externRSTpath);
		
		// TODO: code to run joty suite
		List<RstNode> rstNodes = new ExternalRSTImporter().buildArrayOfRSTnodes(null, externRSTpath+"/tmp_doc.dis");
	
		// discover and add RST arcs
		List<WordWordInterSentenceRelationArc> arcsRSTexternal = externalRstBuilder.buildRSTArcsFromRSTparser(  rstNodes, null, sentNumPhrases, pt );
		System.out.println(arcsRST);


		List<WordWordInterSentenceRelationArc> arcs = pt.getArcs();
		arcs.addAll(arcsRST);
		arcs.addAll(arcsRSTexternal);
		pt.setArcs(arcs);


		List<List<ParseTreeNode>> expandedPhrases = expandTowardsThicketPhrases(phrasesAllSent, pt.getArcs(), sentNumPhrases, pt);
		return expandedPhrases;
	}

	

}
