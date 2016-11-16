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

package opennlp.tools.parse_thicket.matching;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.ParseCorefBuilderWithNER;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class Matcher implements IGeneralizer<List<List<ParseTreeNode>>>{
	public static String resourceDir = new File(".").getAbsolutePath().replace("/.", "") + "/src/test/resources";
	VerbNetProcessor proc = VerbNetProcessor.getInstance(resourceDir);

	protected PhraseGroupGeneralizer pgGen = new PhraseGroupGeneralizer();

	protected static ParseCorefBuilderWithNER ptBuilder = null;
	
	static {
		synchronized (Matcher.class) {
			ptBuilder = ParseCorefBuilderWithNER.getInstance();
		}
	}
	
	
	PT2ThicketPhraseBuilder phraseBuilder = new PT2ThicketPhraseBuilder();
	protected Map<String, ParseThicket> parseThicketHash = new HashMap<String, ParseThicket>();


	/**	   * The key function of similarity component which takes two portions of text
	 * and does similarity assessment by finding the set of all maximum common
	 * subtrees of the set of parse trees for each portion of text
	 * 
	 * @param input
	 *          text 1
	 * @param input
	 *          text 2
	 * @return the matching results structure, which includes the similarity score
	 */
	private static Matcher instance;

	public synchronized static Matcher getInstance() {
		if (instance == null)
			instance = new Matcher();

		return instance;
	}


	public List<List<ParseTreeChunk>> assessRelevance(String para1, String para2) {
		// first build PTs for each text
		ParseThicket pt1 = ptBuilder.buildParseThicket(para1);
		ParseThicket pt2 = ptBuilder.buildParseThicket(para2);
		// then build phrases and rst arcs
		List<List<ParseTreeNode>> phrs1 = phraseBuilder.buildPT2ptPhrases(pt1);
		List<List<ParseTreeNode>> phrs2 = phraseBuilder.buildPT2ptPhrases(pt2);
		// group phrases by type
		List<List<ParseTreeChunk>> sent1GrpLst = formGroupedPhrasesFromChunksForPara(phrs1), 
				sent2GrpLst = formGroupedPhrasesFromChunksForPara(phrs2);


		List<List<ParseTreeChunk>> res = pgGen.generalize(sent1GrpLst, sent2GrpLst);

		return res;

	}


	public List<List<ParseTreeChunk>> assessRelevance(List<List<ParseTreeChunk>> para0, String para2) {
		// first build PTs for each text

		ParseThicket pt2 = ptBuilder.buildParseThicket(para2);
		// then build phrases and rst arcs
		List<List<ParseTreeNode>> phrs2 = phraseBuilder.buildPT2ptPhrases(pt2);
		// group phrases by type
		List<List<ParseTreeChunk>> sent2GrpLst = formGroupedPhrasesFromChunksForPara(phrs2);


		List<List<ParseTreeChunk>> res = pgGen.generalize(para0, sent2GrpLst);

		return res;

	}

	public GeneralizationResult  assessRelevanceG(List<List<ParseTreeChunk>> para0, String para2) {
		List<List<ParseTreeChunk>> res = assessRelevance( para0, para2);
		return new GeneralizationResult(res);
	}

	public GeneralizationResult  assessRelevanceG(String para0, String para2) {
		List<List<ParseTreeChunk>> res = assessRelevance( para0, para2);
		return new GeneralizationResult(res);
	}

	public GeneralizationResult  assessRelevanceG(GeneralizationResult  para0, String para2) {
		List<List<ParseTreeChunk>> res = assessRelevance( para0.getGen(), para2);
		return new GeneralizationResult(res);
	}

	public List<List<ParseTreeChunk>> assessRelevanceCache(String para1, String para2) {
		// first build PTs for each text

		ParseThicket pt1 = parseThicketHash.get(para1);
		if (pt1==null){
			pt1=	ptBuilder.buildParseThicket(para1);
			parseThicketHash.put(para1, pt1);
		}

		ParseThicket pt2 = parseThicketHash.get(para2);
		if (pt2==null){
			pt2=	ptBuilder.buildParseThicket(para2);
			parseThicketHash.put(para2, pt2);
		}

		// then build phrases and rst arcs
		List<List<ParseTreeNode>> phrs1 = phraseBuilder.buildPT2ptPhrases(pt1);
		List<List<ParseTreeNode>> phrs2 = phraseBuilder.buildPT2ptPhrases(pt2);
		// group phrases by type
		List<List<ParseTreeChunk>> sent1GrpLst = formGroupedPhrasesFromChunksForPara(phrs1), 
				sent2GrpLst = formGroupedPhrasesFromChunksForPara(phrs2);


		List<List<ParseTreeChunk>> res = pgGen.generalize(sent1GrpLst, sent2GrpLst);
		return res;

	}

	public List<List<ParseTreeChunk>> generalize(List<List<ParseTreeNode>> phrs1,
			List<List<ParseTreeNode>> phrs2) {
		// group phrases by type
		List<List<ParseTreeChunk>> sent1GrpLst = formGroupedPhrasesFromChunksForPara(phrs1), 
				sent2GrpLst = formGroupedPhrasesFromChunksForPara(phrs2);


		List<List<ParseTreeChunk>> res = pgGen.generalize(sent1GrpLst, sent2GrpLst);
		return res;
	}
	protected List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForPara(
			List<List<ParseTreeNode>> phrs) {
		List<List<ParseTreeChunk>> results = new ArrayList<List<ParseTreeChunk>>();
		List<ParseTreeChunk> nps = new ArrayList<ParseTreeChunk>(), vps = new ArrayList<ParseTreeChunk>(), 
				pps = new ArrayList<ParseTreeChunk>();
		for(List<ParseTreeNode> ps:phrs){
			ParseTreeChunk ch = new ParseTreeChunk(ps);
			String ptype = ps.get(0).getPhraseType();
			if (ptype.equals("NP")){
				nps.add(ch);
			} else if (ptype.equals("VP")){
				vps.add(ch);
			} else if (ptype.equals("PP")){
				pps.add(ch);
			}
		}
		results.add(nps); results.add(vps); results.add(pps);
		return results;
	}

	private ParseTreeChunk convertNodeListIntoChunk(List<ParseTreeNode> ps) {
		List<String> lemmas = new ArrayList<String>(),  poss = new ArrayList<String>();
		for(ParseTreeNode n: ps){
			lemmas.add(n.getWord());
			poss.add(n.getPos());
		}
		ParseTreeChunk ch = new ParseTreeChunk(lemmas, poss, 0, 0);
		ch.setMainPOS(ps.get(0).getPhraseType());
		ch.setParseTreeNodes(ps);
		return ch;
	}

	// this function is the main entry point into the PT builder if rst arcs are required
	public ParseThicket buildParseThicketFromTextWithRST(String para){
		ParseThicket pt = ptBuilder.buildParseThicket(para);
		List<List<ParseTreeNode>> phrs = phraseBuilder.buildPT2ptPhrases(pt);
		pt.setPhrases(phrs);
		return pt;	
	}

	// verify that all sections (NP, PRP and VP are present
	public boolean isCoveredByTemplate(List<List<ParseTreeChunk>> template, List<List<ParseTreeChunk>> sampleGen){
		try {
			if (template.size() == sampleGen.size() && sampleGen.get(0).size()>0  &&  sampleGen.get(1).size()>0  )
				//template.get(0).get(0).getParseTreeNodes().size() == template.get(0).get(0).size())
				return true;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	@Override
	public List<List<List<ParseTreeNode>>> generalize(Object o1, Object o2) {
		// TODO Auto-generated method stub
		return null;
	}


	public static void main(String[] args){
		Matcher m = new Matcher();

		m.buildParseThicketFromTextWithRST("Mary Poppins got her identification 8765");

		List<List<ParseTreeChunk>> template = m.assessRelevance("John Doe send his California driver license 1234567", 
				"John Travolta send her california license 4567456"
				//"New York hid her US social number 666-66-6666");
				);

		System.out.println(template+"\n");
		//in		
		List<List<ParseTreeChunk>> res = m.assessRelevance(template, "Mary Jones send her Canada prisoner id number 666666666");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));
		res = m.assessRelevance(template, "Mary Stewart hid her Mexico cook id number 666666666");
		System.out.println(res + " => "+
				m.isCoveredByTemplate(template, res));
		res = m.assessRelevance(template, "Robin mentioned her Peru fisher id  2345");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));
		res = m.assessRelevance(template, "Yesterday Peter Doe hid his Bolivia set id number 666666666");
		System.out.println(res + " => "+
				m.isCoveredByTemplate(template, res));
		res = m.assessRelevance(template, "Robin mentioned her best Peru fisher man id  2345");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));
		//out		
		res = m.assessRelevance(template, "Spain hid her Canada driver id number 666666666");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));
		res = m.assessRelevance(template, "John Poppins hid her  prisoner id  666666666");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));

		res = m.assessRelevance(template, "Microsoft announced its Windows Azure release number 666666666");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));
		res = m.assessRelevance(template, "John Poppins hid her Google id  666666666");
		System.out.println(res+ " => "+
				m.isCoveredByTemplate(template, res));
	}
}

