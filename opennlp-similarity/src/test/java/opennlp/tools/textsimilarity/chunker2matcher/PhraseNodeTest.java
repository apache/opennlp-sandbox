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
package opennlp.tools.textsimilarity.chunker2matcher;

import java.util.List;

import junit.framework.TestCase;

public class PhraseNodeTest extends TestCase {
  ParserChunker2MatcherProcessor proc = ParserChunker2MatcherProcessor
      .getInstance();

  public void testPOSTagsExtraction() {

    SentenceNode node = proc.parseSentenceNode("How can I get there");

    try {
      List<String> pOSlist = node.getOrderedPOSList();
      assertEquals("[WRB, MD, PRP, VB, RB]", pOSlist.toString());

      node = proc.parseSentenceNode("where do I apply");
      pOSlist = node.getOrderedPOSList();
      assertEquals("[WRB, VBP, PRP, RB]", pOSlist.toString());

      // should NOT start with upper case! last tag is missing
      node = proc.parseSentenceNode("Where do I apply");
      pOSlist = node.getOrderedPOSList();
      assertEquals("[WRB, VBP, PRP]", pOSlist.toString());
    } catch (Exception e) { // for run without models, where init fails
      assertEquals(node, null);
    }
  }

}
