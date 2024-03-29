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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.ParseTreeMatcherDeterministic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PhrasePatternStructure {
	private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	final int objectCount;
	final int attributeCount;
	public final List<PhraseConcept> conceptList;
	final ParseTreeMatcherDeterministic md;
	public PhrasePatternStructure(int objectCounts, int attributeCounts) {
		objectCount = objectCounts;
		attributeCount = attributeCounts;
		conceptList = new ArrayList<>();
		PhraseConcept bottom = new PhraseConcept();
		md = new ParseTreeMatcherDeterministic();
		/*Set<Integer> b_intent = new HashSet<Integer>();
		for (int index = 0; index < attributeCount; ++index) {
			b_intent.add(index);
		}
		bottom.setIntent(b_intent);*/
		bottom.setPosition(0);
		conceptList.add(bottom);
	}
	public int GetMaximalConcept(List<List<ParseTreeChunk>> intent, int Generator) {
		boolean parentIsMaximal = true;
		while(parentIsMaximal) {
			parentIsMaximal = false;
			for (int parent : conceptList.get(Generator).parents) {
				if (conceptList.get(parent).intent.containsAll(intent)) {
					Generator = parent;
					parentIsMaximal = true;
					break;
				}
			}
		}
		return Generator;
	}
	
	public int AddIntent(List<List<ParseTreeChunk>> intent, int generator) {
		LOG.debug("debug called for {}", intent);
		//printLattice();
		int generator_tmp = GetMaximalConcept(intent, generator);
		generator = generator_tmp;
		if (conceptList.get(generator).intent.equals(intent)) {
			LOG.debug("at generator: {}", conceptList.get(generator).intent);
			LOG.debug("to add: {}", intent);
			LOG.debug("already generated");
			return generator;
		}
		Set<Integer> generatorParents = conceptList.get(generator).parents;
		Set<Integer> newParents = new HashSet<>();
		for (int candidate : generatorParents) {
			if (!intent.containsAll(conceptList.get(candidate).intent)) {
				//if (!conceptList.get(candidate).intent.containsAll(intent)) {
				//Set<Integer> intersection = new HashSet<Integer>(conceptList.get(candidate).intent);
				//List<List<ParseTreeChunk>> intersection = new ArrayList<List<ParseTreeChunk>>(conceptList.get(candidate).intent);
				//intersection.retainAll(intent);
				List<List<ParseTreeChunk>> intersection = md
						.matchTwoSentencesGroupedChunksDeterministic(intent, conceptList.get(candidate).intent);
				LOG.debug("recursive call (inclusion)");
				candidate = AddIntent(intersection, candidate);
			}
			boolean addParents = true;
			LOG.debug("now iterating over parents");
			Iterator<Integer> iterator = newParents.iterator();
			while (iterator.hasNext()) {
				int parent = iterator.next();
				if (conceptList.get(parent).intent.containsAll(conceptList.get(candidate).intent)) {
					addParents = false;
					break;
				}
				else {
					if (conceptList.get(candidate).intent.containsAll(conceptList.get(parent).intent)) {
						iterator.remove();
					}
				}
			}
			/*for (int parent : newParents) {
				System.out.println("parent = " + parent);
				System.out.println("candidate intent:"+conceptList.get(candidate).intent);
				System.out.println("parent intent:"+conceptList.get(parent).intent);

				if (conceptList.get(parent).intent.containsAll(conceptList.get(candidate).intent)) {
					addParents = false;
					break;
				}
				else {
					if (conceptList.get(candidate).intent.containsAll(conceptList.get(parent).intent)) {
						newParents.remove(parent);
					}
				}
			}*/
			if (addParents) {
				newParents.add(candidate);
			}
		}
		LOG.debug("size of lattice: {}", conceptList.size());
		PhraseConcept newConcept = new PhraseConcept();
		newConcept.setIntent(intent);
		newConcept.setPosition(conceptList.size());
		conceptList.add(newConcept);
		conceptList.get(generator).parents.add(newConcept.position);
		for (int newParent: newParents) {
			if (conceptList.get(generator).parents.contains(newParent)) {
				conceptList.get(generator).parents.remove(newParent);
			}
			conceptList.get(newConcept.position).parents.add(newParent);
		}
		return newConcept.position;
	}

	public void printLatticeStats() {
		LOG.info("Lattice stats:");
		LOG.info("max_object_index = {}", objectCount);
		LOG.info("max_attribute_index = {}", attributeCount);
		LOG.info("Current concept count = {}", conceptList.size());

	}

	public void printLattice() {
		for (int i = 0; i < conceptList.size(); ++i) {
			printConceptByPosition(i);
		}
	}

	public void printConceptByPosition(int index) {
		LOG.debug("Concept at position {}", index);
		conceptList.get(index).printConcept();
	}

	public List<List<ParseTreeChunk>> formGroupedPhrasesFromChunksForPara(
			List<List<ParseTreeNode>> phrs) {
		List<List<ParseTreeChunk>> results = new ArrayList<>();
		List<ParseTreeChunk> nps = new ArrayList<>(), vps = new ArrayList<>(), pps = new ArrayList<>();
		for(List<ParseTreeNode> ps:phrs) {
			ParseTreeChunk ch = convertNodeListIntoChunk(ps);
			LOG.debug(ps.toString());
			String ptype = ps.get(0).getPhraseType();
			switch (ptype) {
				case "NP":
					nps.add(ch);
					break;
				case "VP":
					vps.add(ch);
					break;
				case "PP":
					pps.add(ch);
					break;
			}
		}
		results.add(nps); results.add(vps); results.add(pps);
		return results;
	}

	private ParseTreeChunk convertNodeListIntoChunk(List<ParseTreeNode> ps) {
		List<String> lemmas = new ArrayList<>(), poss = new ArrayList<>();
		for(ParseTreeNode n: ps) {
			lemmas.add(n.getWord());
			poss.add(n.getPos());
		}
		ParseTreeChunk ch = new ParseTreeChunk(lemmas, poss, 0, 0);
		ch.setMainPOS(ps.get(0).getPhraseType());
		return ch;
	}

}

