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

package org.apache.opennlp.corpus_server.store;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MemoryCorporaStore extends AbstractCorporaStore {
	
	private static MemoryCorporaStore instance;
	
	private Map<String, MemoryCorpusStore> corpora = new HashMap<String, MemoryCorpusStore>();
	
	public void initialize() {
	}
	
	public void shutdown() {
	}
	
	public static synchronized MemoryCorporaStore getStore() {
		
		if (instance == null) {
			instance = new MemoryCorporaStore();
		}
		
		return instance;
	}
	
	@Override
	public Set<String> getCorpusIds() throws IOException {
	  return Collections.unmodifiableSet(corpora.keySet());
	}
	
	// Note: Add one twice, overwrites an existing one!
	public void createCorpus(String corpusName,
			byte typeSystemBytes[], byte indexMapping[]) {
		corpora.put(corpusName, new MemoryCorpusStore(corpusName, 
				typeSystemBytes, indexMapping));
	}
	
	public MemoryCorpusStore getCorpus(String corpusId) {
		return corpora.get(corpusId);
	}
	
	@Override
	public void dropCorpus(String corpusId) throws IOException {
	  corpora.remove(corpusId);
	}
}
