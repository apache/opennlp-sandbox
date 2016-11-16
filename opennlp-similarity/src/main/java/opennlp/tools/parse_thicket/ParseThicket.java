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
	
	private List<Float> sentimentProfile;
	
	private String origText;
	private List<List<ParseTreeNode>> phrases;
	
	
	public List<Tree> getSentenceTrees() {
		return sentenceTrees;
	}

	public void setSentenceTrees(List<Tree> sentenceTrees) {
		this.sentenceTrees = sentenceTrees;
	}

	public List<List<ParseTreeNode>> getSentenceNodes() {
		return sentenceNodes;
	}

	public void setSentenceNodes(List<List<ParseTreeNode>> sentenceNodes) {
		this.sentenceNodes = sentenceNodes;
	}

	public String getOrigText() {
		return origText;
	}

	public void setOrigText(String origText) {
		this.origText = origText;
	}

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

	public void setPhrases(List<List<ParseTreeNode>> phrs) {
		this.phrases = phrs;		
	}

	public List<List<ParseTreeNode>> getPhrases() {
		return phrases;
	}

	public List<Float> getSentimentProfile() {
		return sentimentProfile;
	}

	public void setSentimentProfile(List<Float> sentimentProfile) {
		this.sentimentProfile = sentimentProfile;
	}
	
	
	
}
