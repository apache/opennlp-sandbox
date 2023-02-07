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
import org.apache.opennlp.corpus_server.impl.DerbyCorporaStore;
import org.apache.opennlp.corpus_server.store.CorporaStore;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.XMLInputSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Disabled
class CSCollectionReaderTest extends AbstractCSTest {

  private static final String CRDPATH = "/CSCollectionReaderTestDescriptor.xml";

  @BeforeAll
  static void setUp() throws IOException {
    // kick out the old db instances that might be present already in this environment
    cleanTestDB();

    // init the new Derby instance with schema and demo data
    try (InputStream in = CSCollectionReaderTest.class.getResourceAsStream("/TypeSystem.xml");
         ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      TypeSystemDescription tsd = UimaUtil.createTypeSystemDescription(in);
      tsd.toXML(os);
      CorporaStore corporaStore = new DerbyCorporaStore();

      corporaStore.initialize(BASE_PATH.replace("file:", "").replace("/test-classes", ""));
      byte[] indexMapping = new byte[] {};
      corporaStore.createCorpus("wikinews", os.toByteArray(), indexMapping);

      os.reset();
      CAS cas = UimaUtil.createEmptyCAS(tsd);
      cas.setDocumentText("this is a test text");
      Annotation a = new Annotation(cas.getJCas());
      a.setBegin(0);
      a.setEnd(4);
      a.addToIndexes();
      XmiCasSerializer.serialize(cas, os);

      corporaStore.getCorpus("wikinews").addCAS("111", os.toByteArray());
      corporaStore.getCorpus("wikinews").addCAS("222", os.toByteArray());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getLocalizedMessage());
    }
  }

  @Test
  @Disabled
    // TODO Investigate why this test fails with:
    //  No value has been assigned to the mandatory configuration parameter corpusName.
  void explicitCRTest() {
    try {
      XMLInputSource s = new XMLInputSource(CSCollectionReaderTest.class.getResource(CRDPATH));
      ResourceSpecifier rs = UIMAFramework.getXMLParser().parseCollectionReaderDescription(s);
      CollectionReader cr = UIMAFramework.produceCollectionReader(rs);
      CAS cas = CasCreationUtils.createCas(new ArrayList<>(0));
      assertTrue(cr.hasNext());
      cr.getNext(cas);
      assertNotNull(cas);
      AnnotationIndex<AnnotationFS> annotationIndex = cas.getAnnotationIndex();
      assertEquals(2, annotationIndex.size());
      assertTrue(cr.hasNext());
      cr.getNext(cas);
      assertFalse(cr.hasNext());
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getLocalizedMessage());
    }
  }

}
