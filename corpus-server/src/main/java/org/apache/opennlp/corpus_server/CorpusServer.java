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

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.opennlp.corpus_server.store.CorporaStore;
import org.apache.opennlp.corpus_server.store.DerbyCorporaStore;

public class CorpusServer implements ServletContextListener {

  private final static Logger LOGGER = Logger.getLogger(
      CorpusServer.class .getName());
  
  private static CorpusServer instance;
  
  private CorporaStore store;
  
  @Override
  public void contextInitialized(ServletContextEvent sce) {
    
    instance = this;
    store = new DerbyCorporaStore();
    try {
      store.initialize();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    if (store != null)
      try {
        store.shutdown();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
  }

  public CorporaStore getStore() {
    return store;
  }
  
  public static CorpusServer getInstance() {
    return instance;
  }
}
