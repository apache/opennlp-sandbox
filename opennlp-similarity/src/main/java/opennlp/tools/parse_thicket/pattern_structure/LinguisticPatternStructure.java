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
package opennlp.tools.parse_thicket.pattern_structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.fca.ConceptLattice;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class LinguisticPatternStructure extends PhrasePatternStructure {

	public LinguisticPatternStructure(int objectCounts, int attributeCounts) {
		super(objectCounts, attributeCounts);
		
		ConceptLattice cl = null;
	}
	
	public void AddExtentToAncestors(LinkedHashSet<Integer>extent, int curNode) {
		//
		if (conceptList.get(curNode).parents.size()>0){
			for (int parent : conceptList.get(curNode).parents){
				conceptList.get(parent).addExtents(extent);
				AddExtentToAncestors(extent, parent);
			}
		}	
	}
	
	public int AddIntent(List<List<ParseTreeChunk>> intent, LinkedHashSet<Integer>extent,int generator) {
		// TODO OPENNLP-1454 Candidate for logger.debug(...) if required/helpful
		// System.out.println("debug called for " + intent);
		//printLattice();
		int generator_tmp = GetMaximalConcept(intent, generator);
		generator = generator_tmp;
		if (conceptList.get(generator).intent.equals(intent)) {
			System.out.println("at generator:" + conceptList.get(generator).intent);
			System.out.println("to add:" + intent);
			System.out.println("already generated");
			AddExtentToAncestors(extent, generator);	
			return generator;
		}
		Set<Integer> generatorParents = conceptList.get(generator).parents;
		Set<Integer> newParents = new HashSet<>();
		for (int candidate : generatorParents) {
			if (!intent.containsAll(conceptList.get(candidate).intent)) {
				List<List<ParseTreeChunk>> intersection = md
				.matchTwoSentencesGroupedChunksDeterministic(intent, conceptList.get(candidate).intent);
				LinkedHashSet<Integer> new_extent = new LinkedHashSet<>();
				new_extent.addAll(conceptList.get(candidate).extent);
				new_extent.addAll(extent);
				if (intent.size()!=intersection.size()){
					// TODO OPENNLP-1454 Candidate for logger.debug(...) if required/helpful
					// System.out.println("recursive call (inclusion)");
					// System.out.println(intent + "----" + intersection);
					candidate = AddIntent(intersection,new_extent, candidate);
				}
			}
			
			boolean addParents = true;
			// System.out.println("now iterating over parents");
			Iterator<Integer> iterator = newParents.iterator();
			while (iterator.hasNext()) {
				Integer parent = iterator.next();
				if (conceptList.get(parent).intent.containsAll(conceptList.get(candidate).intent)) {
					addParents = false;
					break;
				}
				else {
					if (conceptList.get(candidate).intent.containsAll(conceptList.get(parent).intent)) {
						iterator.remove();
					}
				}
			}
			if (addParents) {
				newParents.add(candidate);
			}
		}
		// TODO OPENNLP-1454 Candidate for logger.debug(...) if required/helpful
		// System.out.println("size of lattice: " + conceptList.size());
		PhraseConcept newConcept = new PhraseConcept();
		newConcept.setIntent(intent);

		LinkedHashSet<Integer> new_extent = new LinkedHashSet<>();
		new_extent.addAll(conceptList.get(generator).extent);
		new_extent.addAll(extent);
		newConcept.addExtents(new_extent);
		
		newConcept.setPosition(conceptList.size());
		conceptList.add(newConcept);
		conceptList.get(generator).parents.add(newConcept.position);
		conceptList.get(newConcept.position).children.add(generator);
		for (int newParent: newParents) {
			if (conceptList.get(generator).parents.contains(newParent)) {
				conceptList.get(generator).parents.remove(newParent);
				conceptList.get(newParent).children.remove(generator);
			}
			conceptList.get(newConcept.position).parents.add(newParent);
			conceptList.get(newParent).addExtents(new_extent);
			AddExtentToAncestors(new_extent, newParent);
			conceptList.get(newParent).children.add(newConcept.position);
		}
		return newConcept.position;
	}
	
	public void printLatticeExtended() {
		for (int i = 0; i < conceptList.size(); ++i) {
			printConceptByPositionExtended(i);
		}
	}
	
	public void printConceptByPositionExtended(int index) {
		System.out.println("Concept at position " + index);
		conceptList.get(index).printConceptExtended();
	}
	
	
	public int [][] toContext(int extentCardinality){
		
		int newAttrCount = conceptList.size();
		ArrayList<PhraseConcept> cList = new ArrayList<>(conceptList);
		boolean run = true;
		int k=0;
		while (run && k<conceptList.size()){
			if (conceptList.get(k).intent.size() == attributeCount){
				if (conceptList.get(k).extent.size() == 0)
					for (Integer i:conceptList.get(k).parents)
						cList.remove(i);
				cList.remove(k);
				run=false;
			}
			else
				k+=1;	
		}
		
		run = true;
		k=0;
		while (run && k<=newAttrCount){
			if (cList.get(k).extent.size()==0)
				k++;
				run = false;
		}
		newAttrCount = cList.size();
		Set<Integer> nodeExtend;
		int [][] binaryContext = new int[extentCardinality][newAttrCount];
		for (int j = 0; j<newAttrCount; j++){
			nodeExtend = cList.get(j).extent;
			for (Integer i: nodeExtend){
				binaryContext[i][j]=1;
			}
		}
		return binaryContext;
	}
	
	
	
	public void logStability(){
		int min_delta, delta;
		float sum;
		for (PhraseConcept phraseConcept : conceptList) {
			min_delta = Integer.MAX_VALUE;
			sum = 0;
			PhraseConcept pc = phraseConcept;
			for (Integer j : pc.children) {
				delta = pc.extent.size() - conceptList.get(j).extent.size();
				if (delta < min_delta)
					min_delta = delta;
				sum += Math.pow(2, -delta);
			}
			pc.intLogStabilityBottom = -(Math.log(sum) / Math.log(2.0));
			pc.intLogStabilityUp = min_delta;
		}
	}

}
