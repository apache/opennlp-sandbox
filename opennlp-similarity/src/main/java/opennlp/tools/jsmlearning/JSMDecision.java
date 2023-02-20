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

package opennlp.tools.jsmlearning;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.textsimilarity.ParseTreeChunk;

public class JSMDecision {
	String keywordClassName;
	Boolean bPositiveClass;
	List<List<List<ParseTreeChunk>>> posHypotheses,
			negHypotheses;
	List<List<List<ParseTreeChunk>>> posIntersectionsUnderNeg , 
			negIntersectionsUnderPos;
	private String[] separationKeywords;
	
	public String[] getSeparationKeywords() {
		return separationKeywords;
	}
	public void setSeparationKeywords(String[] separationKeywords) {
		this.separationKeywords = separationKeywords;
	}
	public String getKeywordClassName() {
		return keywordClassName;
	}
	public void setKeywordClassName(String keywordClassName) {
		this.keywordClassName = keywordClassName;
	}
	public Boolean getbPositiveClass() {
		return bPositiveClass;
	}
	public void setbPositiveClass(Boolean bPositiveClass) {
		this.bPositiveClass = bPositiveClass;
	}
	public List<List<List<ParseTreeChunk>>> getPosHypotheses() {
		return posHypotheses;
	}
	public void setPosHypotheses(List<List<List<ParseTreeChunk>>> posHypotheses) {
		this.posHypotheses = posHypotheses;
	}
	public List<List<List<ParseTreeChunk>>> getNegHypotheses() {
		return negHypotheses;
	}
	public void setNegHypotheses(List<List<List<ParseTreeChunk>>> negHypotheses) {
		this.negHypotheses = negHypotheses;
	}
	public List<List<List<ParseTreeChunk>>> getPosIntersectionsUnderNeg() {
		return posIntersectionsUnderNeg;
	}
	public void setPosIntersectionsUnderNeg(
			List<List<List<ParseTreeChunk>>> posIntersectionsUnderNeg) {
		this.posIntersectionsUnderNeg = posIntersectionsUnderNeg;
	}
	public List<List<List<ParseTreeChunk>>> getNegIntersectionsUnderPos() {
		return negIntersectionsUnderPos;
	}
	public void setNegIntersectionsUnderPos(
			List<List<List<ParseTreeChunk>>> negIntersectionsUnderPos) {
		this.negIntersectionsUnderPos = negIntersectionsUnderPos;
	}
	public JSMDecision(String keywordClassName, Boolean bPositiveClass,
			List<List<List<ParseTreeChunk>>> posHypotheses,
			List<List<List<ParseTreeChunk>>> negHypotheses,
			List<List<List<ParseTreeChunk>>> posIntersectionsUnderNeg,
			List<List<List<ParseTreeChunk>>> negIntersectionsUnderPos, String[] separationKeywords) {
		super();
		this.keywordClassName = keywordClassName;
		this.bPositiveClass = bPositiveClass;
		this.posHypotheses = posHypotheses;
		this.negHypotheses = negHypotheses;
		this.posIntersectionsUnderNeg = posIntersectionsUnderNeg;
		this.negIntersectionsUnderPos = negIntersectionsUnderPos;
		this.separationKeywords = separationKeywords;
	}
	
	

}
