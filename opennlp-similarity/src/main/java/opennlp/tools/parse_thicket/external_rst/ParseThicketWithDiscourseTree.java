package opennlp.tools.parse_thicket.external_rst;

import java.util.List;

import edu.arizona.sista.discourse.rstparser.DiscourseTree;
import edu.stanford.nlp.trees.Tree;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.kernel_interface.TreeExtenderByAnotherLinkedTree;

/*
 * This is subclass of ParseThicket with the focus on Discourse Tree
 * It produces a representation of discourse tree for tree kernel learning
 */

public class ParseThicketWithDiscourseTree extends ParseThicket {
	private DiscourseTree dt;
	private String dtDump;
	private String dtDumpWithPOS;
	private String dtDumpWithEmbeddedTrees;
	private String dtDumpWithVerbNet;

	private TreeExtenderByAnotherLinkedTree extender = new TreeExtenderByAnotherLinkedTree();
	private VerbNetProcessor verbBuilder = VerbNetProcessor.getInstance(null);

	public DiscourseTree getDt() {
		return dt;
	}
	// sets the highest level DT (under further iterations does not set anything)
	public void setDt(DiscourseTree dt) {
		if (this.dt==null)
			this.dt = dt;
	}

	public ParseThicketWithDiscourseTree(List<Tree> ptTrees, List<WordWordInterSentenceRelationArc> barcs) {
		super(ptTrees, barcs);
	}

	public void setDtDump(){
		StringBuilder sb = new StringBuilder(100000);
		StringBuilder res = toStringBuilderDTWithPOSSeq(sb, this.dt);
		dtDumpWithPOS = res.toString();

		sb = new StringBuilder(100000);
		res = toStringBuilderDT(sb, this.dt);
		dtDump = res.toString();

		sb = new StringBuilder(100000);
		res = toStringBuilderDTWithEmbeddedTrees(sb, this.dt);
		dtDumpWithEmbeddedTrees = res.toString();

		sb = new StringBuilder(100000);
		res = toStringBuilderDTWithVerbNet(sb, this.dt);
		dtDumpWithVerbNet = res.toString();
	}
	// basic representation of discourse tree 
	private StringBuilder toStringBuilderDT(StringBuilder sb, DiscourseTree dt) {
		if (dt.isTerminal()) {
			if (dt.relationLabel() != null) {
				sb.append(dt.relationLabel());
				//sb.append("("+dt.rawText()+")");
				scala.collection.mutable.StringBuilder sbs = new scala.collection.mutable.StringBuilder(100);

				dt.print(sbs, 0, false, true);
				String text  =  sbs.replaceAllLiterally("Nucleus TEXT:", "(");
				text = text.substring(0, text.length()-1)+")";
				sb.append(text);
			}
			return sb;
		} else {
			sb.append('(');
			if (dt.relationLabel() != null) {
				sb.append(dt.relationLabel());
			}
			DiscourseTree[] kids = dt.children();
			if (kids != null) {
				for (DiscourseTree kid : kids) {
					sb.append(' ');
					toStringBuilderDT(sb, kid);
				}
			}
			return sb.append(')');
		}
	}

	private StringBuilder toStringBuilderDTWithPOSSeq(StringBuilder sb, DiscourseTree dt) {
		if (dt.isTerminal()) {
			if (dt.relationLabel() != null && dt.relationLabel().length()>2) {
				sb.append(dt.relationLabel());
				// different StrBuilder for trees from scala
				scala.collection.mutable.StringBuilder sbs = new scala.collection.mutable.StringBuilder(100);
				dt.print(sbs, 0, false, true);
				String text  =  sbs.replaceAllLiterally("Nucleus TEXT:", "");
				//text = text.substring(0, text.length()-1)+"";
				String textDump = substituteTextWithPOStext(text, this.getNodesThicket().get(dt.firstToken().copy$default$1()));
				sb.append(textDump);
			}
			return sb;
		} else {
			sb.append('(');
			if (dt.relationLabel() != null) {
				sb.append(dt.relationLabel());
			}
			DiscourseTree[] kids = dt.children();
			if (kids != null) {
				for (DiscourseTree kid : kids) {
					sb.append(' ');
					toStringBuilderDTWithPOSSeq(sb, kid);
				}
			}
			return sb.append(')');
		}
	}

	private String substituteTextWithPOStext(String text, List<ParseTreeNode> list) {
		boolean bMatch = false;
		String[] tokens = text.split(" ");
		for(int offset = 0; offset<list.size(); offset++ ){	    	
			List<ParseTreeNode> subList = list.subList(offset, tokens.length+offset);
			int count = 0;
			bMatch = true; // if at least one mismatch
			for(ParseTreeNode n: subList){
				if (!n.getWord().equals(tokens[count])){
					bMatch = false;
					break;
				} else 
					count++;
				if (count>3)
					break;
			}
			if (bMatch){
				return //"(" + 
						ParseTreeNode.toTreeRepresentationString(subList); // + ")";
			}
		}
		return null;
	}

	private StringBuilder toStringBuilderDTWithEmbeddedTrees(StringBuilder sb, DiscourseTree dt) {
		if (dt.isTerminal()) {
			if (dt.relationLabel() != null && dt.relationLabel().length()>2) {
				sb.append(dt.relationLabel());
				//sb.append("("+dt.rawText()+")");
				scala.collection.mutable.StringBuilder sbs = new scala.collection.mutable.StringBuilder(100);

				dt.print(sbs, 0, false, true);
				String text  =  sbs.replaceAllLiterally("Nucleus TEXT:", "");
				//text = text.substring(0, text.length()-1)+"";
				substituteTextWithParseTree(sb, text, this.getSentenceTrees().get(dt.firstToken().copy$default$1()));
			}
			return sb;
		} else {
			sb.append('(');
			if (dt.relationLabel() != null) {
				sb.append(dt.relationLabel());
			}
			DiscourseTree[] kids = dt.children();
			if (kids != null) {
				for (DiscourseTree kid : kids) {
					sb.append(' ');
					toStringBuilderDTWithEmbeddedTrees(sb, kid);
				}
			}
			return sb.append(')');
		}
	}
	private void substituteTextWithParseTree(StringBuilder sb, String text, Tree sentenceTree) {
		String[] tokens = text.split(" ");
		List<Tree> foundTrees = null;
		if (tokens.length>1){
			foundTrees = 
					extender.getASubtreeWithRootAsNodeForWord1(sentenceTree, sentenceTree, new String[]{tokens[0], tokens[1]});
		}
		else{
			foundTrees = 
					extender.getASubtreeWithRootAsNodeForWord1(sentenceTree, sentenceTree, new String[]{tokens[0]});

		}

		if (foundTrees == null || foundTrees.size()<1)
			return;

		extender.toStringBuilder(sb, foundTrees.get(0));

	}

	private StringBuilder toStringBuilderDTWithVerbNet(StringBuilder sb, DiscourseTree dt) {
		if (dt.isTerminal()) {
			if (dt.relationLabel() != null && dt.relationLabel().length()>2) {
				sb.append(dt.relationLabel());
				//sb.append("("+dt.rawText()+")");
				scala.collection.mutable.StringBuilder sbs = new scala.collection.mutable.StringBuilder(100);

				dt.print(sbs, 0, false, true);
				String text  =  sbs.replaceAllLiterally("Nucleus TEXT:", "");
				String textDump = null;
				if (text.split(" ").length<100) // if not TOO long, more informative substitution, including VerbNets
					textDump = substituteTextWithPOStextVerbNet(text, this.getNodesThicket().get(dt.firstToken().copy$default$1()));
				else // otherwise just lemma-POS chains
					textDump = substituteTextWithPOStext(text, this.getNodesThicket().get(dt.firstToken().copy$default$1()));
				
					
				sb.append(textDump);
			}
			return sb;
		} else {
			sb.append('(');
			if (dt.relationLabel() != null) {
				sb.append(dt.relationLabel());
			}
			DiscourseTree[] kids = dt.children();
			if (kids != null) {
				for (DiscourseTree kid : kids) {
					sb.append(' ');
					toStringBuilderDTWithVerbNet(sb, kid);
				}
			}
			return sb.append(')');
		}
	}

	// substitutes lemma-POS pair instead of just lemma
	// in case of verb provides moe detailed info
	private String substituteTextWithPOStextVerbNet(String text, List<ParseTreeNode> list) {
		boolean bMatch = false;
		String[] tokens = text.split(" ");
		for(int offset = 0; offset<list.size(); offset++ ){	    	
			List<ParseTreeNode> subList = list.subList(offset, tokens.length+offset);
			int count = 0;
			bMatch = true; // if at least one mismatch
			for(ParseTreeNode n: subList){
				if (!n.getWord().equals(tokens[count])){
					bMatch = false;
					break;
				} else 
					count++;
				if (count>3) // three tokens is enough for alignment
					break;
			}
			// alignment found; now 
			if (bMatch){
				StringBuilder buf = new StringBuilder();
				for(ParseTreeNode ch: subList){
					try {
	                    if (ch.getPos().startsWith(".") || ch.getPos().startsWith(",") || ch.getPos().startsWith(";") || ch.getPos().startsWith("!"))
	                    	continue;
	                    if (ch.getPos().startsWith("VB") && ch.getNormalizedWord()!=null){ // do more info for verbs
	                    	StringBuilder verbRepr = verbBuilder.
	                    			buildTreeRepresentationForTreeKernelLearning(ch.getNormalizedWord());
	                    	if (verbRepr!=null)
	                    		buf.append(" ("+verbRepr+") ");
	                    	else
	                    		buf.append( "("+ch.getWord()+ " " + ch.getPos() + ")" );
	                    } else { // other than verb
	                    	buf.append( "("+ch.getWord()+ " " + ch.getPos() + ")" );
	                    }
                    } catch (Exception e) {
	                    e.printStackTrace();
                    }
				}
				return buf.toString().trim();
			}
		}
		return null;
	}

	public String getDtDump() {
		return this.dtDump;
	}
	public String getDtDumpWithPOS() {
		return this.dtDumpWithPOS;
	}

	public String getDtDumpWithEmbeddedTrees() {
		return this.dtDumpWithEmbeddedTrees;
	}
	
	public String getDtDumpWithVerbNet() {
		return this.dtDumpWithVerbNet;
	}
}
