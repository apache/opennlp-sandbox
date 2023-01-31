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

public class FcaConverter {
	
	public FcaConverter (){
		
	}
	
	public int [][] latticeToContext(ConceptLattice cl){
		
		int newAttrCount = cl.conceptList.size();
		int [][] binaryContext = null;
		ArrayList<FormalConcept> cList = new ArrayList<FormalConcept>();
		cList.addAll(cl.conceptList);
		boolean run = true;
		int k=0;
		while (run && k<cl.conceptList.size()){
			if (cl.conceptList.get(k).intent.size() == cl.attributeCount){
					for (Integer i:cl.conceptList.get(k).parents){
						//cList.remove(cl.conceptList.get(i));
					}
				//cList.remove(cl.conceptList.get(k));
				run=false;
			}
			else{
				//cList.add(arg0, arg1);
				
			}
			
		}
		//System.out.println("cList.size() " + cList.size());
		run = true;
		k=0;
		while (run && k<=newAttrCount){
			if (cList.get(k).extent.size()==0)
				k++;
				run = false;
		}
		newAttrCount = cList.size();
		Set<Integer> nodeExtend;
		binaryContext = new int[cl.objectCount][newAttrCount];
		for (int j = 0; j<newAttrCount; j++){
			nodeExtend = cList.get(j).extent;
			//System.out.println(cList.get(j).position+" nodeExtend " + nodeExtend);
			for (Integer i: nodeExtend){
				binaryContext[i][j]=1;
			}
		}
		return binaryContext;
	}

}
