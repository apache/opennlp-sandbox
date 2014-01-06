package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import junit.framework.TestCase;

public class PTMatcherTest extends TestCase {
	Matcher m = new Matcher();
	
	public void testMatchTwoParaTestReduced(){
		String q = "I am a US citizen living abroad, and concerned about the health reform regulation of 2014. I do not want to wait till I am sick to buy health insurance. I am afraid I will end up paying the tax.";
		String a = "People are worried about having to pay a fine for not carrying health insurance coverage got more guidance this week with some new federal regulations. "+
				"Hardly anyone will end up paying the tax when the health reform law takes full effect in 2014. "+
				"The individual mandate makes sure that people don’t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they make too little money to file an income tax return, or US citizens living abroad."; 
		List<List<ParseTreeChunk>> res = m.assessRelevance(q, a);
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
		assertEquals(res.toString(), "[[ [NNP-us NN-citizen VBG-living RB-abroad ],  [,-, CC-* ],  [DT-a NNP-* ],  [DT-the NN-* NN-health NN-reform NN-* CD-2014 ],  [NN-* IN-* CD-2014 ],  [NN-health NN-* NN-* IN-* ],  [NN-regulation ], " +
				" [DT-the NN-health NN-reform NN-* ],  [CD-2014 ],  [NN-health NN-insurance ],  [DT-the NN-tax ],  [NN-tax ]], [ [VBP-* DT-a NNP-* NN-health NN-* NN-* NN-regulation ],  [NN-health NN-* NN-* NN-regulation ],  [NN-regulation ], " +
				" [DT-the NN-* NN-health NN-reform NN-* CD-2014 ],  [NN-* IN-* CD-2014 ],  [IN-* NN-health NN-* ],  [NNP-us NN-citizen VBG-living RB-abroad ],  [,-, CC-* ],  [NN-health NN-* NN-* IN-* ], " +
				" [IN-about NN-health NN-* NN-* NN-regulation ],  [VBG-living RB-abroad ],  [TO-to VB-* VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [TO-to VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  " +
				"[TO-to VB-* NN-health NN-insurance ],  [TO-to VB-buy NN-health NN-insurance ],  [VB-* TO-to VB-* VB-* NN-health NN-insurance ],  [TO-to VB-* VB-* NN-health NN-insurance ],  [RB-not VB-* NN-health NN-insurance ],  [VBG-paying DT-* NN-* ],  " +
				"[MD-will VB-end RP-up VBG-paying DT-the NN-tax ],  [VB-end RP-up VBG-paying DT-the NN-tax ],  [VBG-paying DT-the NN-tax ],  [VBP-do RB-* VB-* TO-* TO-to VB-* ],  [VB-* VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ], " +
				" [VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [TO-to VB-* VB-buy NN-health NN-insurance ],  [VB-buy NN-health NN-insurance ],  [NN-health NN-insurance NN-tax ],  " +
				"[TO-to VB-* NN-tax ],  [NN-tax ],  [VB-* TO-to VB-* VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VB-* TO-to VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VB-* NN-health NN-insurance ],  [VB-* VBG-paying DT-* NN-* ]]]");
	
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
		assertEquals(res.toString(), "[[ [DT-the NNP-un NN-* ],  [PRP$-its JJ-nuclear NNS-weapons ],  [NN-work IN-on JJ-nuclear NNS-weapons ],  [PRP$-its NN-* JJ-nuclear NNS-* ],  [PRP$-its JJ-nuclear NNS-* ],  [DT-a NN-* PRP$-its JJ-* NN-* ],  [DT-a NN-resolution VBG-* NNP-iran IN-* VBG-developing PRP$-its NN-uranium NN-enrichment NN-site ],  [NN-* VBG-* NNP-iran ],  [DT-a NN-resolution VBG-* NNP-* NNP-iran ],  [DT-a NN-resolution NNP-iran ],  [DT-a NNP-iran ],  [DT-a PRP$-its ],  [NNP-iran IN-* VBG-developing PRP$-its NN-uranium NN-enrichment NN-site ],  [IN-for ],  [VBG-* PRP$-its JJ-* NN-* ],  [PRP$-its NN-uranium NN-enrichment NN-site ],  [PRP$-its JJ-* NN-* ],  [VBD-* NNP-iran VBD-was VBG-working IN-on JJ-nuclear NNS-weapons ],  [VBG-* JJ-nuclear NNS-* ],  [JJ-nuclear NNS-weapons ],  [JJ-nuclear NNS-* ],  [NNP-iran NN-envoy ],  [NN-* IN-* PRP-it ],  [NN-* PRP-it ],  [DT-the NN-* NN-evidence IN-against PRP-it ],  [DT-the NN-* NN-* ],  [PRP-it ],  [DT-the NNP-us ],  [DT-the NNP-* ],  [DT-a NN-resolution DT-a JJ-recent NNP-* NN-report ],  [DT-a JJ-recent NNP-* NN-report ],  [NN-* PRP$-its JJ-nuclear NN-* ],  [PRP$-its JJ-nuclear NN-* ],  [VBZ-* PRP$-its ],  [NN-development ],  [PRP$-its JJ-nuclear NN-development ],  [JJ-peaceful NN-purpose ],  [NN-* VBZ-says ],  [NNP-un JJ-nuclear NN-* VBZ-* ],  [NN-* VBZ-* PRP$-its JJ-nuclear NN-development VBZ-is IN-for JJ-peaceful NN-purpose ],  [JJ-nuclear NN-* VBZ-* NN-development VBZ-is IN-for JJ-peaceful NN-purpose ],  [NNP-un NN-* PRP$-its ]], [ [VBZ-refuses TO-to VB-* DT-* NNP-* ],  [VB-* DT-the NNP-un NN-* TO-to VB-end PRP$-its ],  [NNP-un ],  [NNP-* NN-* TO-to ],  [TO-to VB-end PRP$-its ],  [VBZ-* DT-a NN-* PRP$-its JJ-* NN-* ],  [VBZ-passes DT-a NN-resolution VBG-* NNP-iran IN-* VBG-developing PRP$-its NN-uranium NN-enrichment NN-site ],  [NN-* VBG-* NNP-iran ],  [VBG-* NNP-iran IN-* VBG-developing PRP$-its NN-uranium NN-enrichment NN-site ],  [IN-for ],  [PRP$-its JJ-* NN-* ],  [VBG-developing PRP$-its NN-uranium NN-enrichment NN-site ],  [VBG-* PRP$-its JJ-* NN-* ],  [VBD-presented NNS-* NNP-iran VBD-was VBG-working IN-on JJ-nuclear NNS-weapons ],  [VBD-* NNP-iran VBD-was VBG-working IN-on JJ-nuclear NNS-weapons ],  [NNP-iran ],  [VBD-was VBG-working IN-on JJ-nuclear NNS-weapons ],  [JJ-nuclear NNS-weapons ],  [VBG-* JJ-nuclear NNS-* ],  [VBG-working IN-on JJ-nuclear NNS-weapons ],  [PRP$-its JJ-nuclear NN-* ],  [NN-development ],  [VBZ-says JJ-nuclear NN-* ],  [VBZ-* PRP$-its JJ-nuclear NN-development VBZ-is IN-for JJ-peaceful NN-purpose ],  [VBZ-* JJ-nuclear NN-* ],  [VBZ-is IN-for JJ-peaceful NN-purpose ],  [VBN-* VBN-fabricated IN-by DT-the NNP-us ],  [VBN-fabricated IN-by DT-the NNP-us ],  [TO-to VB-* DT-* NNP-* VB-end PRP$-its ],  [VB-end PRP$-its ],  [NN-* IN-over PRP$-its ],  [PRP$-its JJ-nuclear NNS-weapons ],  [DT-a ],  [TO-* VB-* PRP$-its NN-* ],  [VB-* PRP$-its NN-* ],  [VB-* PRP$-its JJ-nuclear NNS-* ],  [DT-the NNP-* ],  [TO-to NNP-un ],  [NN-work IN-on JJ-nuclear NNS-weapons ]]]");
	}

	public void testMatchTwoParaTest2(){
		List<List<ParseTreeChunk>> res = m.assessRelevance("I am a US citizen living abroad, and concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"I am afraid I will end up paying the tax. "+
				"I am worried about having to pay a fine for not having health insurance coverage. "
				, 
				"People are worried about having to pay a fine for not carrying health insurance coverage got more guidance this week with some new federal regulations. "+
						"Hardly anyone will end up paying the tax when the health reform law takes full effect in 2014. "+
						"The individual mandate makes sure that people don’t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they make too little money to file an income tax return, or US citizens living abroad.");
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
		assertEquals(res.toString(), "[[ [NNP-us NN-citizen VBG-living RB-abroad ],  [,-, CC-* ],  [DT-a NNP-* ],  [DT-the NN-* NN-health NN-reform NN-* CD-2014 ],  " +
				"[NN-* IN-* CD-2014 ],  [NN-health NN-* NN-* IN-* ],  [NN-regulation ],  [DT-the NN-health NN-reform NN-* ],  [CD-2014 ],  [DT-the NN-tax ],  [NN-tax ], " +
				" [DT-a NN-fine ],  [NN-health NN-insurance NN-coverage ],  [TO-to VB-* DT-* NN-* ],  [NN-fine IN-* ],  [NN-health NN-insurance NN-* ]], " +
				"[ [VBP-* DT-a NNP-* NN-health NN-* NN-* NN-regulation ],  [NN-health NN-* NN-* NN-regulation ],  [NN-regulation ],  [DT-the NN-* NN-health NN-reform NN-* CD-2014 ], " +
				" [NN-* IN-* CD-2014 ],  [IN-* NN-health NN-* ],  [NNP-us NN-citizen VBG-living RB-abroad ],  [,-, CC-* ],  [NN-health NN-* NN-* IN-* ],  [IN-about NN-health NN-* NN-* NN-regulation ],  [VBG-living RB-abroad ],  [TO-to VB-* VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [TO-to VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [TO-to VB-buy NN-health NN-insurance ],  [VBG-* VB-pay DT-* NN-* NN-health NN-* NN-* ],  [VB-pay DT-* NN-* NN-health NN-* NN-* ],  [RB-not VBG-* NN-health NN-insurance NN-coverage ],  [VBG-having NN-health NN-insurance NN-coverage ],  [NN-health NN-insurance NN-tax ],  [TO-to VB-* NN-tax ],  [VB-* TO-to VB-* VB-* NN-health NN-insurance ],  [TO-to VB-* VB-* NN-health NN-insurance ],  [TO-to VB-* VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [RB-not VB-* NN-health NN-insurance NN-coverage ],  [VBP-do RB-* VB-* TO-* TO-to VB-* ],  [VB-* VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [TO-to VB-* VB-buy NN-health NN-insurance ],  [VB-buy NN-health NN-insurance ],  [VB-* TO-to VB-* VB-wait IN-* PRP-* VBP-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VB-* TO-to VB-* JJ-sick TO-to VB-buy NN-health NN-insurance ],  [VB-* TO-to VB-* VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [VB-* NN-health NN-insurance NN-coverage ],  [VBG-having TO-to VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [TO-to VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [VBG-paying DT-* NN-* DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [VBG-* NN-health NN-insurance NN-coverage ],  [MD-will VB-end RP-up VBG-paying DT-the NN-tax ],  [VB-end RP-up VBG-paying DT-the NN-tax NN-health NN-* NN-* ],  [VBG-paying DT-the NN-tax NN-health NN-* NN-* ],  [TO-to VB-* NN-health NN-insurance ],  [NN-fine IN-* ],  [NN-health NN-insurance NN-* ],  [TO-to VB-* DT-* NN-* ],  [NN-tax ],  [VBP-* VBN-worried IN-about VBG-having TO-to VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ],  [VB-* VBG-paying DT-* NN-* DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ], " +
				" [VBN-worried IN-about VBG-having TO-to VB-pay DT-a NN-fine IN-for RB-not VBG-* NN-health NN-insurance NN-coverage ]]]");
	}


	public void testMatchTwoParaTestCA(){
		List<List<ParseTreeChunk>> res = m.assessRelevance("As a US citizen living abroad, I am concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"Yet I am afraid I will end up paying the tax. "+
				"Although I live abroad, I am worried about having to pay a fine for being reported as not having health insurance coverage. "
				, 
				"People are worried about paying a fine for not carrying health insurance coverage, having been informed by IRS about new regulations. "+
						"Yet hardly anyone is expected to pay the tax, when the health reform law takes full effect in 2014. "+
						"The individual mandate confirms that people don’t wait until they are sick to buy health insurance. "+
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
				"The individual mandate confirms that people don’t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine if they report they make too little money, or US citizens living abroad.";
		List<List<ParseTreeChunk>> res = m.assessRelevance(text1, text2);
		System.out.print(res);
		assertTrue(res!=null);
		assertTrue(res.size()>0);
	}

}


