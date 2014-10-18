/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.utils.cfg;

import java.util.TreeSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Testcase for {@link Rule}
 */
public class RuleTest {

  @Test
  public void testEquals() throws Exception {
    Rule r1 = new Rule("NP", "NP", "PP");
    Rule r2 = new Rule("NP", "NP", "PP");
    assertEquals(r1, r2);
  }

  @Test
  public void testNotEquals() throws Exception {
    Rule r1 = new Rule("NP", "DT", "NN");
    Rule r2 = new Rule("NP", "NP", "PP");
    assertNotEquals(r1, r2);
  }

  @Test
  public void testHashcode() throws Exception {
    Rule r1 = new Rule("NP", "DT", "NN");
    Rule r2 = new Rule("NP", "NP", "PP");
    assertNotEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  public void testCompare() throws Exception {
    TreeSet<Rule> rules = new TreeSet<Rule>();
    Rule r1 = new Rule("NP", "DT", "NN");
    Rule r2 = new Rule("NP", "NP", "PP");
    rules.add(r1);
    rules.add(r2);
    assertEquals(2, rules.size());
  }
}
