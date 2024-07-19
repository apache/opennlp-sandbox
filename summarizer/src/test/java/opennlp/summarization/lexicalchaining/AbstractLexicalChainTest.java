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
package opennlp.summarization.lexicalchaining;

import opennlp.summarization.preprocess.DefaultDocProcessor;
import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractLexicalChainTest {

  protected static final String ARTICLE =
          "US President Barack Obama has welcomed an agreement between the US and Russia under which Syria's chemical weapons must be destroyed or removed by mid-2014 as an \"important step\"."
                  + "But a White House statement cautioned that the US expected Syria to live up to its public commitments. "
                  + "The US-Russian framework document stipulates that Syria must provide details of its stockpile within a week. "
                  + "If Syria fails to comply, the deal could be enforced by a UN resolution. "
                  + "China, France, the UK, the UN and Nato have all expressed satisfaction at the agreement. "
                  + "In Beijing, Foreign Minister Wang Yi said on Sunday that China welcomes the general agreement between the US and Russia.";

  protected static DefaultDocProcessor dp;
  protected static LexicalChainingSummarizer lcs;

  @BeforeAll
  static void initEnv() throws Exception {
    dp = new DefaultDocProcessor("en");
    lcs = new LexicalChainingSummarizer(dp, "en");
  }
}
