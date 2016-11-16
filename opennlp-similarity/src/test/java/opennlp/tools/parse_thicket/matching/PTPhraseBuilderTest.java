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

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;

import opennlp.tools.parse_thicket.ParseCorefsBuilder;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import junit.framework.TestCase;

public class PTPhraseBuilderTest extends TestCase {
	private ParseCorefsBuilder ptBuilder = ParseCorefsBuilder.getInstance();
	private PT2ThicketPhraseBuilder phraseBuilder = new PT2ThicketPhraseBuilder();
		
	public void testBuildPhraseForUCP(){
		String q = "I am a US citizen living abroad, and concerned about the health reform regulation of 2014. " +
				"I do not want to wait till I am sick to buy health insurance. I am afraid I will end up paying the tax.";
		
		ParseThicket pt = ptBuilder.buildParseThicket(q);
		List<ParseTreeNode> sentence = pt.getNodesThicket().get(0);
		Tree ptree = pt.getSentences().get(0);		
		List<List<ParseTreeNode>> res = phraseBuilder.buildPT2ptPhrasesForASentence(ptree, sentence );	
		assertTrue(res!=null);  
		assertEquals(res.get(7).toString(), 
				"[<10>ADJP'concerned':JJ, <11>ADJP'about':IN, <12>ADJP'the':DT, <13>ADJP'health':NN, <14>ADJP'reform':NN, <15>ADJP'regulation':NN, <16>ADJP'of':IN, <17>ADJP'2014':CD]");
		
		assertTrue(res.size()>12);
		
		sentence = pt.getNodesThicket().get(1);
		ptree = pt.getSentences().get(1);		
		ptree.pennPrint();
		res = phraseBuilder.buildPT2ptPhrasesForASentence(ptree, sentence );		
		assertTrue(res!=null);
		assertTrue(res.size()>0);
	
	}
	
	public void testParsePhrase(){
		String line = "(NP (NNP Iran)) (VP (VBZ refuses) (S (VP (TO to) (VP (VB accept) (S (NP (DT the) " +
				"(NNP UN) (NN proposal)) (VP (TO to) (VP (VB end) (NP (PRP$ its) (NN dispute))))))))";

		List<ParseTreeNode> res = phraseBuilder. parsePhrase("NP", line);
		System.out.println(res);
		assertEquals(res.toString(), 
				"[NP'Iran':NNP, NP'refuses':VBZ, NP'to':TO, NP'accept':VB, NP'the':DT, NP'UN':NNP, NP'proposal':NN, NP'to':TO, NP'end':VB, NP'its':PRP$, NP'dispute':NN]");


		line = "(VP (VBP am) (NP (NP (DT a) (NNP US) (NN citizen)) (UCP (VP (VBG living) (ADVP (RB abroad))) (, ,) (CC and) (ADJP (JJ concerned) (PP (IN about) (NP (NP (DT the) (NN health) (NN reform) (NN regulation)) (PP (IN of) (NP (CD 2014)))))))))";
		res = phraseBuilder. parsePhrase("VP", line);
		System.out.println(res);
		assertEquals(res.toString(), "[VP'am':VBP, VP'a':DT, VP'US':NNP, VP'citizen':NN, VP'living':VBG, VP'abroad':RB, VP',':,, VP'and':CC, VP'concerned':JJ, VP'about':IN, VP'the':DT, VP'health':NN, VP'reform':NN, VP'regulation':NN, VP'of':IN, VP'2014':CD]");

		
		line = "(VP (TO to) (VP (VB wait) (SBAR (IN till) (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ sick) (S (VP (TO to) (VP (VB buy) (NP (NN health) (NN insurance)))))))))))";
		res = phraseBuilder. parsePhrase("VP", line);
		assertEquals(res.toString(), "[VP'to':TO, VP'wait':VB, VP'till':IN, VP'I':PRP, VP'am':VBP, VP'sick':JJ, VP'to':TO, VP'buy':VB, VP'health':NN, VP'insurance':NN]");
		System.out.println(res);
	}
	
	public void testBuilderPTPhrase(){
		String q = "I am a US citizen living abroad, and concerned about the health reform regulation of 2014. " +
				"I do not want to wait till I am sick to buy health insurance. I am afraid I will end up paying the tax.";
			ParseThicket pt = ptBuilder.buildParseThicket(q);
			List<List<ParseTreeNode>> res = phraseBuilder.buildPT2ptPhrases(pt);
			assertTrue(res!=null);
			assertTrue(res.size()>0);

	}

}


