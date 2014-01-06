package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.rhetoric_structure.RhetoricStructureArcsBuilder;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;


import edu.stanford.nlp.trees.Tree;

public class PT2ThicketPhraseBuilder {
	
	RhetoricStructureArcsBuilder rstBuilder = new RhetoricStructureArcsBuilder();
	
	/*
	 * Building phrases takes a Parse Thicket and forms phrases for each sentence individually
	 * Then based on built phrases and obtained arcs, it builds arcs for RST
	 * Finally, based on all formed arcs, it extends phrases with thicket phrases
	 */

	public List<List<ParseTreeNode>> buildPT2ptPhrases(ParseThicket pt ) {
		List<List<ParseTreeNode>> phrasesAllSent = new ArrayList<List<ParseTreeNode>> ();
		Map<Integer, List<List<ParseTreeNode>>> sentNumPhrases = new HashMap<Integer, List<List<ParseTreeNode>>>();
		// build regular phrases
		for(int nSent=0; nSent<pt.getSentences().size(); nSent++){
			
			
			List<ParseTreeNode> sentence = pt.getNodesThicket().get(nSent);
			Tree ptree = pt.getSentences().get(nSent);
			//ptree.pennPrint();
			List<List<ParseTreeNode>> phrases = buildPT2ptPhrasesForASentence(ptree, sentence);
			System.out.println(phrases);
			phrasesAllSent.addAll(phrases);
			sentNumPhrases.put(nSent, phrases);

		}
		
		// discover and add RST arcs
		List<WordWordInterSentenceRelationArc> arcsRST =
				rstBuilder.buildRSTArcsFromMarkersAndCorefs(pt.getArcs(), sentNumPhrases, pt);
		
		List<WordWordInterSentenceRelationArc> arcs = pt.getArcs();
		arcs.addAll(arcsRST);
		pt.setArcs(arcs);
		
		
		List<List<ParseTreeNode>> expandedPhrases = expandTowardsThicketPhrases(phrasesAllSent, pt.getArcs(), sentNumPhrases, pt);
		return expandedPhrases;
	}

/* Take all phrases, all arcs and merge phrases into Thicket phrases.
 * Then add the set of generalized (Thicket) phrases to the input set of phrases
 * phrasesAllSent - list of lists of phrases for each sentence
 * sentNumPhrase - map , gives for each sentence id, the above list
 * arcs - arcs formed so far
 * pt - the built Parse Thicket
 */
	private List<List<ParseTreeNode>> expandTowardsThicketPhrases(
			List<List<ParseTreeNode>> phrasesAllSent,
			List<WordWordInterSentenceRelationArc> arcs,
			Map<Integer, List<List<ParseTreeNode>>> sentNumPhrases, 
			ParseThicket pt ) {
		List<List<ParseTreeNode>> thicketPhrasesAllSent = new ArrayList<List<ParseTreeNode>>();
		
		
			for(int nSent=0; nSent<pt.getSentences().size(); nSent++){
				for(int mSent=nSent+1; mSent<pt.getSentences().size(); mSent++){
					// for given arc, find phrases connected by this arc and add to the list of phrases
					for(WordWordInterSentenceRelationArc arc: arcs){
						List<List<ParseTreeNode>> phrasesFrom = sentNumPhrases.get(nSent);
						List<List<ParseTreeNode>> phrasesTo = sentNumPhrases.get(mSent);
						int fromIndex = arc.getCodeFrom().getFirst();
						int toIndex = arc.getCodeTo().getFirst();
						if (nSent==fromIndex && mSent==toIndex){
							int sentPosFrom = arc.getCodeFrom().getSecond();
							int sentPosTo = arc.getCodeTo().getSecond();
							// for the given arc arc, find phrases which are connected by it
							List<ParseTreeNode> lFromFound = null, lToFound = null;
							for(List<ParseTreeNode> lFrom: phrasesFrom){
								if (lToFound!=null)
									break;
								for(ParseTreeNode lFromP: lFrom){
									if (lFromP.getId()!=null &&  lFromP.getId()==sentPosFrom){
											lFromFound = lFrom;
											break;
										}
								}
							}
							for(List<ParseTreeNode> lTo: phrasesTo){
								if (lToFound!=null)
									break;
								for(ParseTreeNode lToP: lTo)
									if (lToP.getId()!=null && lToP.getId()==sentPosTo){
										lToFound = lTo;
										break;
									}
							}
							// obtain a thicket phrase and add it to the list
							if (lFromFound!=null && lToFound!=null){
								
								if (identicalSubPhrase(lFromFound, lToFound))
									continue;
								List<ParseTreeNode> appended = append(lFromFound, lToFound);
								if (thicketPhrasesAllSent.contains(appended))
									continue;
								System.out.println("rel: "+arc);
								System.out.println("From "+lFromFound);
								System.out.println("TO "+lToFound);
								thicketPhrasesAllSent.add(append(lFromFound, lToFound));	
								//break;
							}
						}
						
					}
				}
			}
			phrasesAllSent.addAll(thicketPhrasesAllSent);
			return phrasesAllSent;
	}

/* check that one phrase is subphrase of another by lemma (ignoring other node properties)
 * returns true if not found different word
 */
	
	private boolean identicalSubPhrase(List<ParseTreeNode> lFromFound,
			List<ParseTreeNode> lToFound) {
		for(int pos=0; pos<lFromFound.size()&& pos<lToFound.size(); pos++){
			if (!lFromFound.get(pos).getWord().equals(lToFound.get(pos).getWord()))
				return false;
		}
		return true;
	}

	private List<ParseTreeNode> append(List<ParseTreeNode> lFromFound,
			List<ParseTreeNode> lToFound) {
		List<ParseTreeNode> appendList = new ArrayList<ParseTreeNode>();
		appendList.addAll(lFromFound);
		appendList.addAll(lToFound);
		return appendList;
	}


	public List<List<ParseTreeNode>> buildPT2ptPhrasesForASentence(Tree tree, List<ParseTreeNode> sentence ) {
		List<List<ParseTreeNode>> phrases;

		phrases = new ArrayList<List<ParseTreeNode>>();		
		navigateR(tree, sentence, phrases);

		return phrases;
	}


	

/*
 * 
[[<1>NP'Iran':NNP], [<2>VP'refuses':VBZ, <3>VP'to':TO, <4>VP'accept':VB, <5>VP'the':DT, <6>VP'UN':NNP, 
<7>VP'proposal':NN, <8>VP'to':TO, <9>VP'end':VB, <10>VP'its':PRP$, <11>VP'dispute':NN, <12>VP'over':IN, <13>VP'its':PRP$,
 <14>VP'work':NN, <15>VP'on':IN, <16>VP'nuclear':JJ, <17>VP'weapons':NNS], [<3>VP'to':TO, <4>VP'accept':VB, <5>VP'the':DT,
  <6>VP'UN':NNP, <7>VP'proposal':NN, <8>VP'to':TO, <9>VP'end':VB, <10>VP'its':PRP$, <11>VP'dispute':NN, <12>VP'over':IN, 
  <13>VP'its':PRP$, <14>VP'work':NN, <15>VP'on':IN, <16>VP'nuclear':JJ, <17>VP'weapons':NNS], [<4>VP'accept':VB, 
  <5>VP'the':DT, <6>VP'UN':NNP, <7>VP'proposal':NN, <8>VP'to':TO, <9>VP'end':VB, <10>VP'its':PRP$, <11>VP'dispute':NN, 
  <12>VP'over':IN, <13>VP'its':PRP$, <14>VP'work':NN, <15>VP'on':IN, <16>VP'nuclear':JJ, <17>VP'weapons':NNS], 
  [<5>NP'the':DT, <6>NP'UN':NNP, <7>NP'proposal':NN], [<8>VP'to':TO, <9>VP'end':VB, <10>VP'its':PRP$, <11>VP'dispute':NN, 
  <12>VP'over':IN, <13>VP'its':PRP$, <14>VP'work':NN, <15>VP'on':IN, <16>VP'nuclear':JJ, <17>VP'weapons':NNS], 
  [<9>VP'end':VB, <10>VP'its':PRP$, <11>VP'dispute':NN, <12>VP'over':IN, <13>VP'its':PRP$, <14>VP'work':NN, <15>VP'on':IN,
   <16>VP'nuclear':JJ, <17>VP'weapons':NNS], [<10>NP'its':PRP$, <11>NP'dispute':NN], [<12>PP'over':IN, <13>PP'its':PRP$, 
   <14>PP'work':NN, <15>PP'on':IN, <16>PP'nuclear':JJ, <17>PP'weapons':NNS], [<13>NP'its':PRP$, <14>NP'work':NN, 
   <15>NP'on':IN, <16>NP'nuclear':JJ, <17>NP'weapons':NNS], [<13>NP'its':PRP$, <14>NP'work':NN],
 [<15>PP'on':IN, <16>PP'nuclear':JJ, <17>PP'weapons':NNS], [<16>NP'nuclear':JJ, <17>NP'weapons':NNS]]
 *  
 * 
 */
	private void navigateR(Tree t, List<ParseTreeNode> sentence,
			List<List<ParseTreeNode>> phrases) {
		if (!t.isPreTerminal()) {
			if (t.label() != null) {
				if (t.value() != null) {
					// if ROOT or S, returns empty
					List<ParseTreeNode> nodes = parsePhrase(t.label().value(), t.toString());
					nodes = assignIndexToNodes(nodes, sentence);
					if (!nodes.isEmpty())
						phrases.add(nodes);
					if (nodes.size()>0 && nodes.get(0).getId()==null){
							System.err.println("Failed alignment:"+nodes);
					}
				}
			}
			Tree[] kids = t.children();
			if (kids != null) {
				for (Tree kid : kids) {
					navigateR(kid,sentence,  phrases);
				}
			}
			return ;
		}
	}
	
	
	/* alignment of phrases extracted from tree against the sentence as a list of lemma-pos */
	
	private List<ParseTreeNode> assignIndexToNodes(List<ParseTreeNode> node,
			List<ParseTreeNode> sentence) {
		if (sentence==null || sentence.size()<1)
			return node;
		
		List<ParseTreeNode> results = new ArrayList<ParseTreeNode>();
		
		for(int i= 0; i<node.size(); i++){
			String thisLemma = node.get(i).getWord();			
			String thisPOS = node.get(i).getPos();
			String nextLemma = null, nextPOS = null;
			
			if (i+1<node.size()){
				nextLemma = node.get(i+1).getWord();
				nextPOS = node.get(i+1).getPos();
			}
			Boolean matchOccurred = false;
			int j = 0;
			for(j= 0; j<sentence.size(); j++){
				if (!(sentence.get(j).getWord().equals(thisLemma) && (sentence.get(j).getPos().equals(thisPOS))))
					continue;
				if (i+1<node.size() && j+1 < sentence.size() && nextLemma!=null 
						&& ! (sentence.get(j+1).getWord().equals(nextLemma)
					  && sentence.get(j+1).getPos().equals(nextPOS)))
					continue;
				matchOccurred = true;
				break;
			}
			
			ParseTreeNode n = node.get(i);
			if (matchOccurred){
				n.setId(sentence.get(j).getId());
				n.setNe(sentence.get(j).getNe());
			}
			results.add(n);
		}
		
		try {
			if (results!=null && results.size()>1 && results.get(0)!=null && results.get(0).getId()!=null &&
					results.get(1) !=null && results.get(1).getId()!=null &&  results.get(1).getId()>0){
				ParseTreeNode p = results.get(0);
				p.setId(results.get(1).getId()-1);
				results.set(0, p);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return results;
	}


	/*
	 * [[NP'':], ['(NNP':Iran)], [VP'':], ['(VBZ':refuses)], [VP'':], ['(TO':to)], [VP'':], ['(VB':accept)], [NP'':], 
	 * ['(DT':the)], ['(NNP':UN)], ['(NN':proposal)], [VP'':], ['(TO':to)], [VP'':], ['(VB':end)], [NP'':], 
	 * ['(PRP$':its)], ['(NN':dispute)], [PP'':], ['(IN':over)], [NP'':], [NP'':],
	 *  ['(PRP$':its)], ['(NN':work)], [PP'':], ['(IN':on)], [NP'':], ['(JJ':nuclear)], ['(NNS':weapons)], ['(.':.)]]
	 * 
	 * [[NP'':], ['(NNP':Iran)],
 [VP'':], ['(VBZ':refuses)], 
 [VP'':], ['(TO':to)], 
 [VP'':], ['(VB':accept)], 
    [NP'':], ['(DT':the)], ['(NNP':UN)], ['(NN':proposal)], 
    [VP'':], ['(TO':to)], [VP'':], ['(VB':end)], 
    [NP'':], ['(PRP$':its)], ['(NN':dispute)], 
        [PP'':], ['(IN':over)], 
            [NP'':], [NP'':], ['(PRP$':its)], ['(NN':work)], 
              [PP'':], ['(IN':on)], 
                [NP'':], ['(JJ':nuclear)], ['(NNS':weapons)], 
['(.':.)]]
	 */
	private void navigateR1(Tree t, List<ParseTreeNode> sentence, int l,
			List<List<ParseTreeNode>> phrases) {
		if (t.isPreTerminal()) {
			if (t.label() != null) {
				List<ParseTreeNode> node = parsePhrase(t.toString());	
				if (!node.isEmpty())
					phrases.add(node);
			}
			return;
		} else {
			if (t.label() != null) {
				if (t.value() != null) {
					List<ParseTreeNode> node = parsePhrase(t.label().value());		 
					if (!node.isEmpty())
						phrases.add(node);
				}
			}
			Tree[] kids = t.children();
			if (kids != null) {
				for (Tree kid : kids) {
					navigateR1(kid,sentence,  l, phrases);
				}
			}
			return ;
		}
	}


	protected List<ParseTreeNode> parsePhrase(String value) {
		List<ParseTreeNode> nlist = new ArrayList<ParseTreeNode>(); 
		if (value==null)
			return nlist;
		if (value.equals("ROOT")|| value.equals("S")) 
			return nlist;
		
		String[] pos_value = value.split(" ");
		ParseTreeNode node = null;
		if (value.endsWith("P")){
			node = new ParseTreeNode("", ""); 
		    node.setPhraseType(value);
		} else 
		if (pos_value != null && pos_value.length==2){
			node = new ParseTreeNode(pos_value[0], pos_value[1]);
		} else {
			node = new ParseTreeNode(value, "");
		}
			
		nlist.add(node);
		return nlist;
	}
	
	private ParseTreeNode parsePhraseNode(String value) {
		
		if (value.equals("ROOT")|| value.equals("S")) 
			return null;
		
		String[] pos_value = value.split(" ");
		ParseTreeNode node = null;
		if (value.endsWith("P")){
			node = new ParseTreeNode("", ""); 
		    node.setPhraseType(value);
		} else 
		if (pos_value != null && pos_value.length==2){
			node = new ParseTreeNode(pos_value[0], pos_value[1]);
		} else {
			node = new ParseTreeNode(value, "");
		}			
		
		return node;
	}
	
	public List<ParseTreeNode> parsePhrase(String value, String fullDump) {
		
		List<ParseTreeNode> nlist = new ArrayList<ParseTreeNode>(); 
		if (value.equals("S")|| value.equals("ROOT"))
				return nlist;
		
		String flattened = fullDump.replace("(ROOT","").replace("(NP","").replace("(VP","").replace("(PP","")
				.replace("(ADVP","").replace("(UCP","").replace("(ADJP","").replace("(SBAR","").
				replace("(PRT", "").replace("(WHNP","").
				 replace("))))",")").replace(")))",")").replace("))",")")
				.replace("   ", " ").replace("  ", " ").replace("(S","")
				.replace(") (","#").replace(")  (", "#");
		String[] flattenedArr =  flattened.split("#");
		for(String term: flattenedArr){
			term = term.replace('(', ' ').replace(')',' ').trim();
			if (term!=null && term.split(" ")!=null && term.split(" ").length==2){
				ParseTreeNode node = new ParseTreeNode(term.split(" ")[1],term.split(" ")[0] );
				node.setPhraseType(value);
				nlist.add(node);
			}
		}
		return nlist;
	}
	
/* recursion example */
	
	private StringBuilder toStringBuilder(StringBuilder sb, Tree t) {
		if (t.isLeaf()) {
			if (t.label() != null) {
				sb.append(t.label().value());
			}
			return sb;
		} else {
			sb.append('(');
			if (t.label() != null) {
				if (t.value() != null) {
					sb.append(t.label().value());
				}
			}
			Tree[] kids = t.children();
			if (kids != null) {
				for (Tree kid : kids) {
					sb.append(' ');
					toStringBuilder(sb, kid);
				}
			}
			return sb.append(')');
		}
	}
	
	public static void main(String[] args){
		PT2ThicketPhraseBuilder phraseBuilder = new PT2ThicketPhraseBuilder();
		String line = "(NP (NNP Iran)) (VP (VBZ refuses) (S (VP (TO to) (VP (VB accept) (S (NP (DT the) " +
				"(NNP UN) (NN proposal)) (VP (TO to) (VP (VB end) (NP (PRP$ its) (NN dispute))))))))";
		
		List<ParseTreeNode> res = phraseBuilder. parsePhrase("NP", line);
		System.out.println(res);
		

		line = "(VP (VBP am) (NP (NP (DT a) (NNP US) (NN citizen)) (UCP (VP (VBG living) (ADVP (RB abroad))) (, ,) (CC and) (ADJP (JJ concerned) (PP (IN about) (NP (NP (DT the) (NN health) (NN reform) (NN regulation)) (PP (IN of) (NP (CD 2014)))))))))";
		res = phraseBuilder. parsePhrase("VP", line);
		System.out.println(res);
				
		line = "(VP (TO to) (VP (VB wait) (SBAR (IN till) (S (NP (PRP I)) (VP (VBP am) (ADJP (JJ sick) (S (VP (TO to) (VP (VB buy) (NP (NN health) (NN insurance)))))))))))";
		res = phraseBuilder. parsePhrase("VP", line);
		System.out.println(res);
	}
  
}
