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

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;

/**
 * a {@link org.apache.uima.collection.CasConsumer} which puts a passed {@link CAS}
 * inside a {@link CorpusStore}
 */
public class CSCasWriter extends CasConsumer_ImplBase {

  private static final String CORPUSNAME = "corpusName";

//  private CorpusStore corpusStore;

  @Override
  public void initialize() throws ResourceInitializationException {
    super.initialize();
//    String corpusName = String.valueOf(getConfigParameterValue(CORPUSNAME));
//    try {
//      corpusStore = CorpusServer.getInstance().getStore().getCorpus(corpusName);
//    } catch (IOException e) {
//      throw new ResourceInitializationException(e);
//    }
  }

  @Override
  public void processCas(CAS cas) throws ResourceProcessException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
//    try {
//      XmiCasSerializer.serialize(cas, os);
//      corpusStore.addCAS(String.valueOf(cas.getDocumentAnnotation().getCoveredText().hashCode()), os.toByteArray());
//    } catch (Exception e) {
//      throw new ResourceProcessException(e);
//    }

  }

}