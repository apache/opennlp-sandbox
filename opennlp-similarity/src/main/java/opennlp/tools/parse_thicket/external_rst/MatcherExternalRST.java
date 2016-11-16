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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.ParseCorefBuilderWithNER;
import opennlp.tools.parse_thicket.ParseCorefsBuilder;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.matching.GeneralizationResult;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.parse_thicket.matching.PT2ThicketPhraseBuilder;
import opennlp.tools.parse_thicket.matching.PhraseGroupGeneralizer;
import opennlp.tools.textsimilarity.LemmaPair;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeMatcherDeterministic;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class MatcherExternalRST extends Matcher implements IGeneralizer<List<List<ParseTreeNode>>>{

	ParseCorefBuilderWithNERandRST ptBuilderRST = new ParseCorefBuilderWithNERandRST();
	PT2ThicketPhraseBuilder phraseBuilder = new PT2ThicketPhraseBuilder();

	private String externRSTpath;

	public List<List<ParseTreeChunk>> assessRelevance(String para1, String para2) {
		// first build PTs for each text
		ParseThicket pt1 = ptBuilderRST.buildParseThicket(para1);
		ParseThicket pt2 = ptBuilderRST.buildParseThicket(para2);
		// then build phrases and rst arcs
		List<List<ParseTreeNode>> phrs1 = phraseBuilder.buildPT2ptPhrases(pt1);
		List<List<ParseTreeNode>> phrs2 = phraseBuilder.buildPT2ptPhrases(pt2);
		// group phrases by type
		List<List<ParseTreeChunk>> sent1GrpLst = formGroupedPhrasesFromChunksForPara(phrs1), 
				sent2GrpLst = formGroupedPhrasesFromChunksForPara(phrs2);

		
		List<List<ParseTreeChunk>> res = pgGen.generalize(sent1GrpLst, sent2GrpLst);
				
		return res;

	}
	
	// this function is the main entry point into the PT builder if rst arcs are required
		public ParseThicket buildParseThicketFromTextWithRST(String para){
			ParseThicket pt = ptBuilderRST.buildParseThicket(para);
			
			List<List<ParseTreeNode>> phrs = phraseBuilder.buildPT2ptPhrases(pt);
			if (pt!=null)
				pt.setPhrases(phrs);
			return pt;	
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
	
	
	public static void main(String[] args){
		//MatcherExternalRST m = new MatcherExternalRST();
		
		
	}
}
