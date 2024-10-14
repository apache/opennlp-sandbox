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

package opennlp.tools.coref;

import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.coref.linker.AbstractLinkerTest;
import opennlp.tools.parser.Parse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class CorefParseTest extends AbstractLinkerTest {

  private static final String example = "The test may come today . ";
  //        "(TOP (S (NP (DT The) (NN test)) (VP (MD may) (VP (VB come) (NP (NN today)))) (. .)))";


  private List<Parse> parses;

  @BeforeEach
  public void setUp() {
    parses = Arrays.stream(ParserTool.parseLine(example, parserEN, 1)).toList();
  }

  @Test
  // TODO make this a solid test -> DiscourseEntity
  void testConstruct() {
    CorefParse cp = new CorefParse(parses, new DiscourseEntity[0]);
    cp.show();
  }

}
