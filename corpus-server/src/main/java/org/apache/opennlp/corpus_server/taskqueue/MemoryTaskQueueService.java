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

package org.apache.opennlp.corpus_server.taskqueue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.corpus_server.CorpusServer;
import org.apache.opennlp.corpus_server.store.CorpusStore;

// task queue is lost, after server is restarted ...
public class MemoryTaskQueueService implements TaskQueueService {

  private Map<String, MemoryTaskQueue> queues = new HashMap<String, MemoryTaskQueue>();
  
  @Override
  public void createTaskQueue(String queueId, String corpusId, String query) {
    
    try {
      CorpusStore store = CorpusServer.getInstance().getStore().getCorpus(corpusId);
      List<String> hits = CorpusServer.getInstance().getSearchService().search(store, query);
      
      queues.put(queueId, new MemoryTaskQueue(hits));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public TaskQueue getTaskQeue(String queueId) {
    return queues.get(queueId);
  }
}
