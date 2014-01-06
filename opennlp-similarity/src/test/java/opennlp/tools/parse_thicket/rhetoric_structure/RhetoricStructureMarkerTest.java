package opennlp.tools.parse_thicket.rhetoric_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.textsimilarity.ParseTreeChunk;


public class RhetoricStructureMarkerTest extends TestCase  {
	
	private RhetoricStructureMarker rstMarker = new RhetoricStructureMarker();
	private Matcher matcher = new Matcher();
	
	public  RhetoricStructureMarkerTest(){

		
	}

	public void testRSTmarker(){
		String text1 = "As a US citizen living abroad, I am concerned about the health reform regulation of 2014. "+
				"I do not want to wait till I am sick to buy health insurance. "+
				"Yet I am afraid I will end up being requested to pay the tax. "+
				"Although I live abroad, I am worried about having to pay a fine for being reported as not having health insurance coverage. ";

		String text2 =	"People are worried about paying a fine for not carrying health insurance coverage, having been informed by IRS about new regulations. "+
				"Yet hardly anyone is expected to pay the tax, when the health reform law takes full effect in 2014. "+
				"The individual mandate confirms that people don’t wait until they are sick to buy health insurance. "+
				"People are exempt from health insurance fine as long as they report they make too little money, or US citizens living abroad.";
		ParseThicket pt = matcher.buildParseThicketFromTextWithRST(text1);
		for(List<ParseTreeNode> sent: pt.getNodesThicket()){
			List<Pair<String, Integer[]>> res = rstMarker .extractRSTrelationInSentenceGetBoundarySpan(sent);
			System.out.println(rstMarker.markerToString(res));
		}
		
		//assertTrue(res.size()>1);
		
		
		pt = matcher.buildParseThicketFromTextWithRST(text2);
		for(List<ParseTreeNode> sent: pt.getNodesThicket()){
			List<Pair<String, Integer[]>> res = rstMarker .extractRSTrelationInSentenceGetBoundarySpan(sent);
			System.out.println(rstMarker.markerToString(res));
		}
		
	}

	public void testLocal(){
		ParseTreeNode[] sent = 	
		new ParseTreeNode[]{new ParseTreeNode("he","prn"), new ParseTreeNode("was","vbz"), new ParseTreeNode("more","jj"), 
				new ParseTreeNode(",",","),  new ParseTreeNode("than",","), new ParseTreeNode("little","jj"), new ParseTreeNode("boy","nn"),
				new ParseTreeNode(",",","), new ParseTreeNode("however","*"), new ParseTreeNode(",",","),
				new ParseTreeNode("he","prp"), new ParseTreeNode("was","vbz"), new ParseTreeNode("adult","jj")
		};
		
		List<Pair<String, Integer[]>> res = rstMarker.extractRSTrelationInSentenceGetBoundarySpan(Arrays.asList(sent));
		assertTrue(res.size()>2);
		assertTrue(res.get(0).getFirst().startsWith("contrast"));
		System.out.println(rstMarker.markerToString(res));
	}
}
