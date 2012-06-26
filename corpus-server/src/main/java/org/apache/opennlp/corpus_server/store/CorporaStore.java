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
import java.util.Set;

/**
 * The Corpora Store is the central storage which manages the corpora.
 */
public interface CorporaStore {

  void addCorpusChangeListener(CorporaChangeListener listener);
  
  void removeCorpusChangeListener(CorporaChangeListener listener);
  
  /**
   * Initializes the corpora store. Must be called before any other method
   * of the store is called.
   */
  void initialize() throws IOException;
  
  /**
   * Creates a new corpus. 
   * <p>
   * To create a corpus a unique name must be provided to refer to it
   * and a type system to enforce that all CASes are compliant.
   * 
   * @param corpusName
   * @param typeSystemBytes
   * @param indexMapping
   */
  void createCorpus(String corpusName, byte typeSystemBytes[],
      byte indexMapping[]) throws IOException;
  
  Set<String> getCorpusIds() throws IOException;
  
  /**
   * Retrieves a corpus of the given name from the store.
   * 
   * @param corpusId the name of the corpus to retrieve
   * 
   * @return the corpus or null if it does not exist
   */
  CorpusStore getCorpus(String corpusId) throws IOException;
  
  /**
   * Drops the corpus. All data will be removed permanently.
   * 
   * @param corpusId
   * @throws IOException
   */
  void dropCorpus(String corpusId) throws IOException;
  
  /**
   * Indicates that the store will no longer be used and no more
   * methods will be called.
   */
  void shutdown() throws IOException;
}
