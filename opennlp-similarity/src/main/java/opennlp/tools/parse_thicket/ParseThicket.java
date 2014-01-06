package opennlp.tools.parse_thicket;

import java.util.List;

import edu.stanford.nlp.trees.Tree;

public class ParseThicket {
	// parse trees 
	private List<Tree> sentenceTrees;
	// there should be an arc for each sentence
	private List<WordWordInterSentenceRelationArc> arcs;
	// lists of nodes for each sentence
	// then list for all sentences
	private List<List<ParseTreeNode>> sentenceNodes;
	
	public List<Tree> getSentences() {
		return sentenceTrees;
	}

	public void setSentences(List<Tree> sentences) {
		this.sentenceTrees = sentences;
	}

	public List<WordWordInterSentenceRelationArc> getArcs() {
		return arcs;
	}

	public void setArcs(List<WordWordInterSentenceRelationArc> arcs) {
		this.arcs = arcs;
	}

	public List<List<ParseTreeNode>> getNodesThicket() {
		return sentenceNodes;
	}

	public void setNodesThicket(List<List<ParseTreeNode>> nodesThicket) {
		this.sentenceNodes = nodesThicket;
	}

	public ParseThicket(String paragraph){
		ParseCorefsBuilder builder = ParseCorefsBuilder.getInstance();
		ParseThicket res = builder.buildParseThicket(paragraph);
		this.sentenceTrees= res.sentenceTrees;
		this.arcs = res.arcs;		
	}

	public ParseThicket(List<Tree> ptTrees,
			List<WordWordInterSentenceRelationArc> barcs) {
		this.sentenceTrees= ptTrees;
		this.arcs = barcs;				
	}
	
	public String toString(){
		return this.sentenceTrees+"\n"+this.arcs;
	}
	
	
	
}
