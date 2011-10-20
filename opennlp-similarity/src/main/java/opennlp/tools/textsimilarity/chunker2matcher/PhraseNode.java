package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.ArrayList;
import java.util.List;

public class PhraseNode extends SyntacticTreeNode {
	// children nodes within a phrase node
	private List<SyntacticTreeNode> children;

	public PhraseNode(String type, List<SyntacticTreeNode> children) {
		super(type);
		setChildren(children);
	}

	@Override
	public List<SyntacticTreeNode> getChildren() {
		return children;
	}

	public void setChildren(List<SyntacticTreeNode> children) {
		this.children = children;

		// set the parent of the children nodes
		if (children != null && children.size() > 0) {
			for (SyntacticTreeNode child : children) {
				if (child != null)
					child.setParentNode(this);
			}
		}
	}

	public void addChild(SyntacticTreeNode child) {
		if (child == null)
			return;

		if (children == null) {
			children = new ArrayList<SyntacticTreeNode>();
		}

		// set the parent of the child node
		child.setParentNode(this);

		children.add(child);
	}

	@Override
	public String getText() {
		return getText(false, false);
	}

	@Override
	public String getLemma(boolean removeStopWord) {
		return getText(true, removeStopWord);
	}

	private String getText(boolean useLemma, boolean removeStopWord) {
		if (children == null || children.size() == 0)
			return null;

		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (SyntacticTreeNode child : children) {
			String childText = null;
			if (useLemma)
				childText = child.getLemma(removeStopWord);
			else
				childText = child.getText();

			if (childText == null || childText.length() == 0)
				continue;

			// add a leading space for children other than the first
			if (first)
				first = false;
			else
				builder.append(" ");

			// add the child
			builder.append(childText);
		}

		return builder.toString();
	}

	@Override
	public String toStringIndented(int numTabs) {
		StringBuilder builder = new StringBuilder();

		String indent = SyntacticTreeNode.getIndent(numTabs);
		builder.append(indent).append("type = ").append(getType());

		// output all the children
		if (children != null && children.size() > 0) {
			numTabs++;
			for (SyntacticTreeNode child : children) {
				builder.append("\n").append(child.toStringIndented(numTabs));
			}
		}

		return builder.toString();
	}
}
