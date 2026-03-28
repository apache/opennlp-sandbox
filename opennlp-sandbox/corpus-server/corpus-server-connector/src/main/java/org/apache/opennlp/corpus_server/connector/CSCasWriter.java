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
import java.io.IOException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * The CSCasWriter writes a CAS into a Corpus Server.
 */
public class CSCasWriter extends CasAnnotator_ImplBase {

  private static final Logger LOG = LoggerFactory.getLogger(CSCasWriter.class);

  private String serverAddress;
  private String corpusName;

  // TODO: Make it configurable
  private final String action = "update";
  
  private Type idType;
  private Feature idFeature;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);

    serverAddress = (String) context.getConfigParameterValue(CSQueueCollectionReader.SERVER_ADDRESS);
    corpusName = (String) context.getConfigParameterValue(CSQueueCollectionReader.CORPUS_NAME);
  }

  @Override
  public void typeSystemInit(TypeSystem ts) throws AnalysisEngineProcessException {
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
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        XmiCasSerializer.serialize(cas, baos);

        byte[] xmiBytes = baos.toByteArray();

        Client c = ClientBuilder.newClient();
        WebTarget r = c.target(serverAddress + "/corpora/" + corpusName);

        Invocation.Builder casResponseBuilder = r.path(casId)
                .request(MediaType.TEXT_XML)
                .header("Content-Type", MediaType.TEXT_XML)
                .header("Content-Length", xmiBytes.length);

        if ("add".equals(action)) {
          try (Response res = casResponseBuilder.post(
                  Entity.entity(xmiBytes, MediaType.APPLICATION_OCTET_STREAM_TYPE))) {
            logResponse(res, casId);
          }
        } else if ("update".equals(action)) {
          try (Response res = casResponseBuilder.put(
                  Entity.entity(xmiBytes, MediaType.APPLICATION_OCTET_STREAM_TYPE))) {
            logResponse(res, casId);
          }
        }
        else {
          throw new AnalysisEngineProcessException(new Exception("Unknown action: " + action));
        }

      } catch (IOException | SAXException e) {
        throw new AnalysisEngineProcessException();
      }
    }
    else {
      throw new AnalysisEngineProcessException(new Exception("Missing Id Feature Structure!"));
    }
  }

  private void logResponse(Response res, String casId) {
    int statusCode = res.getStatus();
    if (statusCode >= Response.Status.BAD_REQUEST.getStatusCode()) {
      LOG.error("Error ({}), " + action + ", {}", statusCode, casId);
    } else {
      LOG.debug("OK ({}),  " + action + ", {}", statusCode, casId);
    }
  }
}
