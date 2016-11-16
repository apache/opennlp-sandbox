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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import edu.stanford.nlp.trees.Tree;
import opennlp.tools.parse_thicket.ArcType;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.parse_thicket.matching.PT2ThicketPhraseBuilder;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;

public class ExternalRSTImporter extends PT2ThicketPhraseBuilder{
	private StringDistanceMeasurer strDistProc = new StringDistanceMeasurer ();
	private String resourceDir = null; 
	
	public ExternalRSTImporter(){
		 try {
			resourceDir = new File( "." ).getCanonicalPath()+"/src/test/resources";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public List<RstNode>  buildArrayOfRSTnodes(ParseThicket pt, String jotyDumpFileName){
		String dump=null;
		try {
			dump = FileUtils.readFileToString(new File(jotyDumpFileName), Charset.defaultCharset().toString());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		List<RstNode> nodes = new ArrayList<RstNode>(); 
		String[] lines = dump.split("\n");
		int startOfDim = StringUtils.lastIndexOf(lines[0], " ");
		String dimStr = lines[0].substring(startOfDim).replace(")", "").trim();
		int dim = Integer.parseInt(dimStr);
		Integer[][] rstArcsIndices = new Integer[dim][dim];
		for(int i=1; i< lines.length; i++){
			RstNode node = new RstNode(lines[i]);
			nodes.add(node);
		}
		return nodes;
	}

	private Map<String, Integer > phraseRstIndex = new HashMap<String, Integer >();
	private Map<Integer, List<ParseTreeNode> > rstIndexPhrase = new HashMap<Integer, List<ParseTreeNode> > ();

	public List<WordWordInterSentenceRelationArc> buildRSTArcsFromRSTparser( List<RstNode> rstNodes,
			List<WordWordInterSentenceRelationArc> arcs,
			Map<Integer, List<List<ParseTreeNode>>> sentNumPhrasesMap, 
			ParseThicket pt ) {
		List<WordWordInterSentenceRelationArc> arcsRST = new ArrayList<WordWordInterSentenceRelationArc>();		

		for(int nSentFrom=0; nSentFrom<pt.getSentences().size(); nSentFrom++){
			for(int nSentTo=nSentFrom+1; nSentTo<pt.getSentences().size(); nSentTo++){

				// label all phrases with EDU
				List<List<ParseTreeNode>> phrasesFrom = sentNumPhrasesMap.get(nSentFrom);
				for(List<ParseTreeNode> p: phrasesFrom ){
					Integer rstIndex = findBestRstNodeTextForAPhrase(p, rstNodes);
					if (rstIndex!=null){
						phraseRstIndex.put(p.toString(), rstIndex );
						rstIndexPhrase.put(rstIndex , p);
					}
				}
				List<List<ParseTreeNode>> phrasesTo = sentNumPhrasesMap.get(nSentTo);
				for(List<ParseTreeNode> p: phrasesTo ){
					Integer rstIndex = findBestRstNodeTextForAPhrase(p, rstNodes);
					if (rstIndex!=null){
						phraseRstIndex.put(p.toString(), rstIndex );
						rstIndexPhrase.put(rstIndex , p);
					}
				}
			}
		}	// for a pair of phrases, discover ^ in RST tree which connects these sentences
		
		for( int nSentFrom=0; nSentFrom<pt.getSentences().size(); nSentFrom++){
			for(int nSentTo=nSentFrom+1; nSentTo<pt.getSentences().size(); nSentTo++){
				System.out.println("Sent from # = "+nSentFrom + " -- " + "Sent to # = "+nSentTo);
				
				List<List<ParseTreeNode>> phrasesFrom = sentNumPhrasesMap.get(nSentFrom);
				List<List<ParseTreeNode>> phrasesTo = sentNumPhrasesMap.get(nSentTo);
				for(List<ParseTreeNode> vpFrom: phrasesFrom){
					for(List<ParseTreeNode> vpTo: phrasesTo){
						System.out.println("Computing arc between phrases "+ vpFrom + " => " + vpTo);
						// get two RST nodes 
						Integer rstNodeFrom = phraseRstIndex.get(vpFrom.toString());
						Integer rstNodeTo = phraseRstIndex.get(vpTo.toString());
						if (rstNodeFrom==null || rstNodeTo==null ||  rstNodeFrom >= rstNodeTo)
							continue;
						System.out.println("Finding RST path for phrases "+ vpFrom + "' and '"+vpTo);
						System.out.println("Sent from # = "+nSentFrom + " -- " + "Sent to # = "+nSentTo);

						Integer commonAncestorIndex = findCommonAncestor(rstNodeFrom , rstNodeTo, rstNodes);
						if (commonAncestorIndex!=null){
							// and figure out if they can be properly connected by an arc, by navigating RST tree
							ArcType arcType = new ArcType("rst", rstNodes.get(rstNodeTo).getRel2par(), 0, 0);
							WordWordInterSentenceRelationArc arcRST = 
									new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(nSentFrom, vpFrom.get(0).getId()), 
											new Pair<Integer, Integer>(nSentTo,  vpFrom.get(0).getId()), "", "", arcType);
							arcsRST.add(arcRST);

						}
					}
				} 
			}
		}

		return arcsRST;
	}

	private Integer findAncestorForRSTnode(Integer rstNodeFrom, List<RstNode> rstNodes){
		RstNode initNode = rstNodes.get(rstNodeFrom);
		if (initNode.level==null)
			return null;

		try {
			int initLevel = initNode.level;
			int iter=1; // start with moving one step up
			while (rstNodeFrom-iter>=0) {
				Integer currLevel= rstNodes.get(rstNodeFrom-iter).level;
				if ( currLevel!=null && currLevel< initLevel ) // found ancestor
					return rstNodeFrom-iter;
				iter++;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private Integer findCommonAncestor(Integer rstNodeFrom, Integer rstNodeTo,
			List<RstNode> rstNodes) {
		List<Integer> ancestorsFrom = new ArrayList<Integer>() , ancestorsTo = new ArrayList<Integer>();
		ancestorsFrom.add(rstNodeFrom);  ancestorsTo.add(rstNodeTo);
		int curLevel = rstNodes.get(rstNodeTo).level;
		Integer rstNodeFromCurrent = rstNodeFrom,  rstNodeToCurrent = rstNodeTo; 

		while(curLevel>0){
			if (rstNodeFromCurrent !=null) {
				rstNodeFromCurrent = findAncestorForRSTnode(rstNodeFromCurrent, rstNodes);
			}
			if (rstNodeToCurrent != null){
				rstNodeToCurrent = findAncestorForRSTnode(rstNodeToCurrent, rstNodes);
			}
			if (rstNodeFromCurrent !=null) {
				ancestorsFrom.add(rstNodeFromCurrent);  
			}
			if (rstNodeToCurrent != null)
				ancestorsTo.add(rstNodeToCurrent);

			List<Integer> ancestorsFromCurr = new ArrayList<Integer>(ancestorsFrom);
			ancestorsFromCurr.retainAll(ancestorsTo);
			if (! ancestorsFromCurr.isEmpty()){
				System.out.println("Found comm ancestor "+rstNodes.get(ancestorsFromCurr.get(0)).toString() + " id =  "+ancestorsFromCurr.get(0) + 
						" for two RST nodes | id = "+rstNodeFrom + "'"+
						rstNodes.get(rstNodeFrom).toString() + "' and | id = "+ rstNodeTo + "'"+ rstNodes.get(rstNodeTo).toString()+"'");
				String rel2par =  rstNodes.get(ancestorsFromCurr.get(0)).rel2par;
				// if common ancestor is trivial, return null and do not form a link
				if (rel2par==null) // || rel2par.equals("span"))
					return null;
				else
					return ancestorsFromCurr.get(0);
			}
			curLevel--;
		}
		return null;
	}

	private Integer findBestRstNodeTextForAPhrase(List<ParseTreeNode> ps,
			List<RstNode> rstNodes) {
		// firt get  the phrase string
		String phraseStr="";
		for(ParseTreeNode n: ps){
			phraseStr+=" "+n.getWord();
		}
		phraseStr= phraseStr.trim();
		if (phraseStr.length()<10){
			return null;
		}
		// now look for closest EDU text from the list of all 
		double rMin = -10000d; Integer index = -1;
		int count =0;
		for(RstNode r: rstNodes){
			if (r.getText()==null || r.getText().length()<10){
				count++;
				continue;
			}
			double res =  strDistProc.measureStringDistanceNoStemming(phraseStr, r.getText());
			if (res > rMin){
				rMin=res;
				index = count;
			}
			count++;
		}
		if (index==-1)
			return null;
		System.out.println("Found RST node "+ rstNodes.get(index) +" for phrase ="+phraseStr);
		return index;
	}

	/* 
	 * Building phrases takes a Parse Thicket and forms phrases for each sentence individually
	 * Then based on built phrases and obtained arcs, it builds arcs for RST
	 * Finally, based on all formed arcs, it extends phrases with thicket phrases
	 */

	public List<WordWordInterSentenceRelationArc> buildPT2ptPhrases(ParseThicket pt, String externalRSTresultFilename ) {
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
		// TODO: code to run joty suite
		List<RstNode> rstNodes = new ExternalRSTImporter().buildArrayOfRSTnodes(null, resourceDir+externalRSTresultFilename );

		// discover and add RST arcs
		List<WordWordInterSentenceRelationArc> arcsRST = buildRSTArcsFromRSTparser(  rstNodes, null, sentNumPhrases, pt );
		System.out.println(arcsRST);
		return arcsRST;

	}



}
