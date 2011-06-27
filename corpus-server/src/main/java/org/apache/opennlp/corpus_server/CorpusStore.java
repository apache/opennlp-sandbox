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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Dummy in memory corpus store.
 */
public class CorpusStore {

	private Map<String, byte[]> casStore = new HashMap<String, byte[]>();
	
	public byte[] getCAS(String casId) {
		return casStore.get(casId);
	}
	
	public void addCAS(String casID, byte[] content) {
		casStore.put(casID, content);
	}
	
	public byte[] getTypeSystem() {
		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		
		InputStream tsIn = CorpusStore.class.getResourceAsStream("/TypeSystem.xml");
		
		try {
			byte buffer[] = new byte[1024];
			int length;
			while ((length = tsIn.read(buffer)) > 0 ) {
				bytes.write(buffer, 0, length);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		return bytes.toByteArray();
	}
	
	public Collection<String> search(String query) {
		return casStore.keySet();
	}
}
