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

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.SentencePairMatchResult;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;
import junit.framework.TestCase;

public class PairwiseMatcherTest extends TestCase {
	public void testMatchTwoParaTestReduced(){
		String q = "I am a US citizen living abroad, and concerned about the health reform regulation of 2014. I do not want to wait till I am sick to buy health insurance. I am afraid I will end up paying the tax.";
		String a = "People are worried about having to pay a fine for not carrying health insurance coverage got more guidance this week with some new federal regulations. "+
				"Hardly anyone will end up paying the tax when the health reform law takes full effect in 2014. "+
				"The individual mandate makes sure that people don�t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they make too little money to file an income tax return, or US citizens living abroad."; 
		ParserChunker2MatcherProcessor sm = ParserChunker2MatcherProcessor.getInstance();
		SentencePairMatchResult res1 = sm.assessRelevance(a, q);
		System.out.print(res1.getMatchResult());
		System.out.print(res1);
		assertTrue(res1!=null);
		assertTrue(res1.getMatchResult().size()>0);
	
	}

	

}


