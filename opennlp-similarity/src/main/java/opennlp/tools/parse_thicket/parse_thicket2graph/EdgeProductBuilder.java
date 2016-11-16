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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import opennlp.tools.parse_thicket.ParseCorefsBuilder;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.matching.Matcher;
import opennlp.tools.textsimilarity.ParseTreeChunk;

import org.jgrapht.Graph;
import org.jgrapht.alg.BronKerboschCliqueFinder;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;


public class EdgeProductBuilder {
	private Matcher matcher = new Matcher();
	private ParseCorefsBuilder ptBuilder = ParseCorefsBuilder.getInstance();
	private GraphFromPTreeBuilder graphBuilder = new GraphFromPTreeBuilder();
	
	
	public Graph<ParseGraphNode[], DefaultEdge>  
		buildEdgeProduct(Graph<ParseGraphNode, DefaultEdge> g1, Graph<ParseGraphNode, DefaultEdge> g2 ){
			Graph<ParseGraphNode[], DefaultEdge> gp = 
				new SimpleGraph<ParseGraphNode[], DefaultEdge>(DefaultEdge.class);
		
		Set<DefaultEdge> edges1 = g1.edgeSet();
		Set<DefaultEdge> edges2 = g2.edgeSet();
		// build nodes of product graph
		for(DefaultEdge e1:edges1){
			for(DefaultEdge e2:edges2){
				ParseGraphNode sourceE1s = g1.getEdgeSource(e1), sourceE1t = g1.getEdgeTarget(e1);
				ParseGraphNode sourceE2s = g2.getEdgeSource(e2), sourceE2t = g2.getEdgeTarget(e2);
				
				if (isNotEmpty(matcher.generalize(sourceE1s.getPtNodes(), sourceE2s.getPtNodes())) && 
						isNotEmpty(matcher.generalize(sourceE1t.getPtNodes(), sourceE2t.getPtNodes()))
					)
					gp.addVertex(new ParseGraphNode[] {sourceE1s, sourceE1t, sourceE2s, sourceE2t } );
			}
		}
		
		Set<ParseGraphNode[]> productVerticesSet = gp.vertexSet();
		List<ParseGraphNode[]> productVerticesList = new ArrayList<ParseGraphNode[]>(productVerticesSet);
		for(int i=0; i<productVerticesList.size(); i++){
			for(int j=i+1; j<productVerticesList.size(); j++){
				ParseGraphNode[] prodVertexI = productVerticesList.get(i);
				ParseGraphNode[] prodVertexJ = productVerticesList.get(j);
				if (bothAjacentOrNeitherAdjacent(prodVertexI, prodVertexJ)){
					gp.addEdge(prodVertexI, prodVertexJ);
				}
			}
		}
		
		
		return gp;
		
	}
	/*
	 * Finding the maximal clique is the slowest part
	 */
	
	public Collection<Set<ParseGraphNode[]>> getMaximalCommonSubgraphs(Graph<ParseGraphNode[], DefaultEdge>  g){
		BronKerboschCliqueFinder<ParseGraphNode[], DefaultEdge> finder =
	            new BronKerboschCliqueFinder<ParseGraphNode[], DefaultEdge>(g);

	        Collection<Set<ParseGraphNode[]>> cliques = finder.getBiggestMaximalCliques();
	        return cliques;
	}


	private boolean bothAjacentOrNeitherAdjacent(ParseGraphNode[] prodVertexI,
			ParseGraphNode[] prodVertexJ) {
		List<ParseGraphNode> prodVertexIlist = 
				new ArrayList<ParseGraphNode>(Arrays.asList(prodVertexI));
		List<ParseGraphNode> prodVertexJlist = 
				new ArrayList<ParseGraphNode>(Arrays.asList(prodVertexJ));
		prodVertexIlist.retainAll(prodVertexJlist);
		return (prodVertexIlist.size()==2 || prodVertexIlist.size()==4);
	}


	private boolean isNotEmpty(List<List<ParseTreeChunk>> generalize) {
		if (generalize!=null && generalize.get(0)!=null && generalize.get(0).size()>0)
			return true;
		else
			return false;
	}
	
	public Collection<Set<ParseGraphNode[]>>  assessRelevanceViaMaximalCommonSubgraphs(String para1, String para2) {
		// first build PTs for each text
		ParseThicket pt1 = ptBuilder.buildParseThicket(para1);
		ParseThicket pt2 = ptBuilder.buildParseThicket(para2);
		// then build phrases and rst arcs
		Graph<ParseGraphNode, DefaultEdge> g1 = graphBuilder.buildGraphFromPT(pt1);
		Graph<ParseGraphNode, DefaultEdge> g2 = graphBuilder.buildGraphFromPT(pt2);
		
		Graph<ParseGraphNode[], DefaultEdge> gp =  buildEdgeProduct(g1, g2);
		Collection<Set<ParseGraphNode[]>> col = getMaximalCommonSubgraphs(gp);
		return col;
		}
	
	public static void main(String[] args){
		 EdgeProductBuilder b = new  EdgeProductBuilder();
		 Collection<Set<ParseGraphNode[]>> col = b.assessRelevanceViaMaximalCommonSubgraphs("Iran refuses to accept the UN proposal to end its dispute over its work on nuclear weapons."+
				"UN nuclear watchdog passes a resolution condemning Iran for developing its second uranium enrichment site in secret. " +
				"A recent IAEA report presented diagrams that suggested Iran was secretly working on nuclear weapons. " +
				"Iran envoy says its nuclear development is for peaceful purpose, and the material evidence against it has been fabricated by the US. "

				, "Iran refuses the UN offer to end a conflict over its nuclear weapons."+
						"UN passes a resolution prohibiting Iran from developing its uranium enrichment site. " +
						"A recent UN report presented charts saying Iran was working on nuclear weapons. " +
				"Iran envoy to UN states its nuclear development is for peaceful purpose, and the evidence against its claim is fabricated by the US. ");
		System.out.print(col);
	}
}
				
