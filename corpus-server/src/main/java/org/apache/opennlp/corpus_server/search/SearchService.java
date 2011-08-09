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

package org.apache.opennlp.corpus_server.search;

import java.io.IOException;
import java.util.Collection;

import org.apache.opennlp.corpus_server.store.CorpusStore;

public interface SearchService {

  void initialize() throws IOException;
  
  // index
  void index(CorpusStore store, String casId) throws IOException;
  
  Collection<String> search(CorpusStore store, String q) throws IOException;
  
  void shutdown() throws IOException;
  
}
