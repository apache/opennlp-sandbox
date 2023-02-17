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

package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.stemmer.PStemmer;
import opennlp.tools.word2vec.W2VDistanceMeasurer;

public class LemmaGeneralizer implements IGeneralizer<String> {
	public static final String w2vPrefix = "w2v_";
	final PStemmer ps = new PStemmer();
	String pos = null;
	final W2VDistanceMeasurer w2v;
	public LemmaGeneralizer() {
		w2v = W2VDistanceMeasurer.getInstance();
    }

	public void setPOS(String posToSet){
		this.pos = posToSet;
	}

	@Override
	public List<String> generalize(Object o1, Object o2) {
		List<String> results = new ArrayList<>();
		boolean bEqual = false;

		String lemma1 = (String)o1, lemma2 = (String)o2;
	
			
			lemma1 = lemma1.toLowerCase();
			lemma2 = lemma2.toLowerCase();

			if (lemma1.equals(lemma2)) {
				bEqual = true;
				results.add(lemma1);
				return results;
			}


			if ((lemma1.equals(lemma2 + "s") || lemma2.equals(lemma1 + "s"))
					|| lemma1.endsWith(lemma2) || lemma2.endsWith(lemma1)
					|| lemma1.startsWith(lemma2) || lemma2.startsWith(lemma1)) {
				bEqual = true;
				results.add(lemma1);
				return results;
			}

			try {
				if (ps != null) {
					if (ps.stem(lemma1).toString()
							.equalsIgnoreCase(ps.stem(lemma2).toString())) {
						bEqual = true;
						results.add(lemma1);
						return results;
					}
				}
			} catch (Exception e) {
				System.err.println("Problem processing " + lemma1 + " " + lemma2);
				return results;
			}
			// if different words, then compute word2vec distance and write the value as a string
			if (w2v.vec!=null){
				double value = w2v.vec.similarity(lemma1,  lemma2);
				results.add(w2vPrefix + value);
			}
			return results;
		}



		




	}
