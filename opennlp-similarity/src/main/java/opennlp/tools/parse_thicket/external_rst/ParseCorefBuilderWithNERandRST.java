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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import edu.arizona.sista.discourse.rstparser.DiscourseTree;
import edu.arizona.sista.processors.CorefMention;
import edu.arizona.sista.processors.Document;
import edu.arizona.sista.processors.Processor;
import edu.arizona.sista.processors.Sentence;
import edu.arizona.sista.processors.corenlp.CoreNLPProcessor;
import edu.arizona.sista.struct.DirectedGraphEdgeIterator;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import opennlp.tools.parse_thicket.ArcType;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.communicative_actions.CommunicativeActionsArcBuilder;
import scala.Option;

public class ParseCorefBuilderWithNERandRST {	
	public Processor proc = null;
	CommunicativeActionsArcBuilder caFinder = new CommunicativeActionsArcBuilder();
	private static Logger log = Logger
		      .getLogger("opennlp.tools.parse_thicket.external_rst.ParseCorefBuilderWithNERandRST");


	AbstractSequenceClassifier<CoreLabel> classifier = null;

	ParseCorefBuilderWithNERandRST() {
		super();
		classifier = CRFClassifier.getDefaultClassifier();
		proc = new CoreNLPProcessor(true, true, 100);
	}

	public ParseThicketWithDiscourseTree buildParseThicket(String text){
		List<Tree> ptTrees = new ArrayList<Tree>();
		List<WordWordInterSentenceRelationArc> arcs = new ArrayList<WordWordInterSentenceRelationArc>();
		List<List<ParseTreeNode>> nodesThicket = new ArrayList<List<ParseTreeNode>>();

		Document doc=null;
        try {
	        doc = proc.annotate(text, false);
        } catch (IllegalArgumentException iae) {
        	log.severe("failed to parse text: "+text);
        } catch (Exception e) {
	        e.printStackTrace();
        }
        // failed to parse - skip this text
		if (doc==null)
			return null;
		//java.lang.IllegalArgumentException
		for (Sentence sentence: doc.sentences()) {
			List<ParseTreeNode> sentenceNodes = new ArrayList<ParseTreeNode>();
			String[] tokens= sentence.words();
			for(int i=0; i< tokens.length; i++){
				//sentence.startOffsets(), " "));
				//sentence.endOffsets(), " "));
				ParseTreeNode p = new ParseTreeNode(sentence.words()[i], sentence.tags().get()[i]);
				p.setId(i+1);
				if(sentence.entities().isDefined()){
					p.setNe(sentence.entities().get()[i]);
				}
				if(sentence.norms().isDefined()){
					//p.setNormalizedWord(sentence.norms().get()[i]);
					p.setNormalizedWord(sentence.lemmas().get()[i]);
				}
				sentenceNodes.add(p);
			}

			if(sentence.dependencies().isDefined()) {
				int i=0;
				DirectedGraphEdgeIterator<String> iterator = new
						DirectedGraphEdgeIterator<String>(sentence.dependencies().get());
				while(iterator.hasNext()) {
					scala.Tuple3<Object, Object, String> dep = iterator.next();
					//System.out.println(" head:" + dep._1() + " modifier:" + dep._2() + " label:" + dep._3());
					if (i>sentenceNodes.size()-1)
						break;
					ParseTreeNode p = sentenceNodes.get(i);
					p.setHead(dep._1().toString());
					p.setModifier(dep._2().toString());
					p.setLabel(dep._3());
					sentenceNodes.set(i, p);
					i++;
				}
			}
			if(sentence.syntacticTree().isDefined()) {
				Tree tree = Tree.valueOf(sentence.syntacticTree().get().toString());
				ptTrees.add(tree);
				//tree.pennPrint();
			}
			nodesThicket.add(sentenceNodes);
		}

		if(doc.coreferenceChains().isDefined()) {
			// these are scala.collection Iterator and Iterable (not Java!)
			scala.collection.Iterator<scala.collection.Iterable<CorefMention>> chains = doc.coreferenceChains().get().getChains().iterator();
			while(chains.hasNext()) {
				scala.collection.Iterator<CorefMention> chain = chains.next().iterator();
				//System.out.println("Found one coreference chain containing the following mentions:");
				int numInChain = 0;
				int[] niSentence = new int[4], niWord = new int[4], startOffset = new int[4], endOffset = new int[4];

				while(chain.hasNext()) {
					CorefMention mention = chain.next();
					// note that all these offsets start at 0 too
					niSentence[numInChain ] = mention.sentenceIndex();
					niWord[numInChain ] = mention.headIndex();
					startOffset[numInChain ] = mention.startOffset();
					endOffset[numInChain ] = mention.endOffset();
					if (numInChain>=4-1)
						break;
					numInChain++;
					//" headIndex:" + mention.headIndex() +
					//" startTokenOffset:" + mention.startOffset() +
					//" endTokenOffset:" + mention.endOffset());
				}
				if (numInChain>0) { // more than a single mention
					for(int i=0; i<numInChain; i++){
						ArcType arcType = new ArcType("coref-", "", 0, 0);

						WordWordInterSentenceRelationArc arc = 
								new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(niSentence[i],niWord[i]), 
										new Pair<Integer, Integer>(niSentence[i+1],niWord[i+1]), 
									    startOffset[i]+"", startOffset[i+1]+"",
	      	    					  arcType);
						arcs.add(arc);
					}
				}
			}
		}


		List<WordWordInterSentenceRelationArc> arcsCA = buildCAarcs(nodesThicket);
		arcs.addAll(arcsCA);
		ParseThicketWithDiscourseTree result = new ParseThicketWithDiscourseTree(ptTrees, arcs);

		if(doc.discourseTree().isDefined()) {
			Option<DiscourseTree> discourseTree = doc.discourseTree();

			//scala.collection.immutable.List<DiscourseTree> scList = discourseTree.toList();
			scala.collection.Iterator<DiscourseTree> iterator = discourseTree.iterator();
			while(iterator.hasNext()) {
				DiscourseTree dt = iterator.next();
				result.setDt(dt);
				List<WordWordInterSentenceRelationArc> rstArcs = new ArrayList<WordWordInterSentenceRelationArc>();
				navigateDiscourseTree(dt, rstArcs, nodesThicket );
				arcs.addAll(rstArcs);
				System.out.println(dt);
				System.out.println("first EDU = "+dt.firstEDU() + "| dt.firstSentence() = "+ dt.firstSentence() + 
						" \n| last EDU = "+dt.lastEDU() + "| dt.lastSentence() = "+ dt.lastSentence() + 
						" \n| dt.tokenCount() = " + dt.tokenCount() + "| dt.firstToken " + dt.firstToken() + 
						" | dt.lastToken() "+ dt.lastToken() + "\n kind =" + dt.kind() + " | text = "+ dt.rawText());
				StringBuilder sb = new StringBuilder(10000);
				System.out.println(sb);
			}
		}

		result.setOrigText(text);
		result.setNodesThicket(nodesThicket);
		
		result.setDtDump(); // sets the DT representation for TK learning
		return result;
	}

	public List<WordWordInterSentenceRelationArc> buildCAarcs(
			List<List<ParseTreeNode>> nodesThicket) {
		List<WordWordInterSentenceRelationArc> arcs = new ArrayList<WordWordInterSentenceRelationArc>();

		for(int sentI=0; sentI<nodesThicket.size(); sentI++){
			for(int sentJ=sentI+1; sentJ<nodesThicket.size(); sentJ++){
				List<ParseTreeNode> sentenceI = nodesThicket.get(sentI), 
						sentenceJ = nodesThicket.get(sentJ);
				Pair<String, Integer[]> caI = caFinder.findCAInSentence(sentenceI);
				Pair<String, Integer[]> caJ = caFinder.findCAInSentence(sentenceJ);
				int indexCA1 = caFinder.findCAIndexInSentence(sentenceI);
				int indexCA2 = caFinder.findCAIndexInSentence(sentenceJ);
				if (caI==null || caJ==null)
					continue;
				Pair<String, Integer[]> caGen = caFinder.generalize(caI, caJ).get(0);

				ArcType arcType = new ArcType("ca", 
						caGen.getFirst().toString()+printNumArray(caGen.getSecond()), 0, 0);
				WordWordInterSentenceRelationArc arc = 
						new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(sentI,indexCA1), 
								new Pair<Integer, Integer>(sentJ,indexCA2), caI.getFirst(), caJ.getFirst(), 
								arcType);
				arcs.add(arc);

			}
		}

		return arcs;
	}

	private String printNumArray(Integer[] arr){
		StringBuffer buf = new StringBuffer();
		for(Integer i: arr){
			buf.append(Integer.toString(i)+ " ");
		}
		return buf.toString();
	}

	// creates a list of Arcs objects 'arcs' from the descourse tree dt, using the list of sentences 'nodesThicket' to identify words 
	// for nodes being connected with these arcs
	private void navigateDiscourseTree(DiscourseTree dt, List<WordWordInterSentenceRelationArc> arcs,  List<List<ParseTreeNode>> nodesThicket  ) {
		if (dt.isTerminal()) {
			return;
		} else {
			ArcType arcType = new ArcType("rst", 
					dt.relationLabel()+ "=>" + dt.kind(), Boolean.compare(dt.relationDirection().equals("LeftToRight"), true),0);
			String lemmaFrom = nodesThicket.get(dt.firstSentence()).get(dt.firstToken().copy$default$2()).getWord();
			String lemmaTo = nodesThicket.get(dt.lastSentence()).get(dt.lastToken().copy$default$2()-1).getWord();
			
			WordWordInterSentenceRelationArc arc = 
					new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(dt.firstToken().copy$default$1(), dt.firstToken().copy$default$2()), 
							new Pair<Integer, Integer>(dt.lastToken().copy$default$1(), dt.lastToken().copy$default$2()), 
							lemmaFrom,lemmaTo, 
							arcType);
			System.out.println(arc);
			arcs.add(arc);
			DiscourseTree[] kids = dt.children();
			if (kids != null) {
				for (DiscourseTree kid : kids) {
					navigateDiscourseTree(kid, arcs, nodesThicket);
				}
			}
			return ;
		}
	}

	public static void main(String[] args){
		ParseCorefBuilderWithNERandRST builder = new ParseCorefBuilderWithNERandRST ();
		String text = "I thought I d tell you a little about what I like to write. And I like to immerse myself in my topics. I just like to dive right in and become sort of a human guinea pig. And I see my life as a series of experiments. So , I work for Esquire magazine , and a couple of years ago I wrote an article called  My Outsourced Life ,  where I hired a team of people in Bangalore , India , to live my life for me. "
		+ "So they answered my emails. They answered my phone. ";
		
		ParseThicket pt = builder.buildParseThicket(text);
		pt = builder.buildParseThicket(
				"Dutch accident investigators say that evidence points to pro-Russian rebels as being responsible for shooting down plane. The report indicates where the missile was fired from and identifies who was in control of the territory and pins the downing of the plane on the pro-Russian rebels. "+
						"However, the Investigative Committee of the Russian Federation believes that the plane was hit by a missile from the air which was not produced in Russia. "+
						"At the same time, rebels deny that they controlled the territory from which the missile was supposedly fired."
				);
	}

}
