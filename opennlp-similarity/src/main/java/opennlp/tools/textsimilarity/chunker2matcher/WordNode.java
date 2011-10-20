package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.List;

public class WordNode extends SyntacticTreeNode {
	// the word in the sentence
	private String word;
	private String lemma;

	public WordNode(String type, String word) {
		super(type);

		setWord(word);
	}

	public String getWord() {
		return word;
	}

	public void setWord(String word) {
		this.word = word;

		// update lemma accordingly
		this.lemma = null;
		/*WordDictionary.getInstance().getLemmaOrWord(word,
				getType()); */
	}

	@Override
	public String getLemma(boolean removeStopWord) {
		if (removeStopWord) // && Feature.isStopWord(lemma, getType()))
			return null;

		return lemma;
	}

	@Override
	public List<SyntacticTreeNode> getChildren() {
		// a word node is a leaf and has no children
		return null;
	}

	@Override
	public String getText() {
		return word;
	}

	@Override
	public String toStringIndented(int numTabs) {
		String indent = SyntacticTreeNode.getIndent(numTabs);
		StringBuilder builder = new StringBuilder();
		builder.append(indent).append("type = ").append(getType())
				.append(", word = ").append(word);

		return builder.toString();
	}

	public static void main(String[] args) {
	}
}
