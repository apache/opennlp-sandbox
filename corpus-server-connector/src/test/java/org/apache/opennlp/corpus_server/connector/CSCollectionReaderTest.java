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
import org.apache.opennlp.corpus_server.util.TestCorpusServer;
import org.apache.opennlp.corpus_server.store.CorporaStore;
import org.apache.opennlp.corpus_server.store.DerbyCorporaStore;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.metadata.MetaDataObject;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.XMLInputSource;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class CSCollectionReaderTest {

  private static final String CRDPATH = "src/test/resources/org/apache/opennlp/corpus_server/CSCollectionReaderTestDescriptor.xml";

  @BeforeClass
  public static void setUp() throws Exception {
    InputStream in = CorpusServer.class
            .getResourceAsStream("/org/apache/opennlp/corpus_server/search/TypeSystem.xml");
    TypeSystemDescription tsd = UimaUtil.createTypeSystemDescription(in);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    tsd.toXML(os);
    CorporaStore corporaStore = new DerbyCorporaStore();
    try {
      corporaStore.initialize();
      corporaStore.createCorpus("wikinews", os.toByteArray());
    } catch (Exception e) {
      // do nothing
    }
    os.reset();
    CAS cas = UimaUtil.createEmptyCAS(tsd);
    cas.setDocumentText("this is a test text");
    Annotation a = new Annotation(cas.getJCas());
    a.setBegin(0);
    a.setEnd(4);
    a.addToIndexes();
    XmiCasSerializer.serialize(cas, os);
    try {
      corporaStore.getCorpus("wikinews").addCAS("111", os.toByteArray());
      corporaStore.getCorpus("wikinews").addCAS("222", os.toByteArray());
    } catch (Exception e) {
      // do nothing
    }
    new TestCorpusServer();
  }

  @Test
  public void explicitCRTest() {
    try {
      XMLInputSource s = new XMLInputSource(new File(CRDPATH));
      ResourceSpecifier rs = UIMAFramework.getXMLParser().parseCollectionReaderDescription(s);
      CollectionReader cr = UIMAFramework.produceCollectionReader(rs);
      CAS cas = CasCreationUtils.createCas(new ArrayList<MetaDataObject>(0));
      assertTrue(cr.hasNext());
      cr.getNext(cas);
      assertNotNull(cas);
      AnnotationIndex<AnnotationFS> annotationIndex = cas.getAnnotationIndex();
      assertTrue(annotationIndex.size() == 2);
      assertTrue(cr.hasNext());
      cr.getNext(cas);
      assertFalse(cr.hasNext());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getLocalizedMessage());
    }
  }

}
