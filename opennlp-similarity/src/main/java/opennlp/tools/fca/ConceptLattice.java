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

package opennlp.tools.fca;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections.ListUtils;

public class ConceptLattice {
	int objectCount;
	int attributeCount;
	ArrayList<FormalConcept> conceptList;
	int [][] binaryContext;
	static Measures ms;
	static BasicLevelMetrics blm;
	
	public ConceptLattice(int objCount, int attrCount, int [][] binaryContext,boolean stats) {
		this.objectCount = objCount;
		this.attributeCount = attrCount;
		this.binaryContext = binaryContext;
		this.conceptList = new ArrayList<>();
		FormalConcept bottom = new FormalConcept();
		bottom.setPosition(0);
		conceptList.add(bottom);
		ms = new Measures(this);
		blm = new BasicLevelMetrics(this);		
		if (this.binaryContext !=null)
			createLatticeFromBinaryContext();
		if (stats){
			ms.logStabilityExt();
			ms.separation();	
			ms.probability();
			blm.cueValidity();
			blm.categoryFeatureCollocation();
			blm.categoryUtility();
			blm.predictability();
			blm.similarityGoguenNorm();		
		}
	}
	
	public ConceptLattice(String filename, boolean stats) throws IOException {
		
		FcaReader fr = new FcaReader();
		fr.ReadContextFromCxt(filename);
		this.objectCount = fr.getObjectsCount();
		this.attributeCount = fr.getAttributesCount();
		this.binaryContext = fr.getBinaryContext();
		
		this.conceptList = new ArrayList<>();
		FormalConcept bottom = new FormalConcept();
		bottom.setPosition(0);
		conceptList.add(bottom);
		ms = new Measures(this);
		blm = new BasicLevelMetrics(this);
		if (this.binaryContext !=null)
			createLatticeFromBinaryContext();
		if (stats){
			ms.logStabilityExt();
			ms.separation();	
			ms.probability();
			blm.cueValidity();
			blm.categoryFeatureCollocation();
			blm.categoryUtility();
			blm.predictability();
			blm.similarityGoguenNorm();
		}				
	}

	
	public int GetMaximalConcept(List<Integer> intent, int Generator) {
		boolean parentIsMaximal = true;
		while(parentIsMaximal) {
			parentIsMaximal = false;
			for (int parent : conceptList.get(Generator).getParents()) {
				if (conceptList.get(parent).getIntent().containsAll(intent)) {
					Generator = parent;
					parentIsMaximal = true;
					break;
				}
			}
		}
		return Generator;
	}
	
	public void AddExtentToAncestors(LinkedHashSet<Integer>extent, int curNode) {
		if (conceptList.get(curNode).parents.size()>0){
			for (int parent : conceptList.get(curNode).parents){
				conceptList.get(parent).addExtents(extent);
				AddExtentToAncestors(extent, parent);
			}
		}	
	}
	
	public int AddIntent(List<Integer> intent,LinkedHashSet<Integer>extent, int generator) {
		//System.out.println("add intent "+intent+extent+generator);
		int generator_tmp = GetMaximalConcept(intent, generator);
		generator = generator_tmp;
		//System.out.println("	max gen "+generator);
		if (conceptList.get(generator).getIntent().equals(intent)) {
			conceptList.get(generator).addExtents(extent);
			AddExtentToAncestors(extent, generator);							
			return generator;
		}
		Set<Integer> generatorParents = conceptList.get(generator).getParents();
		Set<Integer> newParents = new HashSet<>();
		for (int candidate : generatorParents) {
			if (!intent.containsAll(conceptList.get(candidate).getIntent())) {
				List<Integer> intersection = ListUtils.intersection(intent, conceptList.get(candidate).getIntent());				
				LinkedHashSet<Integer> new_extent = new LinkedHashSet<>();
				new_extent.addAll(conceptList.get(candidate).extent);
				new_extent.addAll(extent);
				candidate = AddIntent(intersection,new_extent,candidate);
			}
			
			boolean addParents = true;
			Iterator<Integer> iterator = newParents.iterator();
			while (iterator.hasNext()) {
				Integer parent = iterator.next();
				if (conceptList.get(parent).getIntent().containsAll(conceptList.get(candidate).getIntent())) {
					addParents = false;
					break;
				}
				else {
					if (conceptList.get(candidate).getIntent().containsAll(conceptList.get(parent).getIntent())) {
						iterator.remove();
					}
				}
			}
			if (addParents) {
				newParents.add(candidate);
			}
		}
		
		FormalConcept newConcept = new FormalConcept();
		newConcept.setIntent(intent);
		LinkedHashSet<Integer> new_extent = new LinkedHashSet<>();
		new_extent.addAll(conceptList.get(generator).extent);
		new_extent.addAll(extent);
		newConcept.addExtents(new_extent);
		newConcept.setPosition(conceptList.size());
		conceptList.add(newConcept);
		conceptList.get(generator).getParents().add(newConcept.position);
		conceptList.get(newConcept.position).children.add(generator);
		for (int newParent: newParents) {
			if (conceptList.get(generator).getParents().contains(newParent)) {
				conceptList.get(generator).getParents().remove(newParent);
				conceptList.get(newParent).children.remove(generator);
			}
			conceptList.get(newConcept.position).getParents().add(newParent);
			conceptList.get(newParent).addExtents(new_extent);
			AddExtentToAncestors(new_extent, newParent);
			conceptList.get(newParent).children.add(newConcept.position);
		}
		
		return newConcept.position;
	}
	
	public void printLatticeStats() {
		System.out.println("Lattice stats");
		System.out.println("max_object_index = " + objectCount);
		System.out.println("max_attribute_index = " + attributeCount);
		System.out.println("Current concept count = " + conceptList.size());
	}
	
	public void printLattice() {
		for (int i = 0; i < conceptList.size(); ++i) {
			printConceptByPosition(i);
		}
	}
	
	public void printLatticeFull() {
		for (int i = 0; i < conceptList.size(); ++i) {
			printConceptByPositionFull(i);
		}
	}
	
	public void printContext() {
		for (int i = 0; i<objectCount; i++){
			for (int j = 0; j<attributeCount; j++){
				System.out.print(binaryContext[i][j]+" ");
			}
			System.out.println();
		}	
	}
	
	public void printConceptByPosition(int index) {
		System.out.println("Concept at position " + index);
		conceptList.get(index).printConcept();
	}
	
	public void printConceptByPositionFull(int index) {
		System.out.println("Concept at position " + index);
		conceptList.get(index).printConceptFull();
	}
	
	public void createLatticeFromBinaryContext(){
		LinkedHashSet<Integer> obj;
		ArrayList<Integer> intent;
		// attributes list
		ArrayList<Integer> attributes = new ArrayList<>();
		for (int i = 0; i <attributeCount; i++){
			attributes.add(i);
		}
		this.conceptList.get(0).setIntent(attributes);

		for (int i = 0; i < objectCount; i++){
			intent = new ArrayList<>();
			obj = new LinkedHashSet<>();
			obj.add(i);
			for (int j = 0; j < attributeCount; j++){
				if (binaryContext[i][j] == 1){
					intent.add(j);
				}
			}
			this.AddIntent(intent,obj,0);
		}	
	}
	
	public static void main(String []args) throws IOException {
		ConceptLattice cl = new ConceptLattice("sports.cxt", true);
		cl.printLattice();	
	}
	
	public List<Integer> getAttributeExtByID(int ind){
		ArrayList<Integer> attrExt = new ArrayList<>();
		for (int i=0;i<objectCount; i++)
			if (binaryContext[i][ind]==1)
				attrExt.add(i); 
		return attrExt;
	}
	
	public ArrayList<Integer> getObjectIntByID(int ind){
		ArrayList<Integer> objInt = new ArrayList<>();
		for (int i=0;i<attributeCount; i++)
			if (binaryContext[ind][i]==1)
				objInt.add(i); 
		return objInt;
	}	
	
	public ArrayList<FormalConcept> getLattice(){
		return conceptList;
	}
	
	public int getAttributesCount() {
		return attributeCount;
	}
	
	public int getObjectCount() {
		return objectCount;
	}
	
	public int getSize(){
		return conceptList.size();
	}

	public void printBinContext() {
		for (int[] ints : binaryContext) {
			System.out.println(Arrays.toString(ints));
		}	
	}
	
}
