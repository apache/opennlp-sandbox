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


public class CSCasConsumerTest {

//  private static final String CCPATH = "src/test/resources/org/apache/opennlp/corpus_server/CSCasConsumerTestDescriptor.xml";
//
//  @Before
//  public void setUp() throws Exception {
//    InputStream in = CorpusServer.class
//            .getResourceAsStream("/org/apache/opennlp/corpus_server/search/TypeSystem.xml");
//    TypeSystemDescription tsd = UimaUtil.createTypeSystemDescription(in);
//    ByteArrayOutputStream os = new ByteArrayOutputStream();
//    tsd.toXML(os);
//    CorporaStore corporaStore = new DerbyCorporaStore();
//    try {
//      corporaStore.initialize();
//      corporaStore.createCorpus("wikinews", os.toByteArray());
//    } catch (Exception e) {
//      // do nothing
//    }
//    new TestCorpusServer();
//  }
//
//  @Test
//  public void testCasWrite() {
//    try {
//      XMLInputSource s = new XMLInputSource(new File(CCPATH));
//      ResourceSpecifier rs = UIMAFramework.getXMLParser().parseCasConsumerDescription(s);
//      CasConsumer casConsumer = UIMAFramework.produceCasConsumer(rs);
//      InputStream in = CorpusServer.class
//              .getResourceAsStream("/org/apache/opennlp/corpus_server/WikinewsTypeSystem.xml");
//      TypeSystemDescription tsd = UimaUtil.createTypeSystemDescription(in);
//      CAS cas = UimaUtil.createEmptyCAS(tsd);
//      cas.setDocumentText("this cas needs to be stored");
//      casConsumer.processCas(cas);
//    } catch (Exception e) {
//      e.printStackTrace();
//      fail(e.getLocalizedMessage());
//    }
//  }
}
