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
package opennlp.tools.parse_thicket.communicative_actions;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.similarity.apps.HitBase;

import junit.framework.TestCase;

public class CommunicativeActionsArcBuilderTest extends TestCase {
	Matcher matcher = new Matcher();
	
	public void testCommunicativeActionsArcBuilderTestQ(){
		String text = "As a US citizen living abroad, I am concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"Yet I am afraid I will end up being requested to pay the tax. "+
				"Although I live abroad, I am worried about having to pay a fine for being reported as not having health insurance coverage. ";
		ParseThicket pt = matcher.buildParseThicketFromTextWithRST(text);
		List<WordWordInterSentenceRelationArc> results = new ArrayList<WordWordInterSentenceRelationArc>();
		for(WordWordInterSentenceRelationArc arc: pt.getArcs()){
			if(arc.getArcType().getType().startsWith("ca")){
				results.add(arc);
				System.out.println(arc);
			}
		}
		assertTrue(results.size()>11);
		
	}
	public void testCommunicativeActionsArcBuilderTestA(){
		String text =	"People are worried about paying a fine for not carrying health insurance coverage, having been informed by IRS about new regulations. "+
				"Yet hardly anyone is expected to pay the tax, when the health reform law takes full effect in 2014. "+
				"The individual mandate confirms that people don’t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they report they make too little money, or US citizens living abroad.";
		ParseThicket pt = matcher.buildParseThicketFromTextWithRST(text);
		List<WordWordInterSentenceRelationArc> results = new ArrayList<WordWordInterSentenceRelationArc>();
		for(WordWordInterSentenceRelationArc arc: pt.getArcs()){
			if(arc.getArcType().getType().startsWith("ca")){
				results.add(arc);
				System.out.println(arc);
			}
		}
		assertTrue(results.size()>5);
	}
	

	

}
