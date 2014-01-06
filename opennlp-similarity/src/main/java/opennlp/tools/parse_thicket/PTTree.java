package opennlp.tools.parse_thicket;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.SimpleTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;



public class PTTree extends SimpleTree {
	
	public PTTree(){
		super();
	}

	public PTTree(Tree t){
		super();
	}
	private static final long serialVersionUID = 1L;

	@Override
	public PTTree[] children() {
		return children();
	}

	@Override
	public TreeFactory treeFactory() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void doNavigate(){
		List<LabeledScoredTreeNode> phrases = new ArrayList<LabeledScoredTreeNode>();
		navigate(0, false, false, false, true, true, phrases);
	}
	
	private static void navigateChildren(PTTree[] trChildren, int indent, boolean parentLabelNull, boolean onlyLabelValue, List<LabeledScoredTreeNode> phrases) {
	    boolean firstSibling = true;
	    boolean leftSibIsPreTerm = true;  // counts as true at beginning
	    for (PTTree currentTree : trChildren) {
	      currentTree.navigate(indent, parentLabelNull, firstSibling, leftSibIsPreTerm, false, onlyLabelValue, phrases);
	      leftSibIsPreTerm = currentTree.isPreTerminal();
	      // CC is a special case for English, but leave it in so we can exactly match PTB3 tree formatting
	      if (currentTree.value() != null && currentTree.value().startsWith("CC")) {
	        leftSibIsPreTerm = false;
	      }
	      firstSibling = false;
	    }
	  }
	
	/**
	   * navigate parse tree
	   */
	  private void navigate(int indent, boolean parentLabelNull, boolean firstSibling, boolean leftSiblingPreTerminal, boolean topLevel, boolean onlyLabelValue, List<LabeledScoredTreeNode> phrases) {
	    // the condition for staying on the same line in Penn Treebank
	    boolean suppressIndent = (parentLabelNull || (firstSibling && isPreTerminal()) || (leftSiblingPreTerminal && isPreTerminal() && (label() == null || !label().value().startsWith("CC"))));
	    if (suppressIndent) {
	      //pw.print(" ");
	      // pw.flush();
	    } else {
	      if (!topLevel) {
	        //pw.println();
	      }
	      for (int i = 0; i < indent; i++) {
	        //pw.print("  ");
	        // pw.flush();
	      }
	    }
	    if (isLeaf() || isPreTerminal()) {
	      String terminalString = toStringBuilder(new StringBuilder(), onlyLabelValue).toString();
	      //pw.print(terminalString);
	      //pw.flush();
	      return;
	    }
	    //pw.print("(");
	    String nodeString = onlyLabelValue ? value() : nodeString();
	    //pw.print(nodeString);
	    // pw.flush();
	    boolean parentIsNull = label() == null || label().value() == null;
	    navigateChildren(children(), indent + 1, parentIsNull, true, phrases);
	    //pw.print(")");
	    
	  }

}
