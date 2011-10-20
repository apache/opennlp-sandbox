package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.List;

/**
 * Sentence node is the first clause node contained in the top node
 * 
 */
public class SentenceNode extends PhraseNode {
	private String sentence;

	public SentenceNode(String sentence, List<SyntacticTreeNode> children) {
		super(ParserConstants.TYPE_S, children);

		this.sentence = sentence;
	}

	@Override
	public String getText() {
		return sentence;
	}

	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	@Override
	public String toStringIndented(int numTabs) {
		StringBuilder builder = new StringBuilder();
		String indent = SyntacticTreeNode.getIndent(numTabs);

		// output the sentence
		builder.append(indent).append(sentence).append("\n");
		builder.append(super.toStringIndented(numTabs));

		return builder.toString();
	}
}
