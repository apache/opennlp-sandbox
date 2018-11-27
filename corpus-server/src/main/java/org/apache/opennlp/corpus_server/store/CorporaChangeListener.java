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

/**
 * Change listener to notify about corpora changes.
 */
public interface CorporaChangeListener {

  void addedCorpus(CorpusStore store);
  
  void droppedCorpus(CorpusStore store);
  
  /**
   * Indicates that the CAS was added to the corpus.
   * 
   * @param store
   * @param casId
   */
  void addedCAS(CorpusStore store, String casId);
  
  /**
   * Indicates that a CAS was updated in the corpus.
   * 
   * @param store
   * @param casId
   */
  void updatedCAS(CorpusStore store, String casId);
  
  /**
   * Indicates that a CAS was removed from the corpus.
   * 
   * @param store
   * @param casId
   */
  void removedCAS(CorpusStore store, String casId);
}
