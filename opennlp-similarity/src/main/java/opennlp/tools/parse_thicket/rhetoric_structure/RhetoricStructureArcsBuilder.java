package opennlp.tools.parse_thicket.rhetoric_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.parse_thicket.ArcType;
import opennlp.tools.parse_thicket.Pair;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;


import edu.stanford.nlp.trees.Tree;

public class RhetoricStructureArcsBuilder {
	private RhetoricStructureMarker markerBuilderForSentence = new RhetoricStructureMarker();

	private Map<Integer, List<Pair<String, Integer[]>>> buildMarkers(ParseThicket pt){

		Map<Integer, List<Pair<String, Integer[]>>> sentNumMarkers = new 
				HashMap<Integer, List<Pair<String, Integer[]>>>();
		int count = 0;
		for( List<ParseTreeNode> sent: pt.getNodesThicket()){
			List<Pair<String, Integer[]>> markersForSentence = markerBuilderForSentence.
					extractRSTrelationInSentenceGetBoundarySpan(sent);
			sentNumMarkers.put(count,  markersForSentence);
			count++;
		}
		return sentNumMarkers;
	}


	/*
	 * Induced RST algorithm
	 * 
	 * Input: obtained RST markers (numbers of words which 
	 * splits sentence in potential RST relation arguments) +
	 * Current Parse Thicket with arcs for coreferences
	 * 
	 * We search for parts of sentences on the opposite side of RST markers
	 * 
	 * $sentPosFrom$  marker
	 *  | == == == [ ] == == == |
	 *     \				\
	 *       \				  \
	 *       coref          RST arc being formed
	 *           \ 				\
	 *             \			 \
	 *     | == == == == == [  ] == == ==|      
	 *     
	 *       Mark yelled at his dog, but it disobeyed
	 *        |							\
	 *       coref                 RST arc for CONTRAST being formed
	 *        | 							\
	 *       He was upset, however he did not show it
	 *       $sentPosTo$
	 */
	public List<WordWordInterSentenceRelationArc> buildRSTArcsFromMarkersAndCorefs(
			List<WordWordInterSentenceRelationArc> arcs,
			Map<Integer, List<List<ParseTreeNode>>> sentNumPhrasesMap, 
			ParseThicket pt ) {
		List<WordWordInterSentenceRelationArc> arcsRST = new ArrayList<WordWordInterSentenceRelationArc>();		

		Map<Integer, List<Pair<String, Integer[]>>> rstMarkersMap = buildMarkers(pt);

		for(int nSentFrom=0; nSentFrom<pt.getSentences().size(); nSentFrom++){
			for(int nSentTo=nSentFrom+1; nSentTo<pt.getSentences().size(); nSentTo++){
				// for given arc, find phrases connected by this arc and add to the list of phrases

				List<List<ParseTreeNode>> phrasesFrom = sentNumPhrasesMap.get(nSentFrom);
				List<List<ParseTreeNode>> phrasesTo = sentNumPhrasesMap.get(nSentTo);
				List<Pair<String, Integer[]>> markersFrom = rstMarkersMap.get(nSentFrom);
				List<Pair<String, Integer[]>> markersTo = rstMarkersMap.get(nSentTo);
				for(WordWordInterSentenceRelationArc arc: arcs){
					// arc should be coref and link these sentences
					if (nSentFrom != arc.getCodeFrom().getFirst() ||
							nSentTo != arc.getCodeTo().getFirst() ||
							!arc.getArcType().getType().startsWith("coref")
							)
						continue;
					int sentPosFrom = arc.getCodeFrom().getSecond();
					int sentPosTo = arc.getCodeTo().getSecond();
					// not more than a single RST link for a pair of sentences
					boolean bFound = false;
					for(List<ParseTreeNode> vpFrom: phrasesFrom){
						if (bFound)
							break;
						for(List<ParseTreeNode> vpTo: phrasesTo){
							for(Pair<String, Integer[]> mFrom: markersFrom){
								for(Pair<String, Integer[]> mTo: markersTo) {
									{
										// the phrases should be on an opposite side of rst marker for a coref link
										if (isSequence( new Integer[] { sentPosFrom,  vpFrom.get(0).getId(), mFrom.getSecond()[0]}) &&
												isSequence( new Integer[] { sentPosTo,  vpTo.get(0).getId(), mTo.getSecond()[0]})	){
											ArcType arcType = new ArcType("rst", mFrom.getFirst(), 0, 0);

											WordWordInterSentenceRelationArc arcRST = 
													new WordWordInterSentenceRelationArc(new Pair<Integer, Integer>(nSentFrom, mFrom.getSecond()[1]), 
															new Pair<Integer, Integer>(nSentTo, mTo.getSecond()[1]), "", "", arcType);
											arcsRST.add(arcRST);
											bFound = true;
											break;
										}
									}
								}
							}
						}
					}
				}
			}
		}

		return arcs;
	}

// check if the word positions occur in sentence in the order Integer[]
// TODO make more sensitive algo	
	private static boolean isSequence(Integer[] integers) {
		//TODO better construction of array
		if (integers==null || integers.length<3)
			return false;
		try {
			for(Integer i: integers)
				if (i==0)
					return false;
		} catch (Exception e) {
			return false;
		}
		
		Boolean bWrongOrder = false;
		for(int i=1; i< integers.length; i++){
			if (integers[i-1]>integers[i]){
				bWrongOrder = true;
				break;
			}
		}
		
		Boolean bWrongInverseOrder = false;
		for(int i=1; i< integers.length; i++){
			if (integers[i-1]<integers[i]){
				bWrongInverseOrder = true;
				break;
			}
		}
		
		return !(bWrongOrder && bWrongInverseOrder);
	}



	public static void main(String[] args){


	}
}
