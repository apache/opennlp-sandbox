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
import java.util.Set;

import org.apache.commons.math3.special.Gamma;

public class Measures {
		
	final ConceptLattice cl;
	
	public Measures(ConceptLattice cl) {
		this.cl = cl;
	}
	
	public void logStabilityInt(){
		int min_delta, delta;
		float sum;
		for (int i = 0; i < cl.conceptList.size(); ++i) {
			min_delta = cl.attributeCount;
			sum = 0;
			FormalConcept fc = cl.conceptList.get(i);
			Set<Integer> parents = fc.parents;
			for (Integer j: parents) {
				delta = fc.getIntent().size() - cl.conceptList.get(j).intent.size();
				if (delta<min_delta)
					min_delta = delta;
				sum += Math.pow(2, -delta);
			}
			fc.intLogStabilityBottom=-(Math.log(sum)/Math.log(2.0));
			fc.intLogStabilityUp = min_delta;
		}
	}
	
	public void logStabilityExt(){
		int min_delta, delta;
		float sum;
		for (int i = 0; i < cl.conceptList.size(); ++i) {
			min_delta = cl.attributeCount;
			sum = 0;
			FormalConcept fc = cl.conceptList.get(i);
			for (int j: fc.children) {
				delta = fc.getExtent().size() - cl.conceptList.get(j).getExtent().size();
				if (delta<min_delta)
					min_delta = delta;
				sum += Math.pow(2, -delta);
			}
			fc.intLogStabilityBottom=-(Math.log(sum)/Math.log(2.0));
			fc.intLogStabilityUp = min_delta;
		}
	}
	
	public void separation(){
		ArrayList<Integer> intent;
		Set<Integer> extentMembers;
		int extentVolume, intentVolume;
		double sz;
		for (int i = 0; i < cl.conceptList.size(); ++i) {
			intent = cl.conceptList.get(i).getIntent();
			extentMembers = cl.conceptList.get(i).extent;
			extentVolume = 0;
			intentVolume = 0;
			for (Integer ext : extentMembers){
				for (int attr = 0; attr<cl.attributeCount;attr++){
					extentVolume += cl.binaryContext[ext][attr];
				}				
			}
			for (Integer integer : intent) {
				for (int obj = 0; obj < cl.objectCount; obj++) {
					intentVolume += cl.binaryContext[obj][integer];
				}
			}			
			sz = intent.size()*extentMembers.size();
			if (extentVolume+extentVolume-sz!=0)
				cl.conceptList.get(i).separation = sz/(extentVolume+intentVolume-sz);
			else
				cl.conceptList.get(i).separation = 0;
		}
	}
	public double attributeProbability(int attrNmb){
	    double pAttr = 0;
	    for (int i = 0; i<cl.objectCount;i++){
	    	pAttr+=cl.binaryContext[i][attrNmb];
	    }
	    pAttr/=cl.objectCount;
	    return pAttr;
	}
	
	public double intentProbability(ArrayList<Integer> intent){
		double pB = 1;
		for (int integer : intent) {
			pB *= attributeProbability(integer);
		}
		return pB;
	}
	
	public void probability(){			
		//the probability of B being closed
		for (int i = 0; i<cl.conceptList.size();i++){
			ArrayList<Integer> intent = cl.conceptList.get(i).getIntent();
			// out of concept intent
			double pB = intentProbability(intent);
			ArrayList<Integer> outOfIntent = new ArrayList<>();
			ArrayList<Double> outOfIntentAttrProb = new ArrayList<>();
			for (int j=0;j<cl.attributeCount; j++){
				outOfIntent.add(j);
			}
			for (int j=intent.size()-1;j>=0;j--){
				outOfIntent.remove(intent.get(j));
			}
			for (Integer integer : outOfIntent) {
				outOfIntentAttrProb.add(attributeProbability(integer));
			}
			double prob = 0,  mult, mult1;
			int n = cl.objectCount;
			for (int k=0; k<= n; k++){
				mult = 1;
				mult1 = 1;
				for (Double aDouble : outOfIntentAttrProb) {
					mult *= (1 - Math.pow(aDouble, k));
				}				
				mult1 = Math.pow(pB,k)*Math.pow(1-pB,n-k);
				prob+=mult1*mult* Gamma.digamma(n+1)/Gamma.digamma(k+1)/Gamma.digamma(n-k+1);
			}
			
			cl.conceptList.get(i).probability = prob;			
		}
	}

}
