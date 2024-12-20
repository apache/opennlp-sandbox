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

package opennlp.tools.formats.muc;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.coref.AbstractCorefTest;
import opennlp.tools.coref.CorefSample;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Muc6FullParseCorefSampleStreamFactoryTest extends AbstractCorefTest {

  private static final String MODEL_TOKENS = "opennlp-en-ud-ewt-tokens-1.2-2.5.0.bin";
  private static final String MODEL_PARSER = "en-parser-chunking.bin";
  private static final String MODEL_NER_PER = "en-ner-person.bin";
  private static final String MODEL_NER_ORG = "en-ner-organization.bin";

  private String[] args;

  // SUT
  private Muc6FullParseCorefSampleStreamFactory streamFactory;

  @BeforeAll
  public static void initEnv() throws IOException {
    Muc6FullParseCorefSampleStreamFactory.registerFactory();
    DownloadUtil.downloadModel("en", DownloadUtil.ModelType.TOKENIZER, TokenizerModel.class);
    downloadVersion15Model(MODEL_PARSER);
    downloadVersion15Model(MODEL_NER_PER);
    downloadVersion15Model(MODEL_NER_ORG);
  }

  @BeforeEach
  public void setUp() {
    streamFactory= new Muc6FullParseCorefSampleStreamFactory();
    args = new String[]{"-data", Muc6FullParseCorefSampleStreamFactoryTest.class.
            getResource("/models/training/coref/muc").getPath(),
            "-tokenizerModel", OPENNLP_DIR.resolve(MODEL_TOKENS).toString(),
            "-parserModel", OPENNLP_DIR.resolve(MODEL_PARSER).toString(),
            "-personModel", OPENNLP_DIR.resolve(MODEL_NER_PER).toString(),
            "-organizationModel", OPENNLP_DIR.resolve(MODEL_NER_ORG).toString()
    };
  }

  @Test
  void testCreate() throws IOException {
    try (ObjectStream<CorefSample> samples = streamFactory.create(args)) {
      assertNotNull(samples);
      CorefSample cs = samples.read();
      assertNotNull(cs);
      List<Parse> parses = cs.getParses();
      assertNotNull(parses);
      assertEquals(17, parses.size());
    }
  }

}
