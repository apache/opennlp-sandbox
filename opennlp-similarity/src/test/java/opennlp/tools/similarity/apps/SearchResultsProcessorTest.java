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
package opennlp.tools.similarity.apps;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SearchResultsProcessorTest {
  private final SearchResultsProcessor proc = new SearchResultsProcessor();

  @Test
  public void testSearchOrder() {
    List<HitBase> res = proc.runSearch("How can I pay tax on my income abroad");

    // we verify that top answers have high similarity score
    HitBase first = res.get(0);
    assertTrue(first.getGenerWithQueryScore() > 2.79);
    // assertTrue(first.getTitle().indexOf("Foreign")>-1 &&
    // first.getTitle().indexOf("earned")>-1);

    HitBase second = res.get(1);
    assertTrue(second.getGenerWithQueryScore() > 1.69);
    // assertTrue(second.getTitle().indexOf("living abroad")>-1);
    proc.close();

  }

  @Test
  public void testSearchOrder2() {
    List<HitBase> res = proc.runSearch("Can I estimate what my income tax would be by using my last pay");

    HitBase first = res.get(0);
    assertTrue(first.getGenerWithQueryScore() > 1.9);

    HitBase second = res.get(1);
    assertTrue(second.getGenerWithQueryScore() > 1.9);
    proc.close();
  }
}
