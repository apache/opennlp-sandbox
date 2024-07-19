/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.summarization;

import opennlp.summarization.lexicalchaining.NounPOSTagger;
import opennlp.summarization.preprocess.DefaultDocProcessor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractSummarizerTest {

  private static final Logger log = LoggerFactory.getLogger(AbstractSummarizerTest.class);

  protected static DefaultDocProcessor docProcessor;
  protected static NounPOSTagger posTagger;

  @BeforeAll
  static void initEnv() throws IOException {
    docProcessor = new DefaultDocProcessor("en");
    posTagger = new NounPOSTagger("en");
  }

  /**
   * @return Obtains the {@link Summarizer} under test.
   */
  public abstract Summarizer getSummarizer();

  @ParameterizedTest(name = "news story {index}")
  @ValueSource(strings = {
          "/news/0a2035f3f73b06a5150a6f01cffdf45d027bbbed.story",
          "/news/0a2278bec4a80aec1bc3e9e7a9dac10ac1b6425b.story",
          "/news/0a3040b6c1bba95efca727158f128a19c44ec8ba.story",
          "/news/0a3479b53796863a664c32ca20d8672583335d2a.story",
          "/news/0a3639cb86487e72e2ba084211f99799918aedf8.story",
          "/news/0a4092bef1801863296777ebcfeceb1aec23c78f.story",
          "/news/0a5458d3427b290524a8df11d8503a5b57b32747.story",
          "/news/0a5691b8fe654b6b2cdace5ab87aff2ee4c23577.story",
          "/news/0a6790f886a42a76945d4a21ed27c4ebd9ca1025.story"
  })
  public void testSummarize(String filename) throws IOException {
    String article = docProcessor.docToString(filename);
    String summary = getSummarizer().summarize(article, 20);
    assertNotNull(summary);
    assertFalse(summary.isBlank());
    assertTrue(summary.length() > 20);
    if (log.isDebugEnabled()) {
      log.debug(summary);
    }
  }
}
