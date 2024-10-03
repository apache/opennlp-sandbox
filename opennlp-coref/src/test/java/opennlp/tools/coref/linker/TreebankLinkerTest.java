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

import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.coref.AbstractCorefTest;
import opennlp.tools.coref.CorefParse;
import opennlp.tools.coref.DefaultParse;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.parser.Parse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TreebankLinkerTest extends AbstractLinkerTest {

  // SUT - TreebankLinker
  private Linker corefLinker;

  /**
   * @throws IOException when the model directory can not be read.
   */
  @BeforeEach
  public void setUp() throws IOException {
    final URL modelDirectory = getClass().getResource(AbstractCorefTest.MODEL_DIR);
    assertNotNull(modelDirectory);
    corefLinker = new TreebankLinker(modelDirectory.getPath(), LinkerMode.TEST);
    assertNotNull(corefLinker);
  }

  /**
   * Identifies co-reference relationships for parsed input.
   */
  @Test
  public void testLinker() {
    int sentenceNumber = 0;
    List<Mention> document = new ArrayList<>();
    List<Parse> parses = new ArrayList<>();

    for (String sentence : sentences) {
      Parse[] topParses = ParserTool.parseLine(sentence, parserEN, 1);
      Parse p = topParses[0];

      parses.add(p);
      Mention[] extents = corefLinker.getMentionFinder().getMentions(new DefaultParse(p, sentenceNumber));
      //construct new parses for mentions which don't have constituents.
      for (Mention extent : extents) {
        if (extent.getParse() == null) {
          //not sure how to get head index, but it's not used at this point.
          Parse snp = new Parse(p.getText(), extent.getSpan(), "NML", 1.0, 0);
          p.insert(snp);
          extent.setParse(new DefaultParse(snp, sentenceNumber));
        }
      }
      document.addAll(Arrays.asList(extents));
      sentenceNumber++;
    }
    assertFalse(document.isEmpty());

    DiscourseEntity[] entities = corefLinker.getEntities(document.toArray(new Mention[0]));
    assertNotNull(entities);
    assertEquals(13, entities.length);
    showEntities(entities);

    CorefParse crParse = new CorefParse(parses, entities);
    assertNotNull(crParse);
    assertNotNull(crParse.getParseMap());
    assertFalse(crParse.getParseMap().isEmpty());
    // crParse.show();
  }

}


