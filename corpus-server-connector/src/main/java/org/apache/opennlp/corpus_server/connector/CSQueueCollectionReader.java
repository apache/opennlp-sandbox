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
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.Progress;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * a {@link org.apache.uima.collection.CollectionReader} which reads {@link CAS}s from a corpus
 * in the {@link CorpusServer}
 */
public class CSQueueCollectionReader extends CollectionReader_ImplBase {

  static final String SERVER_ADDRESS = "ServerAddress";
  
  static final String CORPUS_NAME = "CorpusName";

  private static final String SEARCH_QUERY = "SearchQuery";
  
  private static final String QUEUE_NAME = "QueueName";
  
  private String serverAddress;
  
  private String corpusName;
  
  private Type idType;
  private Feature idFeature;
  
  private Iterator<String> casIds;

  private Logger logger;


  @Override
  public void initialize() throws ResourceInitializationException {
    super.initialize();
    
    logger = getLogger();
    
    serverAddress = (String) getConfigParameterValue(SERVER_ADDRESS);
    
    // Retrieve corpus address ...
    corpusName = (String) getConfigParameterValue(CORPUS_NAME);
    
    String queueName = (String) getConfigParameterValue(QUEUE_NAME);
    
    String searchQuery = (String) getConfigParameterValue(SEARCH_QUERY);
    
    Client client = Client.create();
    
    // Create a queue if the search query is specified
    if (searchQuery != null) {
      WebResource r = client.resource(serverAddress + "/queues/");

      ClientResponse response = r.path("_createTaskQueue")
          .queryParam("corpusId", corpusName)
          .queryParam("queueId", queueName)
          .queryParam("q", searchQuery)
          .accept(MediaType.TEXT_XML)
          // TODO: How to fix this? Shouldn't accept do it?
          .header("Content-Type", MediaType.TEXT_XML)
          .post(ClientResponse.class);
      
      if (response.getStatus() != 204) {
    	  throw new ResourceInitializationException(
    			  new Exception("Failed to create queue: " + response.getStatus()));
      }
      
      if (logger.isLoggable(Level.INFO)) {
        logger.log(Level.INFO, "Sucessfully created queue: " + queueName + " for corpus: " + corpusName);
      }
    }
    
    // Retrieve queue link ...
    
    List<String> casIdList = new ArrayList<String>();
    
    
    WebResource r = client.resource(serverAddress +  "/queues/" + queueName);
    
    while (true) {
      System.out.println("Requesting next CAS ID!");
    	
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
      else {
      // TODO: Check if response was ok ...
      }
      String casId = response.getEntity(String.class);
      casIdList.add(casId);
      
      System.out.println("Received CAS ID: " + casId);
    }
    
    casIds = casIdList.iterator();
  }
  
  @Override
  public void typeSystemInit(TypeSystem ts)
      throws ResourceInitializationException {
    super.typeSystemInit(ts);
    
    String idTypeName = (String) getConfigParameterValue("IdFSTypeName");
    idType = ts.getType(idTypeName);
    String idFeatureName = (String) getConfigParameterValue("IdFeatureName");
    idFeature = idType.getFeatureByBaseName(idFeatureName);
  }
  
  @Override
  public void getNext(CAS cas) throws IOException, CollectionException {
	  
    String casId = casIds.next();
	
    
    Client client = Client.create();
    
    WebResource corpusWebResource = client.resource(serverAddress + "/corpora/" + corpusName);
    
    ClientResponse casResponse = corpusWebResource
        .path(casId)
        .accept(MediaType.TEXT_XML)
        .header("Content-Type", MediaType.TEXT_XML)
        .get(ClientResponse.class);
    
    InputStream casIn = casResponse.getEntityInputStream();
    
    try {
      UimaUtil.deserializeXmiCAS(cas, casIn);
    }
    catch (IOException e) {
      if (logger.isLoggable(Level.SEVERE)) {
        logger.log(Level.SEVERE, "Failed to load CAS: " +  casId + " code: " + casResponse.getStatus());
      }
      
      throw e;
    }
    finally {
      casIn.close();
    }
    
    if (idType != null && idFeature != null) {
      FeatureStructure idFS = cas.createFS(idType);
      idFS.setStringValue(idFeature, casId);
      cas.addFsToIndexes(idFS);
    }
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
