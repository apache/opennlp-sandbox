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

package opennlp.tools.textsimilarity;

import static junit.framework.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext-dedupe-test.xml" })
@ActiveProfiles("UnitTest")
public class LemmaFormManagerTest {

  @Autowired
  private LemmaFormManager lemmaFormManager;

  @Test
  public void notNull() {
    assertNotNull(lemmaFormManager);
  }

  @Test
  public void matches() {

    String res = lemmaFormManager.matchLemmas(null, "loud", "loudness", "NN");
    res = lemmaFormManager.matchLemmas(null, "24", "12", "CD");

    res = lemmaFormManager.matchLemmas(null, "loud", "loudly", "NN");
    res = lemmaFormManager.matchLemmas(null, "!upgrade", "upgrade", "NN");
    res = lemmaFormManager.matchLemmas(null, "!upgrade", "upgrades", "NN");
    res = lemmaFormManager.matchLemmas(null, "!upgrade", "get", "NN");

  }

}
