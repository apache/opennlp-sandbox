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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.ListUtils;

public class BasicLevelMetrics {
	
	private final ConceptLattice cl;
	private List<ArrayList<Integer>> attributesExtent;
  private List<Integer> attributes = null;
	private final double[][] objectsSimilarityJ;
	private final double [][] objectsSimilaritySMC;

	public BasicLevelMetrics (ConceptLattice cl){
		this.cl = cl;
		this.attributesExtent = null;
		objectsSimilarityJ = new double [cl.objectCount][cl.objectCount];
		objectsSimilaritySMC = new double [cl.objectCount][cl.objectCount];
	}
	
	public void setUp(){
		attributesExtent = new ArrayList<>();
    List<ArrayList<Integer>> objectsIntent = new ArrayList<>();
		attributes = new ArrayList<>();
		
		for (int i=0;i<cl.attributeCount;i++){
			attributesExtent.add((ArrayList<Integer>) cl.getAttributeExtByID(i));
			attributes.add(i);
		}	
		
		for (int i=0;i<cl.objectCount;i++){
			objectsIntent.add(cl.getObjectIntByID(i));
		}
	
		double [] buf;
		
		for (int i = 0; i < cl.objectCount; i++){
			for (int j = i + 1 ; j < cl.objectCount; j++){
					buf = simJ_SMC(objectsIntent.get(i), objectsIntent.get(j));	
					objectsSimilarityJ[i][j] = buf[0];
					objectsSimilarityJ[j][i] = buf[0];
					objectsSimilaritySMC[i][j] = buf[1];
					objectsSimilaritySMC[j][i] = buf[1];
			}
			objectsSimilarityJ[i][i] = 1;
			objectsSimilaritySMC[i][i] = 1;
		}

	} 
	 
	// Utility functions for  Similarity approach (S)
	public double simSMC (ArrayList<Integer> intent1, ArrayList<Integer>intent2){
		int tp = (ListUtils.intersection(intent1,intent2)).size();
		ArrayList<Integer> fnlst = new ArrayList<>(this.attributes);
		fnlst.removeAll(ListUtils.union(intent1,intent2)); 
		int fn = fnlst.size();
		return (this.attributes.size()>0) ? 1.*(tp + fn)/this.attributes.size() : 0;
	}
	
	public double simJ (ArrayList<Integer> intent1, ArrayList<Integer>intent2){
		return 1.*(ListUtils.intersection(intent1,intent2)).size()/(ListUtils.union(intent1,intent2)).size() ;
	}
	
	public  double [] simJ_SMC(ArrayList<Integer> intent1, ArrayList<Integer>intent2){
		double simJ;
		double simSMC;
		Set<Integer> intersection = new HashSet<>(intent1);
		intersection.retainAll(intent2);
		
		Set<Integer> union = new HashSet<>(); 
		union.addAll(intent1);
		union.addAll(intent2);
		Set<Integer> unionOut = new HashSet<>(this.attributes);
		unionOut.removeAll(union);	
		simSMC = (this.attributes.size() > 0) ? 1.*(intersection.size() + unionOut.size())/this.attributes.size() : 0;
		simJ = (union.size() > 0) ? 1.*intersection.size()/union.size() : 0;
	    return new double[] {simJ, simSMC};
	}
	
	public double avgCohSMC (FormalConcept c){
		double sum = 0;
		if (c.extent.size() == 1)
			return 1.;//c.intent.size();
		if (c.extent.size() == 0)
			return 0.;
		else {
			for (Integer i:c.extent){
				for (Integer j: c.extent){
					if (i<j)
						sum+=objectsSimilaritySMC[i][j];
				}				
			}
			return (c.extent.size() > 1 ) ? 2.*sum/c.extent.size()/(c.extent.size()-1) : 0;// �� 2, ��� ��� ��������� ������
		}
	}
	
	public double avgCohJ (FormalConcept c){
		double sum = 0;
		if (c.extent.size() == 1)
			return 1.;//c.intent.size();
		if (c.extent.size() == 0)
			return 0.;
		else {
			for (Integer i:c.extent){
				for (Integer j: c.extent){
					if (i<j){
						sum+=objectsSimilarityJ[i][j];
					}
				}				
			}
			return (c.extent.size() > 1 ) ? 2.*sum/c.extent.size()/(c.extent.size()-1) : 0; 
		}
	}
	
	public double minCohJ (FormalConcept c){
		double min = Integer.MAX_VALUE, val;
		
		for (Integer i:c.extent){
			for (Integer j: c.extent){
					val = objectsSimilarityJ[i][j];
					if (val<min)
						min = val;
			}				
		}
		return (min < Integer.MAX_VALUE) ? min : 0;
	}
	
	public double minCohSMC (FormalConcept c){
		double min = Integer.MAX_VALUE, val;
		for (Integer i:c.extent){
			for (Integer j: c.extent){
					val = objectsSimilaritySMC[i][j];
					if (val<min)
						min = val;
			}				
		}
		return (min < Integer.MAX_VALUE) ? min : 0;
	}

	
	public double upperCohAvgByAvgJ(FormalConcept c, float tetta){
		//average alpha with average cohesion J
		double sum = 0;
		Set<Integer> upperNeighbors =c.parents;
		int rightNeighborsNumber = 0;
		float truthDegree;
		for (Integer i: upperNeighbors){
			if (c.cohAvgJ > cl.conceptList.get(i).cohAvgJ){
				rightNeighborsNumber++;
				sum+=1.*cl.conceptList.get(i).cohAvgJ/c.cohAvgJ;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			//truthDegree =  (float) Math.min(1.0, truthDegree);
			return (1-sum/rightNeighborsNumber)*truthDegree;
		}
	}
	
	public double upperCohAvgByMinJ(FormalConcept c,float tetta){
		//average alpha with min cohesion J
		double sum = 0;
		Set<Integer> upperNeighbors =c.parents;
		int rightNeighborsNumber = 0;
		float truthDegree;
		for (Integer i: upperNeighbors){
			if (c.cohMinJ > cl.conceptList.get(i).cohMinJ){
				rightNeighborsNumber++;
				sum+=1.*cl.conceptList.get(i).cohMinJ/c.cohMinJ;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (1-sum/rightNeighborsNumber)*truthDegree;
		}
	}
	
	public double upperCohMinByAvgJ(FormalConcept c,float tetta){
		//min alpha whth average cohesion J
		double max = Integer.MIN_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> upperNeighbors =c.parents;
		for (Integer i: upperNeighbors){
			if (c.cohAvgJ > cl.conceptList.get(i).cohAvgJ){
				rightNeighborsNumber++;
				val = 1.*cl.conceptList.get(i).cohAvgJ/c.cohAvgJ;	
				if (val>max)
					max = val;
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (1-max)*truthDegree;
		}		
	}
	
	public double upperCohMinByMinJ(FormalConcept c,float tetta){
		//min alpha whth average cohesion J
		double max = Integer.MIN_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> upperNeighbors =c.parents;
		for (Integer i: upperNeighbors){
			if (c.cohMinJ > cl.conceptList.get(i).cohMinJ){
				rightNeighborsNumber++;
				val = 1.*cl.conceptList.get(i).cohMinJ/c.cohMinJ;	
				if (val>max)
					max = val;
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			//truthDegree = rightNeighborsNumber/tetta/upperNeighbors.size();
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);			
			return (1-max)*truthDegree;
		}		
	}
	
	public double upperCohAvgByAvgSMC(FormalConcept c,float tetta){
		//average alpha with average cohesion SMC
		double sum = 0;
		Set<Integer> upperNeighbors =c.parents;
		int rightNeighborsNumber = 0;
		float truthDegree;
		for (Integer i: upperNeighbors){
			if (c.cohAvgSMC > cl.conceptList.get(i).cohAvgSMC){
				rightNeighborsNumber++;
				sum+=1.*cl.conceptList.get(i).cohAvgSMC/c.cohAvgSMC;		
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			return (1-sum/rightNeighborsNumber)*truthDegree;
		}	
	}
	
	public double upperCohAvgByMinSMC(FormalConcept c,float tetta){
		//average alpha with min cohesion SMC
		double sum = 0;
		Set<Integer> upperNeighbors =c.parents;
		int rightNeighborsNumber = 0;
		float truthDegree;
		for (Integer i: upperNeighbors){
			if (c.cohMinSMC > cl.conceptList.get(i).cohMinSMC){
				rightNeighborsNumber++;
				sum+=1.*cl.conceptList.get(i).cohMinSMC/c.cohMinSMC;		
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			return (1-sum/rightNeighborsNumber)*truthDegree;
		}
	}
		
		
	public double upperCohMinByAvgSMC(FormalConcept c,float tetta){
		//min alpha whth average cohesion J
		
		double max = Integer.MIN_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> upperNeighbors =c.parents;
		for (Integer i: upperNeighbors){
			if (c.cohAvgSMC > cl.conceptList.get(i).cohAvgSMC){
				rightNeighborsNumber++;
				val = 1.*cl.conceptList.get(i).cohAvgSMC/c.cohAvgSMC;	
				if (val>max)
					max = val;
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (1-max)*truthDegree;
		}			
	}
	
	public double upperCohMinByMinSMC(FormalConcept c,float tetta){
		//min alpha whth average cohesion J
		double max = Integer.MIN_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> upperNeighbors =c.parents;
		for (Integer i: upperNeighbors){
			if (c.cohMinSMC > cl.conceptList.get(i).cohMinSMC){
				rightNeighborsNumber++;
				val = 1.*cl.conceptList.get(i).cohMinSMC/c.cohMinSMC;	
				if (val>max)
					max = val;
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/upperNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);		
			return (1-max)*truthDegree;
		}		
	}
	

	
	public double lowerCohAvgByAvgJ(FormalConcept c,float tetta){
		double sum = 0;
		Set<Integer> lowerNeighbors =c.children;
		int rightNeighborsNumber = 0;
		float truthDegree;
		for (Integer i: lowerNeighbors){
			if (c.cohAvgJ < cl.conceptList.get(i).cohAvgJ){
				rightNeighborsNumber++;
				sum+=1.*c.cohAvgJ/cl.conceptList.get(i).cohAvgJ;
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (sum/rightNeighborsNumber)*truthDegree;
		}
	}
	
	public double lowerCohAvgByMinJ(FormalConcept c,float tetta){
		double sum = 0;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohMinJ< cl.conceptList.get(i).cohMinJ){
				rightNeighborsNumber++;
				sum+=1.*c.cohMinJ/cl.conceptList.get(i).cohMinJ;
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			//truthDegree = rightNeighborsNumber/tetta/lowerNeighbors.size();
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (sum/rightNeighborsNumber)*truthDegree;
		}			
	}
	
	public double lowerCohMinByAvgJ(FormalConcept c,float tetta){
		double min = Integer.MAX_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohAvgJ< cl.conceptList.get(i).cohAvgJ){
				rightNeighborsNumber++;
				val = 1.*c.cohAvgJ/cl.conceptList.get(i).cohAvgJ;
				if (val<min)
					min = val;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (min)*truthDegree;
		}		
	}
	
	public double lowerCohMinByMinJ(FormalConcept c,float tetta){
		double min = Integer.MAX_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohMinJ< cl.conceptList.get(i).cohMinJ){
				rightNeighborsNumber++;
				val = 1.*c.cohMinJ/cl.conceptList.get(i).cohMinJ;
				if (val<min)
					min = val;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (min)*truthDegree;
		}		
	}
	
	public double lowerCohAvgByAvgSMC(FormalConcept c,float tetta){
		double sum = 0;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohAvgSMC < cl.conceptList.get(i).cohAvgSMC){
				rightNeighborsNumber++;
				sum+=1.*c.cohAvgSMC/cl.conceptList.get(i).cohAvgSMC;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (sum/rightNeighborsNumber)*truthDegree;
		}			
	}
	
	public double lowerCohAvgByMinSMC(FormalConcept c,float tetta){
		double sum = 0;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohMinSMC < cl.conceptList.get(i).cohMinSMC){
				rightNeighborsNumber++;
				sum+=1.*c.cohMinSMC/cl.conceptList.get(i).cohMinSMC;		
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (sum/rightNeighborsNumber)*truthDegree;
		}			
	}
	
	public double lowerCohMinByAvgSMC(FormalConcept c,float tetta){
		double min = Integer.MAX_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohAvgSMC<=cl.conceptList.get(i).cohAvgSMC){
				rightNeighborsNumber++;
				val = 1.*c.cohAvgSMC/cl.conceptList.get(i).cohAvgSMC;	
				if (val<min)
					min = val;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			truthDegree =  (float) Math.min(1.0, truthDegree);
			return (min)*truthDegree;
		}		
	}
	
	
	public double lowerCohMinByMinSMC(FormalConcept c,float tetta){
		double min = Integer.MAX_VALUE, val;
		int rightNeighborsNumber = 0;
		float truthDegree;
		Set<Integer> lowerNeighbors =c.children;
		for (Integer i: lowerNeighbors){
			if (c.cohMinSMC< cl.conceptList.get(i).cohMinSMC){
				rightNeighborsNumber++;
				val = 1.*c.cohMinSMC/cl.conceptList.get(i).cohMinSMC;
				if (val<min)
					min = val;	
			}
		}
		if (rightNeighborsNumber == 0)
			return 0;
		else{
			truthDegree = (1.*rightNeighborsNumber/lowerNeighbors.size()>=tetta) ? 1 : 0;
			return (min)*truthDegree;
		}		
	}
	
	
	//Similarity approach (S)
	//group of metrics: 
	// SMC avg_avg, avg_min, min_avg
	//   J avg_avg, avg_min, min_avg
	public void similarityGoguenNorm(){
		
		// ��� ������� �� ��������� �������� �� ���� �������� ������ � �����
		// ����� �����������  � ������� ������� ������ � �����
		double[] buf = new double[2];
		
		if (objectsSimilaritySMC == null)
			this.setUp();		
		
	//upper neighbors - alpha2
	//lower neighbors - alpha3
		for (FormalConcept c: cl.conceptList){	
			c.cohAvgJ = avgCohJ(c);
			c.cohMinJ = minCohJ(c);
			c.cohAvgSMC = avgCohSMC(c);
			c.cohMinSMC = minCohSMC(c);
			
		}
		for (FormalConcept c: cl.conceptList){
			float tetta = 1;
			c.blS_Jaa = (c.cohAvgJ != 0) ? c.cohAvgJ * upperCohAvgByAvgJ(c,tetta) * lowerCohAvgByAvgJ(c,tetta) : 0;
			c.blS_Jma = (c.cohMinJ != 0) ? c.cohMinJ * upperCohAvgByMinJ(c,tetta) * lowerCohAvgByMinJ(c,tetta) : 0;			
			c.blS_Jam = (c.cohAvgJ != 0) ? c.cohAvgJ * upperCohMinByAvgJ(c,tetta) * lowerCohMinByAvgJ(c,tetta) : 0;
			c.blS_Jmm = (c.cohMinJ != 0) ? c.cohMinJ * upperCohMinByMinJ(c,tetta) * lowerCohMinByMinJ(c,tetta) : 0;
			c.blS_SMCaa = (c.cohAvgSMC != 0) ? c.cohAvgSMC * upperCohAvgByAvgSMC(c,tetta) * lowerCohAvgByAvgSMC(c,tetta) : 0;
			c.blS_SMCma = (c.cohMinSMC != 0) ? c.cohMinSMC * upperCohAvgByMinSMC(c,tetta) * lowerCohAvgByMinSMC(c,tetta) : 0;
			c.blS_SMCam = (c.cohAvgSMC != 0) ? c.cohAvgSMC * upperCohMinByAvgSMC(c,tetta) * lowerCohMinByAvgSMC(c,tetta) : 0;
			c.blS_SMCmm = (c.cohMinSMC != 0) ? c.cohMinSMC * upperCohMinByMinSMC(c,tetta) * lowerCohMinByMinSMC(c,tetta) : 0;
		}	
	}
	
	//Cue validity approach (CV)
	public void cueValidity(){
		
		if (attributesExtent == null)
				this.setUp();
		
		ArrayList<Integer> attrExtent;
		Set<Integer> intersection;
		double sum;
		for (FormalConcept c: cl.conceptList){
			sum = 0;
			for (Integer i: c.intent){
				intersection = new HashSet<>(c.extent);
				attrExtent = attributesExtent.get(i);				
				intersection.retainAll(attrExtent);
				sum+= (double) intersection.size() /attrExtent.size();
				}	
			c.blCV = Double.isNaN(sum) ? 0 : sum;
			
		}			
	}
	//Category feature collocation approach
	public void categoryFeatureCollocation(){
		if (attributesExtent == null)
			this.setUp();
		
		ArrayList<Integer> attrExtent;
		Set<Integer> intersection;
		double sum;
		int latticeSize = cl.conceptList.size();
		for (FormalConcept c: cl.conceptList){
			sum = 0;
			for (int i = 0; i < cl.attributeCount; i++){
				intersection = new HashSet<>(c.extent);
				attrExtent = attributesExtent.get(i);				
				intersection.retainAll(attrExtent);
				sum+=(double)intersection.size()/attrExtent.size()*intersection.size()/c.extent.size();
				}	
			c.blCFC = Double.isNaN(sum) ? 0 : sum;
		}
	}
	
	//Category utility approach
	public void categoryUtility(){
		if (attributesExtent == null)
			this.setUp();
		
		ArrayList<Integer> attrExtent;
		Set<Integer> intersection;
		double sum;
		int attrSize = cl.objectCount;
		int cExtentSize = 0;
		for (FormalConcept c: cl.conceptList){
			sum = 0;
			for (int i = 0; i < cl.attributeCount; i++){
				intersection = new HashSet<>(c.extent);
				cExtentSize = c.extent.size();
				attrExtent = attributesExtent.get(i);				
				intersection.retainAll(attrExtent);
				sum += Math.pow(intersection.size()*1./cExtentSize,2)-Math.pow(1.*attrExtent.size()/attrSize,2);
				}	
			c.blCU =Double.isNaN(1.*cExtentSize/attrSize*sum) ? 0 : 1.*cExtentSize/attrSize*sum;
		}					
	}
	
	//Predictability approach (P)
	public void predictability(){
		
		if (attributesExtent == null)
			this.setUp();
		ArrayList<Integer> attributes = new ArrayList<>();
		ArrayList<Integer> outOfIntent;
		Set<Integer> intersection;
		ArrayList<Integer> attrExtent;
		double sum, term;
		
		for (int i = 0; i< cl.attributeCount; i++){
			attributes.add(i);
		}
		for (FormalConcept c: cl.conceptList){
			sum = 0;
			outOfIntent = new ArrayList<>(attributes);
			outOfIntent.removeAll(c.intent);
			for (Integer y: outOfIntent){
				intersection = new HashSet<>(c.extent);
				attrExtent = attributesExtent.get(y);				
				intersection.retainAll(attrExtent);
				term = 1.*intersection.size()/c.extent.size();
				
				if (term > 0){
					sum-=term*Math.log(term);
				}
			}
			c.blP = Double.isNaN(1-sum/outOfIntent.size()) ? 0 : 1-sum/outOfIntent.size();
		}
		
	}
}
