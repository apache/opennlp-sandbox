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
package opennlp.tools.jsmlearning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public class FeatureSpaceCoverageProcessor {

	public Map<String, Integer> paramMap = new HashMap<>();
	public String[] header; 
	String[] attributes;

	public FeatureSpaceCoverageProcessor (){
		
	}

	public void initParamMap(String[] attributes, String[] header){
		this.header = header;
		this.attributes = attributes;
		for(int m=0; m<header.length; m++){
			paramMap.put(header[m], m);
		}
	}


	// distance between array and array
	public Float calcDistance(String[] seed, String[] candidate) throws Exception {
		if (paramMap.isEmpty())
			throw new Exception("paramMap.isEmpty()");

		float score = 0f;
		int p1 = paramMap.get("First Level Category");	
		int p2 = paramMap.get("Second Level Category");
		if (seed[p1].equals(candidate[p1])) {
			if (seed[p2].equals(candidate[p2]))
				score = score+0.0000001f;
			else
				score = score+0.01f;			
		} else return 100000f;

		try {
			int p3 = paramMap.get("Latitude");	
			int p4 = paramMap.get("Longitude");
			Double latDiff = Math.abs(Double.parseDouble(seed[p3]) - Double.parseDouble(candidate[p3]));
			Double longDiff = Math.abs(Double.parseDouble(seed[p4]) - Double.parseDouble(candidate[p4]));
			if (latDiff>1 || longDiff>1)
				return 1000000f;
			else 
				score+= latDiff.floatValue()/100.0f + longDiff.floatValue()/100.0f;
		} catch (Exception e) {
			return 1000000f;
		}


		return score;
	}

	// distance between matrix and array
	public Float calcDistance(String[][] seed, String[] candidate) throws Exception {
		if (paramMap.isEmpty())
			throw new Exception("paramMap.isEmpty()");

		float score = 0f, catScore = 10000f, currCatScore=10000000f;

		int p1 = paramMap.get("First Level Category");	
		int p2 = paramMap.get("Second Level Category");
		for(int v=0; v<seed[0].length; v++){
			if (seed[p1][v].equals(candidate[p1])) {
				if (seed[p2][v].equals(candidate[p2]))
					currCatScore = 0.0000001f;
				else
					currCatScore = 0.01f;			
			} 
			if ( catScore >  currCatScore) // if found closer, update
				catScore =  currCatScore;
		}
		score = catScore;
		if (score > 1000000f)
			return 10000000f;

		float latLongScore = 100000f, currLatLongScore = 10000000f;
		for(int v=0; v<seed[0].length; v++){
			try {
				int p3 = paramMap.get("Latitude");	
				int p4 = paramMap.get("Longitude");
				if (seed[p3][v].equals("") || seed[p4][v].equals("") 
						|| candidate[p3].equals("") ||  candidate[p4].equals(""))
					continue;
				Double latDiff = Math.abs(Double.parseDouble(seed[p3][v]) - Double.parseDouble(candidate[p3]));
				Double longDiff = Math.abs(Double.parseDouble(seed[p4][v]) - Double.parseDouble(candidate[p4]));
				if (!(latDiff>1 || longDiff>1))
					currLatLongScore = latDiff.floatValue()/100.0f + longDiff.floatValue()/100.0f;
			} catch (Exception e) {
				//return 1000000f;
			}
			if (latLongScore > currLatLongScore)
				latLongScore = currLatLongScore;

		}	
		if (latLongScore> 10000)
			return 10000f;
		score+=latLongScore;
		return score;
	}

	public Integer getIdForAttributeName(String key){
		Integer res = paramMap.get(key);
		try {
			res.toString();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("wrong key"+key);
		}
		return res;

	}

	public String getAttribNameForId(Integer id){
		return header[id];
	}




	public Map<String, String> computeIntersection(String[] line1,
			String[] line2) {

		Map<String, String> attr_value = new HashMap<String, String>();
		for(String attr: attributes){
			int attrIndex = getIdForAttributeName(attr);
			String v1 = line1[attrIndex].toLowerCase().replace("\"", "").replace(",  ", ", ").replace(", ", ",");;
			String v2 = line2[attrIndex].toLowerCase().replace("\"", "").replace(",  ", ", ").replace(", ", ",");;
			String valArr1Str = StringUtils.substringBetween(v1, "{", "}");
			String valArr2Str = StringUtils.substringBetween(v2, "{", "}");
			if (valArr1Str==null || valArr2Str==null) { // we assume single value, not an array of values
				if (v1.equals(v2)){
					attr_value.put(attr, v1);
				}
			}
			else {
				valArr1Str = valArr1Str.replaceAll(", ", ",");
				valArr2Str = valArr2Str.replaceAll(", ", ",");
				String[] valArr1 = valArr1Str.split(",");
				String[] valArr2 = valArr2Str.split(","); 
				List<String> valList1 = new ArrayList<String>(Arrays.asList(valArr1));
				List<String> valList2 = new ArrayList<String>(Arrays.asList(valArr2));
				valList1.retainAll(valList2);
				/* verification of coverage
				valList1.retainAll(valList2);
				
				List<String> vl1 = new ArrayList<String>(Arrays.asList(valArr1));
				valList1.retainAll(vl1); */
				
				if (!valList1.isEmpty()){
					v1 = "{"+valList1.toString().replace("["," ").replace("]", " ").trim()+"}";
					attr_value.put(attr, v1);
				}

			}		    		
		}
			return attr_value;
	}


		public boolean ruleCoversCase(Map<String, String> attr_value, String[] line){
			boolean soFarCovers = true;		
			for(String attr: attributes){
				int attrIndex = getIdForAttributeName(attr);
				String rule = attr_value.get(attr);
				if (rule == null)
					continue; // no constraint
				rule = rule.toLowerCase().replace("\"", "").replace(",  ", ",").replace(", ", ",");
				String vCase = line[attrIndex].toLowerCase().replace("\"", "").replace(",  ", ",").replace(", ", ",");
				if (vCase==null){// rule for this attribute exists but case has no value
					soFarCovers = false;
					return false;
				}
				
				String valArrCaseStr = StringUtils.substringBetween(vCase, "{", "}");
				String valArrRuleStr = StringUtils.substringBetween(rule, "{", "}");
				if (valArrCaseStr==null || valArrRuleStr==null) { // we assume single value, not an array of values
					if (!vCase.equals(rule)){
						soFarCovers = false;
						return false;
					}
				}
				else {
					String[] valArrCase = valArrCaseStr.split(",");
					String[] valArrRule = valArrRuleStr.split(","); 
					List<String> valListCase = new ArrayList<String>(Arrays.asList(valArrCase));
					List<String> valListRule = new ArrayList<String>(Arrays.asList(valArrRule));
					
					int ruleSize = valListRule.size();
					//System.out.println(valListRule);
					//System.out.println(valListCase);
					
					// rule members are subset of case
					valListRule.retainAll(valListCase);
					
					//System.out.println(valListRule);
					
					if (ruleSize != valListRule.size()){
						soFarCovers = false;
						return false;
					}
					
					
					
				}		    		
			}
			return  soFarCovers;
		}
		
		public boolean ruleCoversRule(Map<String, String> attr_value, Map<String, String> line){
			boolean soFarCovers = true;		
			for(String attr: attributes){
				int attrIndex = getIdForAttributeName(attr);
				String rule = attr_value.get(attr);
				if (rule == null)
					continue; // no constraint
				
				String vRuleBeingCovered = line.get(attr);
				if (vRuleBeingCovered==null){// rule for this attribute exists but RuleBeingCovered has no value
					soFarCovers = false;
					return false;
				}
				
				String valArrRuleBeingCoveredStr = StringUtils.substringBetween(vRuleBeingCovered, "{", "}");
				String valArrRuleStr = StringUtils.substringBetween(rule, "{", "}");
				if (valArrRuleBeingCoveredStr==null || valArrRuleStr==null) { // we assume single value, not an array of values
					if (!vRuleBeingCovered.equals(rule)){
						soFarCovers = false;
						return false;
					}
				}
				else {
					String[] valArrRuleBeingCovered = valArrRuleBeingCoveredStr.split(",");
					String[] valArrRule = valArrRuleStr.split(","); 
					List<String> valListRuleBeingCovered = new ArrayList<String>(Arrays.asList(valArrRuleBeingCovered));
					List<String> valListRule = new ArrayList<String>(Arrays.asList(valArrRule));		
					for(String r: valListRule){
						if (!strListContainsMember(valListRuleBeingCovered, r)){
							soFarCovers = false;
							return false;
						} 
					}

				}		    		
			}
			return  soFarCovers;
		}

		public Map<String, String> computeIntersection(
				Map<String, String> rule1, Map<String, String> rule2) {
			Map<String, String> attr_value = new HashMap<String, String>();
			for(String attr: attributes){
				int attrIndex = getIdForAttributeName(attr);
				String v1 = rule1.get(attr);
				String v2 = rule2.get(attr);
				if (v1==null || v2==null)
					continue;
				String valArr1Str = StringUtils.substringBetween(v1, "{", "}");
				String valArr2Str = StringUtils.substringBetween(v2, "{", "}");
				if (valArr1Str==null || valArr2Str==null) { // we assume single value, not an array of values
					if (v1.equals(v2)){
						attr_value.put(attr, v1);
					}
				}
				else {
					valArr1Str = valArr1Str.replaceAll(", ", ",");
					valArr2Str = valArr2Str.replaceAll(", ", ",");
					String[] valArr1 = valArr1Str.split(",");
					String[] valArr2 = valArr2Str.split(","); 
					List<String> valList1 = new ArrayList<String>(Arrays.asList(valArr1));
					List<String> valList2 = new ArrayList<String>(Arrays.asList(valArr2));
					valList1.retainAll(valList2);
					if (!valList1.isEmpty()){
						v1 = "{"+valList1.toString().replace("["," ").replace("]", " ").trim()+"}";
						attr_value.put(attr, v1);
					}

				}		    		
			}
				return attr_value;
		}

		private boolean strListContainsMember(List<String> valListCase, String r) {
			boolean bContains = false;
			for(String m: valListCase){
				if (m.startsWith(r) || r.startsWith(m))
					return true;
				
			}
			return false;
		}
}