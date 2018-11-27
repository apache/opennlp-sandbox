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

/**
 * A Corpus Store contains a set of CASes and is responsible to host them
 * together with a type system.
 */
public interface CorpusStore {
  
  /**
   * Retrieves the unique id of the corpus.
   * 
   * @return the corpus id
   */
  String getCorpusId();
  
  /**
   * Retrieves a CAS for a given id.
   * 
   * @param casId the id of the CAS to retrieve
   * 
   * @return the CAS
   * 
   * @throws IOException if retrieving the CAS is not possible
   */
  byte[] getCAS(String casId) throws IOException;
  
  /**
   * Adds a CAS to the corpus with the given id. 
   * 
   * @param casID the id of the new CAS
   * @param content the CAS in the XMI format
   * 
   * @throws IOException if storing the CAS is not possible
   */
  void addCAS(String casID, byte[] content) throws IOException;
  
  /**
   * Updates the XMI content of an existing CAS.
   * 
   * @param casID the id of the CAS to update
   * @param content the new content
   * 
   * @throws IOException if updating the CAS fails
   */
  void updateCAS(String casID, byte[] content) throws IOException;
  
  /**
   * Removes a CAS of the given id from the corpus.
   * 
   * @param casID
   * @throws IOException
   */
  void removeCAS(String casID) throws IOException;
  
  /**
   * Replaces the existing Type System with a new one.
   * 
   * @param newTypeSystem
   * @throws IOException
   */
  void replaceTypeSystem(byte[] newTypeSystem) throws IOException;
  
  /**
   * Retrieves the type system description of this corpus.
   * 
   * @return
   * @throws IOException
   */
  byte[] getTypeSystem() throws IOException;
  
  /**
   * Retrieves the index mapping for this corpus.
   * 
   * @return
   * @throws IOException
   */
  byte[] getIndexMapping() throws IOException;
}
