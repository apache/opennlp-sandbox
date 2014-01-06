package opennlp.tools.parse_thicket.matching;

import java.util.List;

import opennlp.tools.parse_thicket.ParseTreeNode;


import junit.framework.TestCase;

public class PT2ThicketPhraseBuilderTest extends TestCase {
	private PT2ThicketPhraseBuilder builder = new PT2ThicketPhraseBuilder();
	
	public  void testParsePhrase(){
		  String line = "(NP (NNP Iran)) (VP (VBZ refuses) (S (VP (TO to) (VP (VB accept) (S (NP (DT the) " +
		  		"(NNP UN) (NN proposal)) (VP (TO to) (VP (VB end) (NP (PRP$ its) (NN dispute))))))))";
		  
		  List<ParseTreeNode> res = builder.parsePhrase("NP", line);
		  System.out.println(res);
		  assertTrue(res!=null);
		  assertTrue(res.size()>0);
				   
	  }
}
