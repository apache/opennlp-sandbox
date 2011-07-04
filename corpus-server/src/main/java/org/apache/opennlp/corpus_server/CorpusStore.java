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

package org.apache.opennlp.corpus_server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.uima.resource.metadata.TypeSystemDescription;

/**
 * Dummy in memory corpus store.
 */
public class CorpusStore {

	private final String corpusName;
	private final TypeSystemDescription typeSystem;
	
	private Map<String, byte[]> casStore = new HashMap<String, byte[]>();
	
	CorpusStore(String corpusName, TypeSystemDescription typeSystem) {
		this.corpusName = corpusName;
		this.typeSystem = typeSystem;
	}
	
	public byte[] getCAS(String casId) {
		return casStore.get(casId);
	}
	
	// TODO: Add exception declaration to propagte errors back to client ...
	public void addCAS(String casID, byte[] content) {
		
		// Note:
		// Directly store data as xmi, but deserialization is needed to index and validate it!
		
		CAS cas = UimaUtil.createEmptyCAS(typeSystem);
		
		try {
			UimaUtil.deserializeXmiCAS(cas, new ByteArrayInputStream(content));
		} catch (IOException e) {
			// TODO: Send error back to client ...
			e.printStackTrace();
		}
		
		casStore.put(casID, content);
	}
	
	public TypeSystemDescription getTypeSystem() {
		return typeSystem;
	}
	
	public Collection<String> search(String query) {
		return casStore.keySet();
	}
}
