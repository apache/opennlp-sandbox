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

package org.apache.opennlp.corpus_server.connector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * a {@link org.apache.uima.collection.CollectionReader} which reads {@link CAS}s from a corpus
 * in the {@link CorpusServer}
 */
public class CSQueueCollectionReader extends CollectionReader_ImplBase {

  private static final String CORPUS_ADDRESS = "CorpusAddress";

  private static final String QUEUE_ADDRESS = "QueueAddress";
  
  private String corpusAddress;
  
  private Iterator<String> casIds;

  @Override
  public void initialize() throws ResourceInitializationException {
    super.initialize();
    
    // Retrieve corpus address ...
    corpusAddress = (String) getConfigParameterValue(CORPUS_ADDRESS);
    
    // Retrieve queue link ...
    String queueAddress = (String) getConfigParameterValue(QUEUE_ADDRESS);
    
    List<String> casIdList = new ArrayList<String>();
    
    Client client = Client.create();
    
    WebResource r = client.resource(queueAddress);
    
    while (true) {
      // TODO: Make query configurable ...
      ClientResponse response = r
              .path("_nextTask")
              .accept(MediaType.APPLICATION_JSON)
              .header("Content-Type", MediaType.TEXT_XML)
              .get(ClientResponse.class);
      
      if (response.getStatus() == ClientResponse.Status.NO_CONTENT.getStatusCode()) {
        System.out.println("##### FINISHED #####");
        break;
      }
      
      // TODO: Check if response was ok ...
      
      String casId = response.getEntity(String.class);
      casIdList.add(casId);
    }
    
    casIds = casIdList.iterator();
  }
  
  @Override
  public void getNext(CAS cas) throws IOException, CollectionException {
	  
    String casId = casIds.next();
	
    Client client = Client.create();
    
    WebResource corpusWebResource = client.resource(corpusAddress);
    
    ClientResponse casResponse = corpusWebResource
        .path(casId)
        .accept(MediaType.TEXT_XML)
        .header("Content-Type", MediaType.TEXT_XML)
        .get(ClientResponse.class);
    
    InputStream casIn = casResponse.getEntityInputStream();
	  
    UimaUtil.deserializeXmiCAS(cas, casIn);
    
    casIn.close();
  }

  @Override
  public boolean hasNext() throws IOException, CollectionException {
    
    // TODO: What to do if content for cas cannot be loaded? Skip CAS? Report error?
    
    return casIds.hasNext();
  }

  @Override
  public Progress[] getProgress() {
    return new Progress[0];
  }

  @Override
  public void close() throws IOException {
    // do nothing
  }
}
