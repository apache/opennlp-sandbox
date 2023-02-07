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
import java.io.InputStream;

import org.apache.opennlp.corpus_server.impl.DerbyCorporaStore;
import org.apache.opennlp.corpus_server.store.CorporaStore;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CasConsumer;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@Disabled
class CSCasWriterTest extends AbstractCSTest {

  private static final String CCPATH = "/CSCasConsumerTestDescriptor.xml";

  private static final String BASE_PATH = CSCasWriterTest.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();

  @BeforeAll
  static void setUp() throws IOException {
    // kick out the old db instances that might be present already in this environment
    cleanTestDB();

    // init the new Derby instance with schema and demo data
    try (InputStream in = CSCasWriterTest.class.getResourceAsStream("/TypeSystem.xml");
         ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      TypeSystemDescription tsd = UimaUtil.createTypeSystemDescription(in);
      tsd.toXML(os);
      CorporaStore corporaStore = new DerbyCorporaStore();
      corporaStore.initialize(BASE_PATH.replace("file:", "").replace("/test-classes", ""));
      byte[] indexMapping = new byte[] {};
      corporaStore.createCorpus("wikinews", os.toByteArray(), indexMapping);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getLocalizedMessage());
    }
  }

  @Test
  @Disabled
    // TODO Investigate why this test fails with:
    //  A CasConsumer descriptor specified implementation class "org.apache.opennlp.corpus_server.connector.CSCasWriter",
    //  but this class does not implement the CasConsumer interface.
  void testCasWrite() {
    try (InputStream in = CSCasWriterTest.class.getResourceAsStream("/TypeSystem.xml")) {
      XMLInputSource s = new XMLInputSource(CSCasWriterTest.class.getResource(CCPATH));
      ResourceSpecifier rs = UIMAFramework.getXMLParser().parseCasConsumerDescription(s);
      CasConsumer casConsumer = UIMAFramework.produceCasConsumer(rs);
      TypeSystemDescription tsd = UimaUtil.createTypeSystemDescription(in);
      CAS cas = UimaUtil.createEmptyCAS(tsd);
      cas.setDocumentText("this cas needs to be stored");
      casConsumer.processCas(cas);
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getLocalizedMessage());
    }
  }
}
