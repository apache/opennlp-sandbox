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

package org.apache.opennlp.corpus_server.search;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.opennlp.corpus_server.UimaUtil;
import org.apache.opennlp.corpus_server.store.CorpusStore;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;

public class LuceneSearchService implements SearchService {

  private final static Logger LOGGER = Logger.getLogger(
      LuceneSearchService.class .getName());
  
  private AnalysisEngine indexer;
  AnalysisEngineDescription specifier;
  
  // create a map with corpus name and indexer ae ...
  // indexer ae is a pair of ae and descriptor (maybe that can be done nicer)
  
  @Override
  public void initialize() throws IOException {
    
    // TODO: We need an indexer per corpus ... 
    // For each corpus ...
    
    
    //get Resource Specifier from XML file
    XMLInputSource in = new XMLInputSource(LuceneSearchService.class.getResourceAsStream(
        "/org/apache/opennlp/corpus_server/search/LuceneIndexer.xml"), new File(""));
    
    try {
      specifier = (AnalysisEngineDescription) UIMAFramework.getXMLParser().parseResourceSpecifier(in);
      
      // TODO: Place mapping in a tmp file, and then set the path to it ...
      // How to modify this parameter?!
      // TODO: Specify the storage location for the index ...
      
      indexer = UIMAFramework.produceAnalysisEngine(specifier); // NOTE: This will use the wrong type system ... 
    } catch (InvalidXMLException e) {
      throw new IOException(e);
    } catch (ResourceInitializationException e) {
      throw new IOException(e);
    }
  }

  
  @Override
  public void index(CorpusStore store, String casId) throws IOException {
    
    // Get a reference to the indexer for this corpus ...
    
    // TODO: Need to take care for thread safety ..
    
    // TODO: AE type system must be merged with corpus type system .. 
    // CasCreationUtils.createCas(list);
    // Does that also work with a type system description?!
    List specs = new ArrayList();
    specs.add(indexer); // Note: Is this allowed?
    specs.add(store.getTypeSystem());
    
    CAS cas;
    try {
      cas = CasCreationUtils.createCas(specs);
    } catch (ResourceInitializationException e) {
      throw new IOException(e);
    }
    
    byte[] casBytes = store.getCAS(casId);
    
    UimaUtil.deserializeXmiCAS(cas, new ByteArrayInputStream(casBytes));
    
    try {
      indexer.process(cas);
    } catch (AnalysisEngineProcessException e) {
      LOGGER.log(Level.SEVERE, "Failed to index CAS: " + casId, e);
    }
    
    System.out.println("Index: " + casId);
  }

  @Override
  public Collection<String> search(CorpusStore store, String q)
      throws IOException {
    
    // query index ...
    
    return Collections.emptySet();
  }

  @Override
  public void shutdown() throws IOException {
    if (indexer != null) {
      indexer.destroy();
    }
  }
}
