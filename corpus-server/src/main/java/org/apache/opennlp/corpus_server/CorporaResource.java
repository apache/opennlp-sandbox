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

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.opennlp.corpus_server.store.CorporaStore;

@Path("/corpora")
public class CorporaResource {
	
	/**
	 * Creates a new corpus.
	 * <br>
	 * Note: Type system references are not supported currently!
	 * 
	 * @param casId
	 * @param typeSystemBytes
	 */
	@POST
	@Consumes(MediaType.TEXT_XML)
	@Path("_createCorpus")
	public void createCorpus(@QueryParam("corpusName") String corpusName,
			byte[] typeSystemBytes) throws IOException {
	  
	  CorpusServer corpusServer = CorpusServer.getInstance();
	  CorporaStore store = corpusServer.getStore();
	  
	  store.createCorpus(corpusName, typeSystemBytes);
	}

  @Path("{corpus}")
	public CorpusResource getCorpus(
			@PathParam("corpus") String corpus) throws IOException {
    
      CorpusServer corpusServer = CorpusServer.getInstance();
      CorporaStore store = corpusServer.getStore();
    
      return new CorpusResource(store.getCorpus(corpus));
	}
}
