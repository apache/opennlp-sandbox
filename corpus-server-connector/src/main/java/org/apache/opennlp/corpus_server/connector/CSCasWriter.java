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

import java.io.ByteArrayOutputStream;

import javax.ws.rs.core.MediaType;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.xml.sax.SAXException;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;

/**
 * The CSCasWriter writes a CAS into a Corpus Server.
 */
public class CSCasWriter extends CasAnnotator_ImplBase {

  private String serverAddress;
  private String corpusName;

  // TODO: Make it configurable
  private String action = "update";
  
  private Type idType;
  private Feature idFeature;
  private Logger logger;

  @Override
  public void initialize(UimaContext context)
      throws ResourceInitializationException {
    super.initialize(context);

    serverAddress = (String) context.getConfigParameterValue(CSQueueCollectionReader.SERVER_ADDRESS);
    corpusName = (String) context.getConfigParameterValue(CSQueueCollectionReader.CORPUS_NAME);
    
    logger = context.getLogger();
  }

  @Override
  public void typeSystemInit(TypeSystem ts)
      throws AnalysisEngineProcessException {
    super.typeSystemInit(ts);

    String idTypeName = (String) getContext().getConfigParameterValue("IdFSTypeName");
    idType = ts.getType(idTypeName);
    String idFeatureName = (String) getContext().getConfigParameterValue("IdFeatureName");
    idFeature = idType.getFeatureByBaseName(idFeatureName);
  }

  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    
    FSIterator<FeatureStructure> typeFSIter = cas.getIndexRepository().getAllIndexedFS(idType);

    if (typeFSIter.hasNext()) {
      FeatureStructure idFs = typeFSIter.next();

      String casId = idFs.getFeatureValueAsString(idFeature);

      // TODO: Remove the FS here, so its client side only!
      // Was inserted in the reader ...
      cas.removeFsFromIndexes(idFs);
      
      ByteArrayOutputStream xmiBytes = new ByteArrayOutputStream();
      XmiCasSerializer serializer = new XmiCasSerializer(cas.getTypeSystem());
      try {
        serializer.serialize(cas, xmiBytes);
      } catch (SAXException e) {
        throw new AnalysisEngineProcessException();
      }
      
      Client client = Client.create();
      
      WebResource corpusWebResource = client.resource(serverAddress + "/corpora/"
          + corpusName);
      
      Builder casResponseBuilder = corpusWebResource.path(casId)
          .accept(MediaType.TEXT_XML).header("Content-Type", MediaType.TEXT_XML);
      
      ClientResponse response;
      if ("add".equals(action)) {
        response = casResponseBuilder.post(ClientResponse.class, xmiBytes);
      }
      else if ("update".equals(action)) {
        response = casResponseBuilder.put(ClientResponse.class, xmiBytes);
      }
      else {
        throw new AnalysisEngineProcessException(new Exception("Unkown action: " + action));
      }
      
      int statusCode = response.getStatus();
      
      if (statusCode > 400) {
        if (logger.isLoggable(Level.SEVERE)) {
          logger.log(Level.SEVERE, "Error (" + statusCode + "), " + action + ", " + casId);
        }
      }
      else {
        if (logger.isLoggable(Level.FINE)) {
          logger.log(Level.FINE, "OK (" + statusCode + "),  " + action + ", " + casId);
        }
      }
    }
    else {
      throw new AnalysisEngineProcessException(new Exception("Missing Id Feature Structure!"));
    }
  }
}
