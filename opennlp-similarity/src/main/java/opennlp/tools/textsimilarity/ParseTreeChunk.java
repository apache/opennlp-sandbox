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

package opennlp.tools.textsimilarity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;

import opennlp.tools.parse_thicket.ParseTreeNode;

public class ParseTreeChunk implements Serializable{
	private String mainPOS;

	private List<String> lemmas;

	private List<String> POSs;

	private int startPos;

	private int endPos;

	private int size;

	private ParseTreeMatcher parseTreeMatcher;

	private LemmaFormManager lemmaFormManager;

	private GeneralizationListReducer generalizationListReducer;

	private List<ParseTreeNode> parseTreeNodes;


	public List<ParseTreeNode> getParseTreeNodes() {
		return parseTreeNodes;
	}

	public void setParseTreeNodes(List<ParseTreeNode> parseTreeNodes) {
		this.parseTreeNodes = parseTreeNodes;
	}

	public ParseTreeChunk(){};
	// "[<1>NP'Property':NN, <2>NP'has':VBZ, <3>NP'lots':NNS, <4>NP'of':IN, <5>NP'trash':NN, <6>NP'and':CC, <7>NP'debris':NN]";

	public ParseTreeChunk(String phrStr){
		String[] parts = phrStr.replace("]","").split(", <");
		this.POSs = new ArrayList<String>();
		this.lemmas = new ArrayList<String>();
		this.mainPOS = StringUtils.substringBetween(phrStr, ">", "'");
		for(String part: parts){
			String lemma = StringUtils.substringBetween(part, "P'", "':");
			String pos = part.substring(part.indexOf(":")+1, part.length());
			
			if (pos==null || lemma ==null){
				continue;
			}
			this.POSs.add(pos.trim());
			this.lemmas.add(lemma.trim());
		}
		
	}
	
	public ParseTreeChunk(List<String> lemmas, List<String> POSs, int startPos,
			int endPos) {
		this.lemmas = lemmas;
		this.POSs = POSs;
		this.startPos = startPos;
		this.endPos = endPos;

		// phraseType.put(0, "np");
	}

	// constructor which takes lemmas and POS as lists so that phrases can be
	// conveniently specified.
	// usage: stand-alone runs
	public ParseTreeChunk(String mPOS, String[] lemmas, String[] POSss) {
		this.mainPOS = mPOS;
		this.lemmas = new ArrayList<String>();
		for (String l : lemmas) {
			this.lemmas.add(l);
		}
		this.POSs = new ArrayList<String>();
		for (String p : POSss) {
			this.POSs.add(p);
		}
	}

	// constructor which takes lemmas and POS as lists so that phrases can be
	// conveniently specified.
	// usage: stand-alone runs
	public ParseTreeChunk(String mPOS, List<String> lemmas, List<String> POSss) {
		this.mainPOS = mPOS;
		this.lemmas = lemmas;
		this.POSs = POSss;
	}


	public int getStartPos() {
		return startPos;
	}

	public void setStartPos(int startPos) {
		this.startPos = startPos;
	}

	public int getEndPos() {
		return endPos;
	}

	public void setEndPos(int endPos) {
		this.endPos = endPos;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public LemmaFormManager getLemmaFormManager() {
		return lemmaFormManager;
	}

	public void setLemmaFormManager(LemmaFormManager lemmaFormManager) {
		this.lemmaFormManager = lemmaFormManager;
	}

	public GeneralizationListReducer getGeneralizationListReducer() {
		return generalizationListReducer;
	}

	public void setGeneralizationListReducer(
			GeneralizationListReducer generalizationListReducer) {
		this.generalizationListReducer = generalizationListReducer;
	}

	public void setParseTreeMatcher(ParseTreeMatcher parseTreeMatcher) {
		this.parseTreeMatcher = parseTreeMatcher;
	}

	public  ParseTreeChunk(List<ParseTreeNode> ps) {
		this.lemmas = new ArrayList<String>();
		this.POSs = new ArrayList<String>();
		for(ParseTreeNode n: ps){
			this.lemmas.add(n.getWord());
			this.POSs.add(n.getPos());
		}

		if (ps.size()>0){
			this.setMainPOS(ps.get(0).getPhraseType());
			this.parseTreeNodes = ps;
		}
	}

	public List<ParseTreeChunk> buildChunks(List<LemmaPair> parseResults) {
		List<ParseTreeChunk> chunksResults = new ArrayList<ParseTreeChunk>();
		for (LemmaPair chunk : parseResults) {
			String[] lemmasAr = chunk.getLemma().split(" ");
			List<String> poss = new ArrayList<String>(), lems = new ArrayList<String>();
			for (String lem : lemmasAr) {
				lems.add(lem);
				// now looking for POSs for individual word
				for (LemmaPair chunkCur : parseResults) {
					if (chunkCur.getLemma().equals(lem)
							&&
							// check that this is a proper word in proper position
							chunkCur.getEndPos() <= chunk.getEndPos()
							&& chunkCur.getStartPos() >= chunk.getStartPos()) {
						poss.add(chunkCur.getPOS());
						break;
					}
				}
			}
			if (lems.size() != poss.size()) {
				System.err.println("lems.size()!= poss.size()");
			}
			if (lems.size() < 2) { // single word phrase, nothing to match
				continue;
			}
			ParseTreeChunk ch = new ParseTreeChunk(lems, poss, chunk.getStartPos(),
					chunk.getEndPos());
			ch.setMainPOS(chunk.getPOS());
			chunksResults.add(ch);
		}
		return chunksResults;
	}

	public List<List<ParseTreeChunk>> matchTwoSentencesGivenPairLists(
			List<LemmaPair> sent1Pairs, List<LemmaPair> sent2Pairs) {

		List<ParseTreeChunk> chunk1List = buildChunks(sent1Pairs);
		List<ParseTreeChunk> chunk2List = buildChunks(sent2Pairs);

		List<List<ParseTreeChunk>> sent1GrpLst = groupChunksAsParses(chunk1List);
		List<List<ParseTreeChunk>> sent2GrpLst = groupChunksAsParses(chunk2List);

		System.out.println("=== Grouped chunks 1 " + sent1GrpLst);
		System.out.println("=== Grouped chunks 2 " + sent2GrpLst);

		return matchTwoSentencesGroupedChunks(sent1GrpLst, sent2GrpLst);
	}

	// groups noun phrases, verb phrases, propos phrases etc. for separate match

	public List<List<ParseTreeChunk>> groupChunksAsParses(
			List<ParseTreeChunk> parseResults) {
		List<ParseTreeChunk> np = new ArrayList<ParseTreeChunk>(), vp = new ArrayList<ParseTreeChunk>(), prp = new ArrayList<ParseTreeChunk>(), sbarp = new ArrayList<ParseTreeChunk>(), pp = new ArrayList<ParseTreeChunk>(), adjp = new ArrayList<ParseTreeChunk>(), whadvp = new ArrayList<ParseTreeChunk>(), restOfPhrasesTypes = new ArrayList<ParseTreeChunk>();
		List<List<ParseTreeChunk>> results = new ArrayList<List<ParseTreeChunk>>();
		for (ParseTreeChunk ch : parseResults) {
			String mainPos = ch.getMainPOS().toLowerCase();

			if (mainPos.equals("s")) {
				continue;
			}
			if (mainPos.equals("np")) {
				np.add(ch);
			} else if (mainPos.equals("vp")) {
				vp.add(ch);
			} else if (mainPos.equals("prp")) {
				prp.add(ch);
			} else if (mainPos.equals("pp")) {
				pp.add(ch);
			} else if (mainPos.equals("adjp")) {
				adjp.add(ch);
			} else if (mainPos.equals("whadvp")) {
				whadvp.add(ch);
			} else if (mainPos.equals("sbar")) {
				sbarp.add(ch);
			} else {
				restOfPhrasesTypes.add(ch);
			}

		}
		results.add(np);
		results.add(vp);
		results.add(prp);
		results.add(pp);
		results.add(adjp);
		results.add(whadvp);
		results.add(restOfPhrasesTypes);

		return results;

	}

	// main function to generalize two expressions grouped by phrase types
	// returns a list of generalizations for each phrase type with filtered
	// sub-expressions
	public List<List<ParseTreeChunk>> matchTwoSentencesGroupedChunks(
			List<List<ParseTreeChunk>> sent1, List<List<ParseTreeChunk>> sent2) {
		List<List<ParseTreeChunk>> results = new ArrayList<List<ParseTreeChunk>>();
		// first irerate through component
		for (int comp = 0; comp < 2 && // just np & vp
				comp < sent1.size() && comp < sent2.size(); comp++) {
			List<ParseTreeChunk> resultComps = new ArrayList<ParseTreeChunk>();
			// then iterate through each phrase in each component
			for (ParseTreeChunk ch1 : sent1.get(comp)) {
				for (ParseTreeChunk ch2 : sent2.get(comp)) { // simpler version
					ParseTreeChunk chunkToAdd = parseTreeMatcher
							.generalizeTwoGroupedPhrasesRandomSelectHighestScoreWithTransforms(
									ch1, ch2);

					if (!lemmaFormManager.mustOccurVerifier(ch1, ch2, chunkToAdd)) {
						continue; // if the words which have to stay do not stay, proceed to
						// other elements
					}
					Boolean alreadyThere = false;
					for (ParseTreeChunk chunk : resultComps) {
						if (chunk.equalsTo(chunkToAdd)) {
							alreadyThere = true;
							break;
						}

						if (parseTreeMatcher
								.generalizeTwoGroupedPhrasesRandomSelectHighestScore(chunk,
										chunkToAdd).equalsTo(chunkToAdd)) {
							alreadyThere = true;
							break;
						}
					}

					if (!alreadyThere) {
						resultComps.add(chunkToAdd);
					}

					List<ParseTreeChunk> resultCompsReduced = generalizationListReducer
							.applyFilteringBySubsumption(resultComps);
					// if (resultCompsReduced.size() != resultComps.size())
						// System.out.println("reduction of gen list occurred");
				}
			}
			results.add(resultComps);
		}

		return results;
	}

/*	public Boolean equals(ParseTreeChunk ch) {
		List<String> lems = ch.getLemmas();
		List<String> poss = ch.POSs;

		if (this.lemmas.size() <= lems.size())
			return false; // sub-chunk should be shorter than chunk

		for (int i = 0; i < lems.size() && i < this.lemmas.size(); i++) {
			if (!(this.lemmas.get(i).equals(lems.get(i)) && this.POSs.get(i).equals(
					poss.get(i))))
				return false;
		}
		return true;
	}
*/
	// 'this' is super - chunk of ch, ch is sub-chunk of 'this'
	public Boolean isASubChunk_OLD(ParseTreeChunk ch) {
		List<String> lems = ch.getLemmas();
		List<String> poss = ch.POSs;

		if (this.lemmas.size() < lems.size())
			return false; // sub-chunk should be shorter than chunk

		for (int i = 0; i < lems.size() && i < this.lemmas.size(); i++) {
			if (!(this.lemmas.get(i).equals(lems.get(i)) && this.POSs.get(i).equals(
					poss.get(i))))
				return false;
		}
		return true;
	}
	
	// this => value   ch => *
	public Boolean isASubChunk(ParseTreeChunk ch) {
		List<String> lems = ch.getLemmas();
		List<String> poss = ch.POSs;

		if (this.lemmas.size() < lems.size())
			return false; // sub-chunk should be shorter than chunk

		Boolean notSubChunkWithGivenAlignment = false, unComparable = false;
		
		for (int i = 0; i < lems.size() && i < this.lemmas.size(); i++) {
			// both lemma and pos are different
			if (!this.POSs.get(i).equals(poss.get(i)) && !this.lemmas.get(i).equals(lems.get(i)) ){
				unComparable = true;
				break;
			}
			
			// this => *  ch=> run
			if (!this.lemmas.get(i).equals(lems.get(i)) && this.lemmas.get(i).equals("*")) 
				notSubChunkWithGivenAlignment = true;
		}
		if (!notSubChunkWithGivenAlignment && !unComparable)
			return true;
		
		List<String> thisPOS = new ArrayList<String> ( this.POSs);	
		Collections.reverse(thisPOS);
		List<String> chPOS = new ArrayList<String> ( poss);	
		Collections.reverse(chPOS);
		List<String> thisLemma = new ArrayList<String> ( this.lemmas);	
		Collections.reverse(thisLemma );
		List<String> chLemma = new ArrayList<String> ( lems);	
		Collections.reverse(chLemma);
		
		notSubChunkWithGivenAlignment = false; unComparable = false;
		for (int i = lems.size()-1 ; i>=0; i--) {
			// both lemma and pos are different
			if (!thisPOS.get(i).equals(chPOS.get(i)) && !thisLemma.get(i).equals(chLemma.get(i)) ){
				unComparable = true;
				break;
			}
			
			// this => *  ch=> run
			if (!thisLemma.get(i).equals(chLemma.get(i)) && thisLemma.get(i).equals("*")) 
				notSubChunkWithGivenAlignment = true;
		}
		
		if (!notSubChunkWithGivenAlignment && !unComparable)
			return true;
		else
			return false; // then ch is redundant and needs to be removed
	}

	public Boolean equalsTo(ParseTreeChunk ch) {
		List<String> lems = ch.getLemmas();
		List<String> poss = ch.POSs;
		if (this.lemmas.size() != lems.size() || this.POSs.size() != poss.size())
			return false;

		for (int i = 0; i < lems.size(); i++) {
			if (!(this.lemmas.get(i).equals(lems.get(i)) && this.POSs.get(i).equals(
					poss.get(i))))
				return false;
		}

		return true;
	}
	
	public boolean equals(ParseTreeChunk ch) {
		List<String> lems = ch.getLemmas();
		List<String> poss = ch.POSs;
		return ListUtils.isEqualList(ch.getLemmas(), this.lemmas) && ListUtils.isEqualList(ch.getPOSs(), this.POSs);
	}

	public String toString() {
		String buf = " [";
		if (mainPOS != null)
			buf = mainPOS + " [";
		for (int i = 0; i < lemmas.size() && i < POSs.size() ; i++) {
			buf += POSs.get(i) + "-" + lemmas.get(i) + " ";
			if (this.parseTreeNodes!=null){
				Map<String, Object> attrs = this.parseTreeNodes.get(i).getAttributes();
				if (attrs!=null && attrs.keySet().size()>0){
					buf += attrs+ " ";
				}
				String ner =this.parseTreeNodes.get(i).getNe();
				if (ner!=null && ner.length()>1)
					buf+="("+ner+ ") ";
			}
		}
		return buf + "]";
	}
	
	public String toWordOnlyString(){
		String buf = "";

		for (int i = 0; i < lemmas.size()  ; i++) {
			buf+=lemmas.get(i)+" ";
		}
		return buf.trim();
	}

	public int compareTo(ParseTreeChunk o) {
		if (this.size > o.size)
			return -1;
		else
			return 1;

	}

	public String listToString(List<List<ParseTreeChunk>> chunks) {
		StringBuffer buf = new StringBuffer();
		if (chunks.get(0).size() > 0) {
			buf.append(" np " + chunks.get(0).toString());
		}
		if (chunks.get(1).size() > 0) {
			buf.append(" vp " + chunks.get(1).toString());
		}
		if (chunks.size() < 3) {
			return buf.toString();
		}
		if (chunks.get(2).size() > 0) {
			buf.append(" prp " + chunks.get(2).toString());
		}
		if (chunks.get(3).size() > 0) {
			buf.append(" pp " + chunks.get(3).toString());
		}
		if (chunks.get(4).size() > 0) {
			buf.append(" adjp " + chunks.get(4).toString());
		}
		if (chunks.get(5).size() > 0) {
			buf.append(" whadvp " + chunks.get(5).toString());
		}
		/*
		 * if (mainPos.equals("np")) np.add(ch); else if (mainPos.equals( "vp"))
		 * vp.add(ch); else if (mainPos.equals( "prp")) prp.add(ch); else if
		 * (mainPos.equals( "pp")) pp.add(ch); else if (mainPos.equals( "adjp"))
		 * adjp.add(ch); else if (mainPos.equals( "whadvp")) whadvp.add(ch);
		 */
		return buf.toString();
	}

	public List<List<ParseTreeChunk>> obtainParseTreeChunkListByParsingList(
			String toParse) {
		List<List<ParseTreeChunk>> results = new ArrayList<List<ParseTreeChunk>>();
		// if (toParse.endsWith("]]]")){
		// toParse = toParse.replace("[[","").replace("]]","");
		// }
		toParse = toParse.replace(" ]], [ [", "&");
		String[] phraseTypeFragments = toParse.trim().split("&");
		for (String toParseFragm : phraseTypeFragments) {
			toParseFragm = toParseFragm.replace("],  [", "#");

			List<ParseTreeChunk> resultsPhraseType = new ArrayList<ParseTreeChunk>();
			String[] indivChunks = toParseFragm.trim().split("#");
			for (String expr : indivChunks) {
				List<String> lems = new ArrayList<String>(), poss = new ArrayList<String>();
				expr = expr.replace("[", "").replace(" ]", "");
				String[] pairs = expr.trim().split(" ");
				for (String word : pairs) {
					word = word.replace("]]", "").replace("]", "");
					String[] pos_lem = word.split("-");
					lems.add(pos_lem[1].trim());
					poss.add(pos_lem[0].trim());
				}
				ParseTreeChunk ch = new ParseTreeChunk();
				ch.setLemmas(lems);
				ch.setPOSs(poss);
				resultsPhraseType.add(ch);
			}
			results.add(resultsPhraseType);
		}
		System.out.println(results);
		return results;

		// 2.1 | Vietnam <b>embassy</b> <b>in</b> <b>Israel</b>: information on how
		// to get your <b>visa</b> at Vietnam
		// <b>embassy</b> <b>in</b> <b>Israel</b>. <b>...</b> <b>Spain</b>.
		// Scotland. Sweden. Slovakia. Switzerland. T
		// [Top of Page] <b>...</b>
		// [[ [NN-* IN-in NP-israel ], [NP-* IN-in NP-israel ], [NP-* IN-* TO-* NN-*
		// ], [NN-visa IN-* NN-* IN-in ]], [
		// [VB-get NN-visa IN-* NN-* IN-in .-* ], [VBD-* IN-* NN-* NN-* .-* ], [VB-*
		// NP-* ]]]

	}

	public void setMainPOS(String mainPOS) {
		this.mainPOS = mainPOS;
	}

	public String getMainPOS() {
		return mainPOS;
	}

	public List<String> getLemmas() {
		return lemmas;
	}

	public void setLemmas(List<String> lemmas) {
		this.lemmas = lemmas;
	}

	public List<String> getPOSs() {
		return POSs;
	}

	public void setPOSs(List<String> pOSs) {
		POSs = pOSs;
	}

	public ParseTreeMatcher getParseTreeMatcher() {
		return parseTreeMatcher;
	}

	public static void main(String[] args){
		String phrStr = "[<1>NP'Property':NN, <2>NP'has':VBZ, <3>NP'lots':NNS, <4>NP'of':IN, <5>NP'trash':NN, <6>NP'and':CC, <7>NP'debris':NN]";
	    ParseTreeChunk ch = new ParseTreeChunk(phrStr);
	    System.out.println(ch);
	}
}
