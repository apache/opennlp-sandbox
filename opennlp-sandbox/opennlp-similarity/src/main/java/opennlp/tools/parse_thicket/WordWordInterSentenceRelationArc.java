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

package opennlp.tools.parse_thicket;

public class WordWordInterSentenceRelationArc {
	
	
		Pair<Integer, Integer> codeFrom;
		Pair<Integer, Integer> codeTo;
		String lemmaFrom;
		String lemmaTo;
		ArcType arcType;
		
		public Pair<Integer, Integer> getCodeFrom() {
			return codeFrom;
		}

		public void setCodeFrom(Pair<Integer, Integer> codeFrom) {
			this.codeFrom = codeFrom;
		}

		public Pair<Integer, Integer> getCodeTo() {
			return codeTo;
		}

		public void setCodeTo(Pair<Integer, Integer> codeTo) {
			this.codeTo = codeTo;
		}

		public String getLemmaFrom() {
			return lemmaFrom;
		}

		public void setLemmaFrom(String lemmaFrom) {
			this.lemmaFrom = lemmaFrom;
		}

		public String getLemmaTo() {
			return lemmaTo;
		}

		public void setLemmaTo(String lemmaTo) {
			this.lemmaTo = lemmaTo;
		}

		public ArcType getArcType() {
			return arcType;
		}

		public void setArcType(ArcType arcType) {
			this.arcType = arcType;
		}

		public WordWordInterSentenceRelationArc(
				Pair<Integer, Integer> codeFrom, Pair<Integer, Integer> codeTo,
				String lemmaFrom, String lemmaTo, ArcType arcType) {
			super();
			this.codeFrom = codeFrom;
			this.codeTo = codeTo;
			this.lemmaFrom = lemmaFrom;
			this.lemmaTo = lemmaTo;
			this.arcType = arcType;
		}
	
		public String toString(){
			return arcType.toString()+"&<sent="+codeFrom.getFirst()+"-word="+codeFrom.getSecond()+".."+lemmaFrom+"> ===> "+
					"<sent="+codeTo.getFirst()+"-word="+codeTo.getSecond()+".."+lemmaTo+">";
		}

}
