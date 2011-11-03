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

package org.apache.opennlp.corpus_server.caseditor;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONArray;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * A job to retrieve search results from the corpus server.
 */
public class SearchCorpusServerJob extends Job {

  private String serverAddress;
  private String searchQuery;
  private JSONArray searchResult;
  
  public SearchCorpusServerJob() {
    super("Search Job");
  }

  public void setServerAddress(String serverPath) {
    serverAddress = serverPath;
  }
  
  public void setQuery(String query) {
    searchQuery = query;
  }
  
  @Override
  protected IStatus run(IProgressMonitor monitor) {
    
    Client c = Client.create();
    c.setConnectTimeout(10000);
    WebResource r = c.resource(serverAddress);
    
    ClientResponse response;
    
    try {
      response = r
          .path("_search")
          .queryParam("q", searchQuery)
          .accept(MediaType.APPLICATION_JSON)
          .get(ClientResponse.class);
    }
    catch (com.sun.jersey.api.client.ClientHandlerException e) {
      return new Status(IStatus.WARNING, CorpusServerPlugin.PLUGIN_ID, "Failed to connect to server!");
    }
    
    if (response.getClientResponseStatus().getStatusCode() != 200) {
      return new Status(IStatus.WARNING, CorpusServerPlugin.PLUGIN_ID, "Failed to retrieve results from server!");
    }
    
    searchResult = response.getEntity(JSONArray.class);
    
    return new Status(IStatus.OK, CorpusServerPlugin.PLUGIN_ID, "OK");
  }
  
  JSONArray getSearchResult() {
    return searchResult;
  }
}
