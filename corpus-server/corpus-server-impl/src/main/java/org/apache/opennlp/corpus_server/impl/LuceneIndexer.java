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

package org.apache.opennlp.corpus_server.impl;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.lucas.LuceneDocumentAE;
import org.apache.uima.lucas.indexer.IndexWriterProvider;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

public class LuceneIndexer extends LuceneDocumentAE {

  static final String CAS_ID_TYPE = "org.apache.opennlp.corpus_server.CasId";
  static final String CAS_ID_FEEATURE = "id";
  
  private static final String RESOURCE_INDEX_WRITER_PROVIDER = "indexWriterProvider";
  
  private Type casIdType;
  private Feature casIdFeature;
  
  private IndexWriter indexWriter;
  
  @Override
  public void initialize(UimaContext aContext)
      throws ResourceInitializationException {
    super.initialize(aContext);
    
    IndexWriterProvider indexWriterProvider;
    try {
      indexWriterProvider = (IndexWriterProvider) aContext
          .getResourceObject(RESOURCE_INDEX_WRITER_PROVIDER);
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }
    
    indexWriter = indexWriterProvider.getIndexWriter();
  }
  
  @Override
  public void typeSystemInit(TypeSystem aTypeSystem)
      throws AnalysisEngineProcessException {
    super.typeSystemInit(aTypeSystem);
    
    casIdType = aTypeSystem.getType(CAS_ID_TYPE);
    casIdFeature = casIdType.getFeatureByBaseName(CAS_ID_FEEATURE);
  }
  
  @Override
  public void process(CAS cas) throws AnalysisEngineProcessException {
    
    String casId = null;
    
    for (FSIterator<FeatureStructure> casIdIterator = 
        cas.getIndexRepository().getAllIndexedFS(casIdType); casIdIterator.hasNext();) {
      FeatureStructure casIdFS = casIdIterator.next();
      
      casId = casIdFS.getStringValue(casIdFeature);
    }
    
    if (casId == null)
      throw new AnalysisEngineProcessException(new Exception("Missing cas id feature structure!"));
    
    Query idQuery = new TermQuery(new Term(LuceneSearchService.LUCENE_ID_FIELD, casId));
    
    // Note: A CAS with a null document text is removed from the index.
    // This is used to remove a CAS from an index!
    
    try {
      indexWriter.deleteDocuments(idQuery);
      
      if (cas.getDocumentText() != null) {
        Document doc = createDocument(cas);
        doc.add(new Field(LuceneSearchService.LUCENE_ID_FIELD,
            casId, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        indexWriter.addDocument(doc);
      }
      
      // TODO: Commit handling might need to be changed
      indexWriter.commit();
    } catch (CorruptIndexException e) {
      throw new AnalysisEngineProcessException(e);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
  }
  
  @Override
  public void destroy() {
    super.destroy();
    
    try {
      indexWriter.close();
    } catch (CorruptIndexException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    indexWriter = null;
  }
}
