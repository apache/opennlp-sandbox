package opennlp.tools.parse_thicket;

import java.io.*;
import java.util.*;

import opennlp.tools.parse_thicket.communicative_actions.CommunicativeActionsArcBuilder;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;

public class ParseCorefsBuilder {
	protected static ParseCorefsBuilder instance;
	private Annotation annotation;
	StanfordCoreNLP pipeline;
	CommunicativeActionsArcBuilder caFinder = new CommunicativeActionsArcBuilder();
	
	  /**
	   * singleton method of instantiating the processor
	   * 
	   * @return the instance
	   */
	  public synchronized static ParseCorefsBuilder getInstance() {
	    if (instance == null)
	      instance = new ParseCorefsBuilder();

	    return instance;
	  }
	
	ParseCorefsBuilder(){
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		pipeline = new StanfordCoreNLP(props);
	}
	
	public ParseThicket buildParseThicket(String text){
		List<Tree> ptTrees = new ArrayList<Tree>();
		// all numbering from 1, not 0
		List<WordWordInterSentenceRelationArc> arcs = new ArrayList<WordWordInterSentenceRelationArc>();
		List<List<ParseTreeNode>> nodesThicket = new ArrayList<List<ParseTreeNode>>();
		
		annotation = new Annotation(text);
		try {
			pipeline.annotate(annotation);
			List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
			if (sentences != null && sentences.size() > 0) 
			for(CoreMap sentence: sentences){
				List<ParseTreeNode> nodes = new ArrayList<ParseTreeNode>();
				
				// traversing the words in the current sentence
			    // a CoreLabel is a CoreMap with additional token-specific methods
				Class<TokensAnnotation> tokenAnn = TokensAnnotation.class;
				List<CoreLabel> coreLabelList = sentence.get(tokenAnn);
				int count=1;
			    for (CoreLabel token: coreLabelList ) {
			      // this is the text of the token
			      String lemma = token.get(TextAnnotation.class);
			      // this is the POS tag of the token
			      String pos = token.get(PartOfSpeechAnnotation.class);
			      // this is the NER label of the token
			      String ne = token.get(NamedEntityTagAnnotation.class);     
			      nodes.add(new ParseTreeNode(lemma, pos, ne, count));
			      count++;
			    }	
			    nodesThicket.add(nodes);
			  Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
			  ptTrees.add(tree);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	    
	  
	    // now coreferences
	    Map<Integer, CorefChain> corefs = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
	    List<CorefChain> chains = new ArrayList<CorefChain>(corefs.values());
	    for(CorefChain c: chains){
	      //System.out.println(c);
	      List<CorefMention> mentions = c.getMentionsInTextualOrder();
	      //System.out.println(mentions);
	      if (mentions.size()>1)
	      for(int i=0; i<mentions.size(); i++){
	    	  for(int j=i+1; j<mentions.size(); j++){
	    	  CorefMention mi = mentions.get(i), mj=mentions.get(j);
	    	  
	    	  
	    	  int niSentence = mi.position.get(0);
	    	  int niWord = mi.startIndex;
	    	  int njSentence = mj.position.get(0);
	    	  int njWord = mj.startIndex;
	    	  
	    	  ArcType arcType = new ArcType("coref-", mj.mentionType+"-"+mj.animacy, 0, 0);
	    	  
	    	  WordWordInterSentenceRelationArc arc = 
	    			  new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(niSentence,niWord), 
	    					  new Pair<Integer, Integer>(njSentence,njWord), mi.mentionSpan, mj.mentionSpan, 
	    					  arcType);
	    	  arcs.add(arc);
	    	  
	    	  /*
	    	  System.out.println("animacy = "+m.animacy);
	    	  System.out.println("mention span = "+m.mentionSpan);
	    	  System.out.println(" id = "+m.mentionID);
	    	  System.out.println(" position = "+m.position);
	    	  System.out.println(" start index = "+m.startIndex);
	    	  System.out.println(" end index = "+m.endIndex);   
	    	  System.out.println(" mentionType = "+m.mentionType);   
	    	  System.out.println(" number =  = "+m.number);  
	    	  */
	    	  }
	      }
	      
	      
	    }
	    List<WordWordInterSentenceRelationArc> arcsCA = buildCAarcs(nodesThicket);
	    
	    ParseThicket result = new ParseThicket(ptTrees, arcs);
	    result.setNodesThicket(nodesThicket);
	    return result;
	}

  private List<WordWordInterSentenceRelationArc> buildCAarcs(
			List<List<ParseTreeNode>> nodesThicket) {
	  List<WordWordInterSentenceRelationArc> arcs = new ArrayList<WordWordInterSentenceRelationArc>();
	  
		for(int sentI=0; sentI<nodesThicket.size(); sentI++){
			for(int sentJ=sentI+1; sentJ<nodesThicket.size(); sentJ++){
				List<ParseTreeNode> sentenceI = nodesThicket.get(sentI), 
						sentenceJ = nodesThicket.get(sentJ);
				Pair<String, Integer[]> caI = caFinder.findCAInSentence(sentenceI);
				Pair<String, Integer[]> caJ = caFinder.findCAInSentence(sentenceJ);
				int indexCA1 = caFinder.findCAIndexInSentence(sentenceI);
				int indexCA2 = caFinder.findCAIndexInSentence(sentenceJ);
				if (caI==null || caJ==null)
					continue;
				Pair<String, Integer[]> caGen = caFinder.generalize(caI, caJ).get(0);
				
				ArcType arcType = new ArcType("ca", 
						caGen.getFirst().toString()+printNumArray(caGen.getSecond()), 0, 0);
				 WordWordInterSentenceRelationArc arc = 
		    			  new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(sentI,indexCA1), 
		    					  new Pair<Integer, Integer>(sentJ,indexCA2), caI.getFirst(), caJ.getFirst(), 
		    					  arcType);
		    	  arcs.add(arc);
				
			}
					}
		
		return arcs;
	}
  
    private String printNumArray(Integer[] arr){
    	StringBuffer buf = new StringBuffer();
    	for(Integer i: arr){
    		buf.append(Integer.toString(i)+ " ");
    	}
    	return buf.toString();
    }

public static void main(String[] args) throws IOException {
	  ParseCorefsBuilder builder = ParseCorefsBuilder.getInstance();
	  ParseThicket  th = builder.buildParseThicket("Iran refuses to accept the UN proposal to end its dispute over its work on nuclear weapons."+
    		  "UN nuclear watchdog passes a resolution condemning Iran for developing its second uranium enrichment site in secret. " +
    		  "A recent IAEA report presented diagrams that suggested Iran was secretly working on nuclear weapons. " +
    		  "Iran envoy says its nuclear development is for peaceful purpose, and the material evidence against it has been fabricated by the US. ");
    //GraphFromPTreeBuilder gbuilder = new GraphFromPTreeBuilder();
    //gbuilder.buildGraphFromPT(th);
	 
  }

}

/*
 * [<sent=1-word=1..Iran> ===> <sent=3-word=9..Iran>, <sent=1-word=1..Iran> ===> <sent=4-word=1..Iran>, <sent=1-word=1..Iran> ===> <sent=4-word=4..its>, <sent=1-word=1..Iran> ===> <sent=4-word=17..it>, <sent=3-word=9..Iran> ===> <sent=4-word=1..Iran>, <sent=3-word=9..Iran> ===> <sent=4-word=4..its>, <sent=3-word=9..Iran> ===> <sent=4-word=17..it>, <sent=4-word=1..Iran> ===> <sent=4-word=4..its>, <sent=4-word=1..Iran> ===> <sent=4-word=17..it>, <sent=4-word=4..its> ===> <sent=4-word=17..it>, <sent=1-word=6..UN> ===> <sent=2-word=1..UN>, <sent=1-word=5..the UN proposal> ===> <sent=1-word=10..its>, <sent=1-word=5..the UN proposal> ===> <sent=1-word=13..its>, <sent=1-word=10..its> ===> <sent=1-word=13..its>, <sent=1-word=16..nuclear weapons> ===> <sent=3-word=14..nuclear weapons>, <sent=2-word=1..UN nuclear watchdog> ===> <sent=2-word=11..its>]

[[[1]Iran:NNP>LOCATION, [2]refuses:VBZ>O, [3]to:TO>O, [4]accept:VB>O, [5]the:DT>O, [6]UN:NNP>ORGANIZATION, [7]proposal:NN>O, [8]to:TO>O, [9]end:VB>O, [10]its:PRP$>O, [11]dispute:NN>O, [12]over:IN>O, [13]its:PRP$>O, [14]work:NN>O, [15]on:IN>O, [16]nuclear:JJ>O, [17]weapons:NNS>O, [18].:.>O], 

[[1]UN:NNP>ORGANIZATION, [2]nuclear:JJ>O, [3]watchdog:NN>O, [4]passes:VBZ>O, [5]a:DT>O, [6]resolution:NN>O, [7]condemning:VBG>O, [8]Iran:NNP>LOCATION, [9]for:IN>O, [10]developing:VBG>O, [11]its:PRP$>O, [12]second:JJ>ORDINAL, [13]uranium:NN>O, [14]enrichment:NN>O, [15]site:NN>O, [16]in:IN>O, [17]secret:NN>O, [18].:.>O], 

[[1]A:DT>O, [2]recent:JJ>O, [3]IAEA:NNP>ORGANIZATION, [4]report:NN>O, [5]presented:VBD>O, [6]diagrams:NNS>O, [7]that:WDT>O, [8]suggested:VBD>O, [9]Iran:NNP>LOCATION, [10]was:VBD>O, [11]secretly:RB>O, [12]working:VBG>O, [13]on:IN>O, [14]nuclear:JJ>O, [15]weapons:NNS>O, [16].:.>O], 

[[1]Iran:NNP>LOCATION, [2]envoy:NN>O, [3]says:VBZ>O, [4]its:PRP$>O, [5]nuclear:JJ>O, [6]development:NN>O, [7]is:VBZ>O, [8]for:IN>O, [9]peaceful:JJ>O, [10]purpose:NN>O, [11],:,>O, [12]and:CC>O, [13]the:DT>O, [14]material:NN>O, [15]evidence:NN>O, [16]against:IN>O, [17]it:PRP>O, [18]has:VBZ>O, [19]been:VBN>O, [20]fabricated:VBN>O, [21]by:IN>O, [22]the:DT>O, [23]US:NNP>LOCATION, [24].:.>O]]
*/
