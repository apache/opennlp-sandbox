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
import java.util.HashMap;
import java.util.Map;

/**
 * Dummy in memory corpus store.
 */
public class MemoryCorpusStore implements CorpusStore {

  private final String corpusName;
  private byte[] typeSystemBytes;
  private byte[] indexMapping;

  private Map<String, byte[]> casStore = new HashMap<String, byte[]>();

  MemoryCorpusStore(String corpusName, byte[] typeSystem,
      byte[] indexMapping) {
    this.corpusName = corpusName;
    this.typeSystemBytes = typeSystem;
    this.indexMapping = indexMapping;
  }

  @Override
  public String getCorpusId() {
    return corpusName;
  }

  public byte[] getCAS(String casId) {
    return casStore.get(casId);
  }

  // TODO: Add exception declaration to propagte errors back to client ...
  public void addCAS(String casID, byte[] content) {

    // Note:
    // Directly store data as xmi, but deserialization is needed to index and
    // validate it!

//    TypeSystemDescription tsDescription = UimaUtil.createTypeSystemDescription(
//        new ByteArrayInputStream(typeSystemBytes));
//    
//    CAS cas = UimaUtil.createEmptyCAS(tsDescription);
//
//    try {
//      UimaUtil.deserializeXmiCAS(cas, new ByteArrayInputStream(content));
//    } catch (IOException e) {
//      // TODO: Send error back to client ...
//      e.printStackTrace();
//    }

    casStore.put(casID, content);
  }

  @Override
  public void updateCAS(String casID, byte[] content) throws IOException {
    addCAS(casID, content);
  }

  @Override
  public void removeCAS(String casID) throws IOException {
    casStore.remove(casID);
  }
  
  @Override
  public void replaceTypeSystem(byte[] newTypeSystem) throws IOException {
    typeSystemBytes = newTypeSystem;
  }
  
  public byte[] getTypeSystem() {
    return typeSystemBytes;
  }

  @Override
  public byte[] getIndexMapping() throws IOException {
    return indexMapping;
  }
}
