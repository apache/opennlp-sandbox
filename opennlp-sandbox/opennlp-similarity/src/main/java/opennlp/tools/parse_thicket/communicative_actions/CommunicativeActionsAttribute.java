/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.parse_thicket.communicative_actions;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.IGeneralizer;


public class CommunicativeActionsAttribute implements IGeneralizer<Integer[]>{

	public List<Integer[]> generalize(Object intArr1ob, Object intArr2ob) {
		Integer[] arr1 = (Integer[])intArr1ob, arr2 = (Integer[])intArr2ob;
		Integer[] result = new Integer[arr2.length];
		for(int i=0; i< arr2.length; i++ ){
			if (arr1[i].equals(arr2[i]))
				result[i] = arr1[i];
			else if ((arr1[i]<0 && arr2[i]>0) || (arr1[i]>0 && arr2[i]<0)){
				result[i]=0;
			} else if (arr1[i]==0)
				result[i]=arr2[i];
			else if (arr2[i]==0)
				result[i]=arr1[i];
		}
		List<Integer[]> results = new ArrayList<>();
		results.add(result);
		return results;
	}

}
