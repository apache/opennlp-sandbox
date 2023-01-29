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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.opennlp.corpus_server.CorpusServer;
import org.apache.opennlp.corpus_server.CorpusServerBundle;
import org.apache.opennlp.corpus_server.store.CorpusStore;

/**
 * In memory task queue. Contents of the queue is lost when the
 * server restarts.
 */
public class MemoryTaskQueueService implements TaskQueueService {

  private final static Logger LOGGER = Logger.getLogger(
      MemoryTaskQueueService.class .getName());

  private Map<String, MemoryTaskQueue> queues = new HashMap<String, MemoryTaskQueue>();

  @Override
  public void createTaskQueue(String queueId, String corpusId, String query) {

    try {
      CorpusServer corpusServer = CorpusServerBundle.getInstance().getCorpusServer();
      
      CorpusStore store = corpusServer.getStore().getCorpus(corpusId);
      List<String> hits = corpusServer.getSearchService().search(store, query);

      queues.put(queueId, new MemoryTaskQueue(hits));

      if (LOGGER.isLoggable(Level.INFO)) {
        LOGGER.log(Level.INFO, "Created queue " + queueId +
            " with " + hits.size() + "CASes.");
      }
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to create task queue: " + queueId, e);
    }
  }

  @Override
  public TaskQueue getTaskQeue(String queueId) {
    return queues.get(queueId);
  }
}
