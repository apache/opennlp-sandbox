/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.parse_thicket.parse_thicket2graph;

import java.io.PrintWriter;
import java.util.List;

import opennlp.tools.parse_thicket.PTTree;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;


import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;

public class GraphFromPTreeBuilder {
	
	
	public Graph<ParseGraphNode, DefaultEdge> buildGraphFromPT(ParseThicket pt){
		PrintWriter out = new PrintWriter(System.out);

		
		List<Tree> ts = pt.getSentences();
		ts.get(0).pennPrint(out);
		Graph<ParseGraphNode, DefaultEdge> gfragment = buildGGraphFromTree(ts.get(0));
		
		//ParseTreeVisualizer applet = new ParseTreeVisualizer();
		//applet.showGraph(gfragment);
		
		return gfragment;
		
	}
	
	
	private Graph<ParseGraphNode, DefaultEdge> buildGGraphFromTree(Tree tree) {
		Graph<ParseGraphNode, DefaultEdge> g =
				new SimpleGraph<ParseGraphNode, DefaultEdge>(DefaultEdge.class);
		ParseGraphNode root = new ParseGraphNode(tree,"S 0");
		g.addVertex(root);
		navigate(tree, g, 0, root);
	        
		return g;
	}



	private void navigate(Tree tree, Graph<ParseGraphNode, DefaultEdge> g, int l, ParseGraphNode currParent) {
		//String currParent = tree.label().value()+" $"+Integer.toString(l);
		//g.addVertex(currParent);
		if (tree.getChildrenAsList().size()==1)
			navigate(tree.getChildrenAsList().get(0), g, l+1, currParent);
		else
			if (tree.getChildrenAsList().size()==0)
				return;
		
		for(Tree child: tree.getChildrenAsList()){
			String currChild = null;
			ParseGraphNode currChildNode = null;
			try {
				if (child.isLeaf()) 
					continue;
				if (child.label().value().startsWith("S"))
					navigate(child.getChildrenAsList().get(0), g, l+1, currParent);
				
				if (!child.isPhrasal() || child.isPreTerminal())
					currChild = child.toString()+" #"+Integer.toString(l);
				else 
					currChild = child.label().value()+" #"+Integer.toString(l);
				currChildNode = new ParseGraphNode(child, currChild);
				g.addVertex(currChildNode);
				g.addEdge(currParent, currChildNode);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			navigate(child, g, l+1, currChildNode);
		}
	}


	/*
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
	  */

}
