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

import java.util.*;
import java.io.*;

import opennlp.tools.fca.FormalConcept;
import opennlp.tools.textsimilarity.ParseTreeChunk;


public class PhraseConcept {
	int position;
	public List<List<ParseTreeChunk>> intent;
	Set<Integer> parents;
	Set<Integer> childs;
	Set<Integer> extent;
	
	double intLogStabilityBottom = 0;
	double intLogStabilityUp = 0;
	
	
	public PhraseConcept() {
		position = -1;
		intent = new ArrayList<List<ParseTreeChunk>>();
		parents = new HashSet<Integer>();
		extent = new HashSet<Integer>();
		childs = new HashSet<Integer>();
	}
	public void setPosition( int newPosition ){
	       position = newPosition;
	}
	public void setIntent( List<List<ParseTreeChunk>> newIntent ){
	       intent.clear();
	       intent.addAll(newIntent);
	}
	public void setParents( Set<Integer> newParents ){
	       //parents = newParents;
		parents.clear();
		parents.addAll(newParents);
	}
	public void printConcept() {
		System.out.println("Concept position:" + position);
		System.out.println("Concept intent:" + intent);
		System.out.println("Concept parents:" + parents);
	}
	
	public void printConceptExtended() {
		System.out.println("Concept position:" + position);
		System.out.println("Concept intent:" + intent);
		System.out.println("Concept extent:" + extent);
		System.out.println("Concept parents:" + parents);
		System.out.println("Concept parents:" + childs);
		System.out.println("log stab: ["+ intLogStabilityBottom + "; "+intLogStabilityUp+"]");		
	}
	
	public void addExtents(LinkedHashSet<Integer> ext){
		extent.addAll(ext);
}
	
	
	 public static void main(String []args) {
		 PhraseConcept c = new PhraseConcept();
		 c.printConcept();
		 c.setPosition(10);
		 c.printConcept();
		 c.printConcept();

	 }
}
