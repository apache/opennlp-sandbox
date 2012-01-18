package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.List;

import junit.framework.TestCase;

public class PhraseNodeTest extends TestCase{
	ParserChunker2MatcherProcessor proc = ParserChunker2MatcherProcessor.getInstance();
    public void testPOSTagsExtraction(){
    	
    	SentenceNode node  = proc.parseSentenceNode("How can I get there");
		List<String> pOSlist = node.getOrderedPOSList();
		assertEquals("[WRB, MD, PRP, VB, RB]", pOSlist.toString());
		
		node  = proc.parseSentenceNode("where do I apply");
		pOSlist = node.getOrderedPOSList();
		assertEquals("[WRB, VBP, PRP, RB]", pOSlist.toString());
		
		// should NOT start with upper case! last tag is missing
		node  = proc.parseSentenceNode("Where do I apply");
		pOSlist = node.getOrderedPOSList();
		assertEquals("[WRB, VBP, PRP]", pOSlist.toString());
    }
    	
}
