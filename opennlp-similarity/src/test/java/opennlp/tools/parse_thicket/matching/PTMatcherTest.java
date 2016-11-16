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
import java.util.List;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import junit.framework.TestCase;

public class PTMatcherTest extends TestCase {
	//public static String resourceDir = new File(".").getAbsolutePath().replace("/.", "") + "/src/test/resources";
	//VerbNetProcessor proc = VerbNetProcessor.getInstance(resourceDir);
	Matcher m = new Matcher();
	
	public void testMatchTwoParaTestReduced(){
		String q = "I am a US citizen living abroad, and concerned about the health reform regulation of 2014. I do not want to wait till I am sick to buy health insurance. I am afraid I will end up paying the tax.";
		String a = "People are worried about having to pay a fine for not carrying health insurance coverage got more guidance this week with some new federal regulations. "+
				"Hardly anyone will end up paying the tax when the health reform law takes full effect in 2014. "+
				"The individual mandate makes sure that people don�t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they make too little money to file an income tax return, or US citizens living abroad."; 
		List<List<ParseTreeChunk>> res = m.assessRelevance(q, a);
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
		assertEquals(  "[[NP [NNP-us (LOCATION) NN*-citizen VB-living RB-abroad ], NP [,-, CC-* ], NP [DT-the NN-* NN-health NN-reform NN-* CD-2014 ], NP [NN-health NN-* NN-* IN-* ], NP [DT-the NN-health NN-reform NN-* ], NP [NN-health NN-insurance ], NP [NN*-* NN-* JJ-* NN-* ]], [VP [VB-* {phrStr=[], phrDescr=[], roles=[A, *, *]} DT-a NN*-* NN-health NN-* NN-* NN*-regulation ], VP [VB-* NN*-* NN-* VB-* RB*-* IN-* DT-* NN*-regulation ], VP [VB-* NN-* NN-health NN-* NN-* ], VP [IN-about NN-health NN-* NN-* NN*-regulation ], VP [VB-living RB-abroad ], VP [TO-to VB-* VB-wait IN-* PRP-* VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ], VP [VB-* TO-to VB-* VB-* NN-health NN-insurance ], UCP [MD-will VB-end RP-up VB-paying DT-the NN-tax ], VP [TO-to VB-* VB-buy NN-health NN-insurance ], VP [VB-* TO-to VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ]]]" 
				, res.toString());
	
	}

	public void testMatchTwoParaTest1(){
		List<List<ParseTreeChunk>> res = m.assessRelevance("Iran refuses to accept the UN proposal to end its dispute over its work on nuclear weapons."+
				"UN nuclear watchdog passes a resolution condemning Iran for developing its second uranium enrichment site in secret. " +
				"A recent IAEA report presented diagrams that suggested Iran was secretly working on nuclear weapons. " +
				"Iran envoy says its nuclear development is for peaceful purpose, and the material evidence against it has been fabricated by the US. "

			, "Iran refuses the UN offer to end a conflict over its nuclear weapons."+
					"UN passes a resolution prohibiting Iran from developing its uranium enrichment site. " +
					"A recent UN report presented charts saying Iran was working on nuclear weapons. " +
				"Iran envoy to UN states its nuclear development is for peaceful purpose, and the evidence against its claim is fabricated by the US. ");
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
		assertEquals(res.toString(), 
				"[[NP [DT-a NN-* PRP$-its JJ-* NN-* ], NP [DT-a NN-resolution VB-* NNP-iran (LOCATION) IN-* VB-developing PRP$-its NN-uranium NN-enrichment NN-site ], NP [DT-a IN-for ], NP [DT-a PRP$-its ], NP [VB-* JJ-nuclear NN*-* ], NP [JJ-nuclear NNS-weapons ], NP [PRP$-its JJ-nuclear NN-development ], NP [DT-the NN-* NN-evidence IN-against PR*-it ], NP [DT-the NNP-un (ORGANIZATION) NN-* ], NP [VB-* NN-* NN-* NN-* ], NP [VB-* NNP-iran (LOCATION) NN*-* ], NP [NNP-iran (LOCATION) NN-envoy ]], [VP [VB-refuses TO-to VB-* DT-* NN*-* ], VP [VB-* DT-the NNP-un (ORGANIZATION) NN-* TO-to VB-end PRP$-its ], VP [VB-* NN-* NN-work IN-on JJ-nuclear NN*-weapons.un ], VP [VB-* DT-a NN-* NN-resolution VB-* NNP-iran (LOCATION) IN-* VB-developing PRP$-its ], VP [VB-* DT-a NN-* PRP$-its JJ-* NN-* ], VP [VB-passes DT-a NN-resolution VB-* NNP-iran (LOCATION) IN-* VB-developing PRP$-its NN-uranium NN-enrichment NN-site ], VP [PRP$-its JJ-* NN-* NN-uranium NN-enrichment NN-site ], VP [VB-presented NNS-* NNP-iran (LOCATION) VB-was VB-working IN-on JJ-nuclear NNS-weapons ], VP [VB-* VB-fabricated IN-by DT-the NNP-us (LOCATION) ], VP [VB-* DT-the NNP-un (ORGANIZATION) NN-* TO-to VB-end NN-* IN-over PRP$-its NNP-* ], VP [TO-to VB-* DT-* NN*-* VB-end PRP$-its ], VP [PRP$-its JJ-nuclear NN-weapons.un ], VP [IN-* VB-* PRP$-its NN-* ], VP [DT-a PRP$-its JJ-nuclear NN-* VB-* NN-development ], VP [DT-a VB-* PRP$-its ], VP [VB-* NN-development NN-* ], VP [NN*-* VB-says JJ-nuclear NN*-* ], VP [VB-is IN-for JJ-peaceful NN-purpose ]]]" )	;	}

	public void testMatchTwoParaTest2(){
		List<List<ParseTreeChunk>> res = m.assessRelevance("I am a US citizen living abroad, and concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"I am afraid I will end up paying the tax. "+
				"I am worried about having to pay a fine for not having health insurance coverage. "
				, 
				"People are worried about having to pay a fine for not carrying health insurance coverage got more guidance this week with some new federal regulations. "+
						"Hardly anyone will end up paying the tax when the health reform law takes full effect in 2014. "+
						"The individual mandate makes sure that people don�t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they make too little money to file an income tax return, or US citizens living abroad.");
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
		assertEquals(res.toString(), "[[NP [NNP-us (LOCATION) NN*-citizen VB-living RB-abroad ], NP [,-, CC-* ], NP [DT-the NN-* NN-health NN-reform NN-* CD-2014 ], NP [NN-health NN-* NN-* IN-* ], NP [DT-the NN-health NN-reform NN-* ], UCP [NN-health NN-insurance NN-coverage ], UCP [TO-to VB-* {phrStr=[], phrDescr=[], roles=[A, *, *]} DT-a NN-* ], NP [NN*-* NN-* JJ-* NN-* ]], [VP [VB-* {phrStr=[], phrDescr=[], roles=[A, *, *]} DT-a NN*-* NN-health NN-* NN-* NN*-regulation ], VP [VB-* NN*-* NN-* VB-* RB*-* IN-* DT-* NN*-regulation ], VP [IN-about NN-health NN-* NN-* NN*-regulation ], VP [VB-living RB-abroad ], VP [TO-to VB-* VB-wait IN-* PRP-* VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ], VP [VB-* VB-pay DT-* NN-* NN-health NN-* NN-* ], VP [VB-having NN-health NN-insurance NN-coverage ], UCP [MD-will VB-end RP-up VB-paying DT-the NN-tax ], VP [VB-* TO-to VB-* VB-* NN-health NN-insurance ], VP [TO-to VB-* VB-buy NN-health NN-insurance ], VP [VB-* TO-to VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ], VP [VB-* TO-to VB-* VB-pay {phrStr=[NP V NP PP.theme, NP V NP], phrDescr=[NP-PPfor-PP, (SUBCAT MP)], roles=[A, A, T]} DT-a NN-fine IN-for RB-not VB-* NN-health NN-insurance NN-coverage ], VP [VB-paying DT-the NN-tax NN-health NN-* NN-* ], VP [VB-* TO-to VB-* NN-health NN-insurance ], UCP [VB-* VB-worried IN-about VB-having TO-to VB-pay {phrStr=[NP V NP PP.theme, NP V NP], phrDescr=[NP-PPfor-PP, (SUBCAT MP)], roles=[A, A, T]} DT-a NN-fine IN-for RB-not VB-* NN-health NN-insurance NN-coverage ], VP [VB-paying DT-* NN-* DT-a NN-fine IN-for RB-not VB-* NN-health NN-insurance NN-coverage ]]]"
		);
	}


	public void testMatchTwoParaTestCA(){
		List<List<ParseTreeChunk>> res = m.assessRelevance("As a US citizen living abroad, I am concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"Yet I am afraid I will end up paying the tax. "+
				"Although I live abroad, I am worried about having to pay a fine for being reported as not having health insurance coverage. "
				, 
				"People are worried about paying a fine for not carrying health insurance coverage, having been informed by IRS about new regulations. "+
						"Yet hardly anyone is expected to pay the tax, when the health reform law takes full effect in 2014. "+
						"The individual mandate confirms that people don�t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they report they make too little money, or US citizens living abroad.");
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
	}

	public void testMatchTwoParaTestCA1(){
		String text1 = "As a US citizen living abroad, I am concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"Yet I am afraid I will end up being requested to pay the tax. "+
				"Although I live abroad, I am worried about having to pay a fine for being reported as not having health insurance coverage. ";

		String text2 =	"People are worried about paying a fine for not carrying health insurance coverage, having been informed by IRS about new regulations. "+
				"Yet hardly anyone is expected to pay the tax, when the health reform law takes full effect in 2014. "+
				"The individual mandate confirms that people don�t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they report they make too little money, or US citizens living abroad.";
		List<List<ParseTreeChunk>> res = m.assessRelevance(text1, text2);
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
	}
	
	public void testMatchTwoParaTestREq1(){
		String q = "I am buying a foreclosed house. "
				+ "A bank offered me to waive inspection; however I am afraid I will not identify "
				+ "some problems in this property unless I call a specialist.";

		String a1 =	"I am a foreclosure specialist in a bank which is subject to an inspection. "
				+ "FTC offered us to waive inspection "
				+ "if we can identify our potential problems with customers we lent money to buy their properties.";
		
		String a2 =	"My wife and I are buying a foreclosure from a bank. "
				+ "In return for accepting a lower offer, they want me to waive the inspection.  "
				+ "I prefer to let the bank know that I would not waive the inspection.";
		List<List<ParseTreeChunk>> res = m.assessRelevance(q, a1);
		assertEquals(res.toString(), "[[NP [DT-a NN-bank ], NP [NNS-problems ], NP [NN*-property ], NP [PRP-i ]], [VP [VB-am {phrStr=[NP V ADVP-Middle PP, NP V ADVP-MIddle], phrDescr=[Middle Construction, Middle Construction], roles=[A, P, P, P]} DT-a ], VP [VB-* TO-to NN-inspection ], VP [VB-offered PRP-* TO-to VB-waive NN-inspection ], VP [VB-* TO-to VB-* ], VP [VB-am {phrStr=[NP V ADVP-Middle PP, NP V ADVP-MIddle], phrDescr=[Middle Construction, Middle Construction], roles=[A, P, P, P]} NN*-* IN-in DT-* NN-* ], VP [VB-* VB-identify NNS-problems IN-* NN*-property ], VP [VB-* DT-* NN*-* VB-* ], VP [VB-* {phrStr=[], phrDescr=[], roles=[A, *, *]} DT-a NN-* ]]]");	    
		System.out.println(res);
		res = m.assessRelevance(q, a2);
		assertEquals(res.toString(), "[[NP [DT-a NN-bank ], NP [PRP-i ]], [VP [VB-* VB-buying DT-a ], VP [VB-* PRP-me TO-to VB-waive NN-inspection ], VP [TO-to VB-* VB-waive NN-inspection ], VP [VB-* {phrStr=[], phrDescr=[], roles=[]} PRP-i MD-* RB-not VB-* DT-* NN*-* ], VP [VB-* DT-* NN*-* VB-* DT-* NN-* ], VP [VB-* DT-a NN-* ]]]");
		System.out.println(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
	}

}


