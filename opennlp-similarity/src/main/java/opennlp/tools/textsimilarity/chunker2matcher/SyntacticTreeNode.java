package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parser.AbstractBottomUpParser;

public abstract class SyntacticTreeNode {
	// the POS type
	private String type;

	// parent node, it is null for the root node
	private PhraseNode parentNode;

	public abstract List<SyntacticTreeNode> getChildren();

	public abstract String getText();

	public abstract String getLemma(boolean removeStopWord);

	public abstract String toStringIndented(int numTabs);

	public SyntacticTreeNode(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getLemma() {
		return getLemma(false);
	}

	public PhraseNode getParentNode() {
		return parentNode;
	}

	public void setParentNode(PhraseNode parentNode) {
		this.parentNode = parentNode;
	}

	public int getChildrenCount() {
		List<SyntacticTreeNode> childrenList = getChildren();
		if (childrenList == null)
			return 0;

		return childrenList.size();
	}

	public String toString() {
		return toStringIndented(0);
	}

	public static String getIndent(int numTabs) {
		if (numTabs <= 0)
			return "";

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < numTabs; i++) {
			builder.append("\t");
		}

		return builder.toString();
	}

	public static boolean isJunkType(String type) {
		if (type == null)
			return true;

		// the token node is useless
		if (type.equals(AbstractBottomUpParser.TOK_NODE))
			return true;

		// the punctuation nodes are not useful, '.', '.', '?', '!', ';', etc
		if (type.equals(",") || type.equals(".") || type.equals("?")
				|| type.equals("!") || type.equals(";"))
			return true;

		return false;
	}

	public static void replaceNode(SyntacticTreeNode nodeToReplace,
			SyntacticTreeNode newNode) {
		List<SyntacticTreeNode> newNodeList = null;
		if (newNode != null) {
			newNodeList = new ArrayList<SyntacticTreeNode>(1);
			newNodeList.add(newNode);
		}

		replaceNode(nodeToReplace, newNodeList);
	}

	public static void replaceNode(SyntacticTreeNode nodeToReplace,
			List<SyntacticTreeNode> newNodeList) {
		if (nodeToReplace == null)
			throw new NullPointerException("The node to replace cannot be null");

		PhraseNode parentNode = nodeToReplace.getParentNode();

		if (parentNode == null) {
			// the node to replace is the root node
			// clear all children of the existing root node and use it as the
			// new root node
			if (nodeToReplace instanceof PhraseNode)
				((PhraseNode) nodeToReplace).setChildren(newNodeList);
			return;
		}

		List<SyntacticTreeNode> childrenNodes = parentNode.getChildren();
		int index = childrenNodes.indexOf(nodeToReplace);
		if (index >= 0) {
			// remove the old node
			childrenNodes.remove(index);

			// put the new node list at the place of the old node if there are
			// any
			if (newNodeList != null && newNodeList.size() > 0) {
				childrenNodes.addAll(index, newNodeList);

				// set the parent node of the new children
				for (SyntacticTreeNode newNode : newNodeList) {
					newNode.setParentNode(parentNode);
				}
			}
		}
	}
}
