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

import org.apache.opennlp.corpus_server.CorpusServer;
import org.apache.opennlp.corpus_server.UimaUtil;
import org.apache.opennlp.corpus_server.store.CorporaStore;
import org.apache.opennlp.corpus_server.store.CorpusStore;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * a {@link org.apache.uima.collection.CollectionReader} which reads {@link CAS}s from a corpus
 * in the {@link CorpusServer}
 */
public class CSCollectionReader extends CollectionReader_ImplBase {

  private static final String CORPUSNAME = "corpusName";

  private static final String CASIDS = "casIds";

  private CorporaStore corporaStore;

  private String corpusName;

  private Iterator<String> casIds;

  @Override
  public void initialize() throws ResourceInitializationException {
    super.initialize();
    corporaStore = CorpusServer.getInstance().getStore();
    corpusName = String.valueOf(getConfigParameterValue(CORPUSNAME));
    casIds = Arrays.asList((String[])getConfigParameterValue(CASIDS)).iterator();
  }

  @Override
  public void getNext(CAS cas) throws IOException, CollectionException {
    CorpusStore corpus = corporaStore.getCorpus(corpusName);
    byte[] serializedCas = corpus.getCAS(casIds.next());
    UimaUtil.deserializeXmiCAS(cas, new ByteArrayInputStream(serializedCas));
  }

  @Override
  public boolean hasNext() throws IOException, CollectionException {
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
