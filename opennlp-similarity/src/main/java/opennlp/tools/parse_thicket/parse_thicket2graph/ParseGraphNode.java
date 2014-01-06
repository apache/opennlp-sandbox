package opennlp.tools.parse_thicket.parse_thicket2graph;

import java.util.List;

import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.matching.PT2ThicketPhraseBuilder;


import edu.stanford.nlp.trees.Tree;

public class ParseGraphNode {
	 PT2ThicketPhraseBuilder phraseBuilder = new PT2ThicketPhraseBuilder();
	 
	private Tree tree;
	private String label;
	private List<List<ParseTreeNode>> ptNodes;
	
	
	
	public List<List<ParseTreeNode>> getPtNodes() {
		return ptNodes;
	}

	public ParseGraphNode(Tree tree, String label) {
		super();
		this.tree = tree;
		this.label = label;
		ptNodes =  phraseBuilder.buildPT2ptPhrasesForASentence(tree, null);
	}

	public Tree getTree() {
		return tree;
	}

	public void setTree(Tree tree) {
		this.tree = tree;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String toString(){
		return label;
	}
}
	
