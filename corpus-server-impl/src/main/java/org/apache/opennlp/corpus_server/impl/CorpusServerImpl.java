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

package org.apache.opennlp.corpus_server.impl;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.opennlp.corpus_server.CorpusServer;
import org.apache.opennlp.corpus_server.search.SearchService;
import org.apache.opennlp.corpus_server.store.CorporaChangeListener;
import org.apache.opennlp.corpus_server.store.CorporaStore;
import org.apache.opennlp.corpus_server.store.CorpusStore;
import org.apache.opennlp.corpus_server.taskqueue.MemoryTaskQueueService;
import org.apache.opennlp.corpus_server.taskqueue.TaskQueueService;

public class CorpusServerImpl implements CorpusServer {

  static class IndexListener implements CorporaChangeListener {

    private final SearchService searchService;

    IndexListener(SearchService searchService) {
      this.searchService = searchService;
    }

    @Override
    public void addedCAS(CorpusStore store, String casId) {
      try {
        searchService.index(store, casId);
      } catch (IOException e) {
        // TODO: Also log store name!
        LOGGER.log(Level.WARNING, "Failed to index cas: " + casId, e);
      }
    }

    @Override
    public void droppedCorpus(CorpusStore store) {
      try {
        searchService.dropIndex(store);
      } catch (IOException e) {
        // TODO: Also log store name!
        LOGGER.log(Level.WARNING, "Failed to index cas: " + store.getCorpusId(), e);
      }
    }
    
    @Override
    public void updatedCAS(CorpusStore store, String casId) {
      addedCAS(store, casId);
    }

    @Override
    public void addedCorpus(CorpusStore store) {
      try {
        searchService.createIndex(store);
      } catch (IOException e) {
        LOGGER.log(Level.WARNING,
            "Failed to create index: " + store.getCorpusId(), e);
      }
    }

    @Override
    public void removedCAS(CorpusStore store, String casId) {
      try {
        searchService.removeFromIndex(store, casId);
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Failed to remove cas " + casId
            + "from  index " + store.getCorpusId(), e);
      }
    }
  }

  private final static Logger LOGGER = Logger.getLogger(CorpusServerImpl.class
      .getName());
  
  private CorporaStore store;

  private SearchService searchService;

  private MemoryTaskQueueService taskQueueService;

  private IndexListener indexListener;

  public void start() {
    store = new DerbyCorporaStore();
    try {
      store.initialize();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to start corpora store!", e);
      return;
    }
    
    LOGGER.info("Successfully loaded database.");
    
    searchService = new LuceneSearchService();
    
    try {
      searchService.initialize(store);
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to start search service!", e);
      return;
    }

    LOGGER.info("Successfully started search service.");

    indexListener = new IndexListener(searchService);
    store.addCorpusChangeListener(indexListener);
    
    taskQueueService = new MemoryTaskQueueService();
  }
  
  public void stop() {
    taskQueueService = null;
    
  // Note: 
  // Everything should be shutdown in the opposite
  // order than the startup.
  
  taskQueueService = null;
  
  if (store != null && indexListener != null) {
    store.removeCorpusChangeListener(indexListener);
  }
  
  if (searchService != null) {
    try {
      searchService.shutdown();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to shutdown search service!", e);
    }
  }
  
  if (store != null) {
    try {
      store.shutdown();
    } catch (IOException e) {
      LOGGER.log(Level.SEVERE, "Failed to shutdown corpora store!", e);
    }
  }
    
  }
  
  public CorporaStore getStore() {
    return store;
  }

  public SearchService getSearchService() {
    return searchService;
  }

  public TaskQueueService getTaskQueueService() {
    return taskQueueService;
  }
}
