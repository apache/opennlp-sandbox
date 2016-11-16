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
import java.util.List;
import java.util.logging.Logger;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.parse_thicket.matching.PT2ThicketPhraseBuilder;
import edu.stanford.nlp.trees.Tree;

public class TreeExtenderByAnotherLinkedTree extends  PT2ThicketPhraseBuilder {
	private static Logger log = Logger
		      .getLogger("opennlp.tools.parse_thicket.kernel_interface.TreeExtenderByAnotherLinkedTree");

	public List<String> buildForestForCorefArcs(ParseThicket pt){
		List<String> results = new ArrayList<String>();
		for(WordWordInterSentenceRelationArc arc: pt.getArcs()){
			//if (!arc.getArcType().getType().startsWith("coref"))
			//	continue;
			int fromSent = arc.getCodeFrom().getFirst();
			int toSent = arc.getCodeTo().getFirst();
			if (fromSent <1 || toSent <1 ) // TODO problem in sentence enumeration => skip building extended trees
				return results;
			
			String wordFrom = arc.getLemmaFrom();
			String wordTo = arc.getLemmaTo();

			List<Tree> trees = getASubtreeWithRootAsNodeForWord1(pt.getSentences().get(fromSent-1), 
					pt.getSentences().get(fromSent-1), new String[]{ wordFrom});
			if (trees==null || trees.size()<1)
				continue;
			System.out.println(trees);
			StringBuilder sb = new StringBuilder(10000);	
			toStringBuilderExtenderByAnotherLinkedTree1(sb, pt.getSentences().get(toSent-1), trees.get(0), new String[]{wordTo});
			System.out.println(sb.toString());
			results.add(sb.toString());
		}
		// if no arcs then orig sentences
		if (results.isEmpty()){
			for(Tree t: pt.getSentences()){
				results.add(t.toString());
			}
		}
		return results;
	}
	// sentences in pt are enumerarted starting from 0;
	//this func works with Sista version of Stanford NLP and sentences are coded from 0
	public List<String> buildForestForRSTArcs(ParseThicket pt){
		List<String> results = new ArrayList<String>();
		for(WordWordInterSentenceRelationArc arc: pt.getArcs()){
			// TODO - uncomment
			//if (!arc.getArcType().getType().startsWith("rst"))
			//   continue;
			int fromSent = arc.getCodeFrom().getFirst();
			int toSent = arc.getCodeTo().getFirst();
			
			String wordFrom = arc.getLemmaFrom();
			String wordTo = arc.getLemmaTo();
			
			if (wordFrom == null || wordFrom.length()<1 || wordTo == null || wordTo.length()<1) 
				log.severe("Empty lemmas for RST arc "+ arc);

			List<Tree> trees = getASubtreeWithRootAsNodeForWord1(pt.getSentences().get(fromSent), 
					pt.getSentences().get(fromSent), new String[]{ wordFrom});
			if (trees==null || trees.size()<1)
				continue;
			System.out.println(trees);
			StringBuilder sb = new StringBuilder(10000);	
			Tree tree = trees.get(0);
			// instead of phrase type for the root of the tree, we want to put the RST relation name
			if (arc.getArcType().getType().startsWith("rst"))
				tree.setValue(arc.getArcType().getSubtype());
			
			toStringBuilderExtenderByAnotherLinkedTree1(sb, pt.getSentences().get(toSent), tree, new String[]{wordTo});
			System.out.println(sb.toString());
			results.add(sb.toString());
		}
		// if no arcs then orig sentences
		if (results.isEmpty()){
			for(Tree t: pt.getSentences()){
				results.add(t.toString());
			}
		}
		return results;
	}

	public StringBuilder toStringBuilderExtenderByAnotherLinkedTree1(StringBuilder sb, Tree t, Tree treeToInsert, String[] corefWords) {
		if (t.isLeaf()) {
			if (t.label() != null) {
				sb.append(t.label().value());
			}
			return sb;
		} else {
			sb.append('(');
			if (t.label() != null) {
				if (t.value() != null) {
					sb.append(t.label().value());
				}
			}
			boolean bInsertNow=false;
			Tree[] kids = t.children();
			if (kids != null) {
				for (Tree kid : kids) {
					if (corefWords!=null){
						String word = corefWords[corefWords.length-1];
						String phraseStr = kid.toString();
						phraseStr=phraseStr.replace(")", "");
						if (phraseStr.endsWith(word)){
							bInsertNow=true;
						}
					}
				}
				if (bInsertNow){ 
					for (Tree kid : kids) {
						sb.append(' ');
						toStringBuilderExtenderByAnotherLinkedTree1(sb, kid, null, null);
					}
					sb.append(' ');
					toStringBuilderExtenderByAnotherLinkedTree1(sb, treeToInsert, null, null);
				} else {
					for (Tree kid : kids) {
						sb.append(' ');
						toStringBuilderExtenderByAnotherLinkedTree1(sb, kid, treeToInsert, corefWords);
					}

				}
			}

			return sb.append(')');
		}
	}

	// given a parse tree and a 
	public List<Tree> getASubtreeWithRootAsNodeForWord1(Tree tree, Tree currentSubTree, String[] corefWords){
		if (currentSubTree.isLeaf()){
			return null;
		}
		List<Tree> result = null;
		Tree[] kids = currentSubTree.children();
		if (kids != null) {
			boolean bFound=false;
			String word = corefWords[corefWords.length-1];
			for (Tree kid : kids) {
				if (bFound){
					result.add(kid);
				} else {
					String phraseStr = kid.toString();
					phraseStr=phraseStr.replace(")", "");
					if (phraseStr.endsWith(word)){ // found 
						bFound=true;
						result = new ArrayList<Tree>();
					}
				}
			}
			if (bFound){
				return result;
			}
			// if not a selected node, proceed with iteration
			for (Tree kid : kids) {
				List<Tree> ts = getASubtreeWithRootAsNodeForWord1(tree, kid, corefWords);
				if (ts!=null)
					return ts;
			}

		}
		return null;
	}

	// now obsolete
	public Tree[] getASubtreeWithRootAsNodeForWord(Tree tree, Tree currentSubTree, String[] corefWords){
		if (currentSubTree.isLeaf()){
			return null;
		}


		boolean bInsertNow=false;
		/*List<ParseTreeNode> bigTreeNodes = parsePhrase(currentSubTree.label().value());	
		for(ParseTreeNode smallNode: bigTreeNodes ){
			if (bigTreeNodes.get(0).getWord().equals("") )
				continue;
			String word = bigTreeNodes.get(0).getWord();
			for(String cWord: corefWords){

				if (word.equalsIgnoreCase(cWord))
					bInsertNow=true;
			}
		} */

		String nodePhraseStr = currentSubTree.toString();
		System.out.println(nodePhraseStr);
		for(String w: corefWords)
			nodePhraseStr = nodePhraseStr.replace(w, "");
		// all words are covered
		if (nodePhraseStr.toUpperCase().equals(nodePhraseStr))
			bInsertNow=true;

		//if(bInsertNow)
		//	return currentSubTree;

		Tree[] kids = currentSubTree.children();
		if (kids != null) {
			/*for (Tree kid : kids) {
				List<ParseTreeNode> bigTreeNodes = parsePhrase(kid.label().value());	
				if (bigTreeNodes!=null && bigTreeNodes.size()>0 && bigTreeNodes.get(0)!=null &&
						bigTreeNodes.get(0).getWord().equalsIgnoreCase(corefWords[0])){
					bInsertNow=true;
					return kids;
				}

			}*/


			for (Tree kid : kids) {
				Tree[] t = getASubtreeWithRootAsNodeForWord(tree, kid, corefWords);
				if (t!=null)
					return t;
			}

		}
		return null;
	}


	public StringBuilder toStringBuilderExtenderByAnotherLinkedTree(StringBuilder sb, Tree t, Tree treeToInsert) {
		if (t.isLeaf()) {
			if (t.label() != null) {
				sb.append(t.label().value());
			}
			return sb;
		} else {
			sb.append('(');
			if (t.label() != null) {
				if (t.value() != null) {
					sb.append(t.label().value());
				}
			}

			boolean bInsertNow=false;
			// we try match trees to find out if we are at the insertion position
			if (treeToInsert!=null){
				List<ParseTreeNode> bigTreeNodes = parsePhrase(t.label().value());	
				List<ParseTreeNode> smallTreeNodes = parsePhrase(treeToInsert.getChild(0).getChild(0).getChild(0).label().value());	

				System.out.println(t + " \n "+ treeToInsert+ "\n");

				if (smallTreeNodes.size()>0 && bigTreeNodes.size()>0)
					for(ParseTreeNode smallNode: smallTreeNodes ){
						if (!bigTreeNodes.get(0).getWord().equals("") 
								&& bigTreeNodes.get(0).getWord().equalsIgnoreCase(smallNode.getWord()))
							bInsertNow=true;
					}
			}

			if (bInsertNow){ 
				Tree[] kids = t.children();
				if (kids != null) {
					for (Tree kid : kids) {
						sb.append(' ');
						toStringBuilderExtenderByAnotherLinkedTree(sb, kid, null);
					}
					sb.append(' ');
					toStringBuilderExtenderByAnotherLinkedTree(sb, treeToInsert.getChild(0).getChild(1), null);
					int z=0; z++;
				}
			} else {
				Tree[] kids = t.children();
				if (kids != null) {
					for (Tree kid : kids) {
						sb.append(' ');
						toStringBuilderExtenderByAnotherLinkedTree(sb, kid, treeToInsert);
					}

				}
			}
			return sb.append(')');
		}
	}

	public StringBuilder toStringBuilder(StringBuilder sb, Tree t) {
		if (t.isLeaf()) {
			if (t.label() != null) {
				sb.append(t.label().value());
			}
			return sb;
		} else {
			sb.append('(');
			if (t.label() != null) {
				if (t.value() != null) {
					sb.append(t.label().value());
				}
			}
			Tree[] kids = t.children();
			if (kids != null) {
				for (Tree kid : kids) {
					sb.append(' ');
					toStringBuilder(sb, kid);
				}
			}
			return sb.append(')');
		}
	}

	public static void main(String[] args){
		VerbNetProcessor p = VerbNetProcessor.
				getInstance("/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources"); 
				
		Matcher matcher = new Matcher();
		TreeExtenderByAnotherLinkedTree extender = new TreeExtenderByAnotherLinkedTree();
		
		ParseThicket pt = matcher.buildParseThicketFromTextWithRST(//"I went to the forest to look for a tree. I found out that it was thick and green");
				"Iran refuses to accept the UN proposal to end its dispute over its work on nuclear weapons. "+
				"UN nuclear watchdog passes a resolution condemning Iran for developing its second uranium enrichment site in secret. " +
				"A recent IAEA report presented diagrams that suggested Iran was secretly working on nuclear weapons. " +
				"Iran envoy says its nuclear development is for peaceful purpose, and the material evidence against it has been fabricated by the US. ");

		List<String> results = extender.buildForestForCorefArcs(pt);
		System.out.println(results);
		//System.exit(0);

		List<Tree> forest = pt.getSentences();
		
		List<Tree> trees = extender.getASubtreeWithRootAsNodeForWord1(forest.get(1), forest.get(1), new String[]{"its"});
		System.out.println(trees);
		StringBuilder sb = new StringBuilder(10000);	
		extender.toStringBuilderExtenderByAnotherLinkedTree1(sb, forest.get(0), trees.get(0), new String[]{"the", "forest"});
		System.out.println(sb.toString());


		//
		//extender.toStringBuilderExtenderByAnotherLinkedTree(sb, forest.get(0), forest.get(1));
		//System.out.println(sb.toString());
	}
}
