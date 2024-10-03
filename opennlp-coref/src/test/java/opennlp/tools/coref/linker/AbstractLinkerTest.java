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

package opennlp.tools.coref.linker;

import opennlp.tools.coref.AbstractCorefTest;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public abstract class AbstractLinkerTest extends AbstractCorefTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractLinkerTest.class);

  protected static final String PARSER_MODEL_NAME = "en-parser-chunking.bin";

  protected static Parser parserEN;

  static final String[] sentences = {
          "Pierre Vinken, 61 years old, will join the board as a nonexecutive director Nov. 29 .",
          "Mr. Vinken is chairman of Elsevier N.V. , the Dutch publishing group .",
          "Rudolph Agnew, 55 years old and former chairman of Consolidated Gold Fields PLC , " +
                  "was named a director of this British industrial conglomerate ."
  };

  @BeforeAll
  static void initEnv() throws IOException {
    downloadVersion15Model(PARSER_MODEL_NAME);
    final Path modelPath = OPENNLP_DIR.resolve(PARSER_MODEL_NAME);
    ParserModel model = new ParserModel(modelPath);
    parserEN = ParserFactory.create(model);
  }

  static void showEntities(DiscourseEntity[] entities) {
    for (int ei = 0, en = entities.length; ei < en; ei++) {
      logger.debug(ei + " " + entities[ei]);
    }
  }
}
