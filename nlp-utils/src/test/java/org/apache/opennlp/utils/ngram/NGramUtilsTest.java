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
package org.apache.opennlp.utils.ngram;

import java.util.Collection;
import java.util.LinkedList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.ngram.NGramUtils}
 */
public class NGramUtilsTest {
  @Test
  public void testBigram() {
    Collection<String[]> set = new LinkedList<String[]>();
    set.add(new String[]{"<s>", "I", "am", "Sam", "</s>"});
    set.add(new String[]{"<s>", "Sam", "I", "am", "</s>"});
    set.add(new String[]{"<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"});
    set.add(new String[]{});
    Double d = NGramUtils.calculateBigramMLProbability("I", "<s>", set);
    assertTrue(d > 0);
    assertEquals(Double.valueOf(0.6666666666666666d), d);
    d = NGramUtils.calculateBigramMLProbability("</s>", "Sam", set);
    assertEquals(Double.valueOf(0.5d), d);
    d = NGramUtils.calculateBigramMLProbability("Sam", "<s>", set);
    assertEquals(Double.valueOf(0.3333333333333333d), d);
  }

  @Test
  public void testTrigram() {
    Collection<String[]> set = new LinkedList<String[]>();
    set.add(new String[]{"<s>", "I", "am", "Sam", "</s>"});
    set.add(new String[]{"<s>", "Sam", "I", "am", "</s>"});
    set.add(new String[]{"<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"});
    set.add(new String[]{});
    Double d = NGramUtils.calculateTrigramMLProbability("I", "am", "Sam", set);
    assertEquals(Double.valueOf(0.5), d);
    d = NGramUtils.calculateTrigramMLProbability("Sam", "I", "am", set);
    assertEquals(Double.valueOf(1d), d);
  }

  @Test
  public void testLinearInterpolation() throws Exception {
    Collection<String[]> set = new LinkedList<String[]>();
    set.add(new String[]{"the", "green", "book", "STOP"});
    set.add(new String[]{"my", "blue", "book", "STOP"});
    set.add(new String[]{"his", "green", "house", "STOP"});
    set.add(new String[]{"book", "STOP"});
    Double lambda = 1d / 3d;
    Double d = NGramUtils.calculateLinearInterpolationProbability("the", "green", "book", set, lambda, lambda, lambda);
    assertNotNull(d);
    assertTrue(d > 0);
    assertEquals("wrong result", Double.valueOf(0.5714285714285714d), d);
  }

  @Test
  public void testLinearInterpolation2() throws Exception {
    Collection<String[]> set = new LinkedList<String[]>();
    set.add(new String[]{"D", "N", "V", "STOP"});
    set.add(new String[]{"D", "N", "V", "STOP"});
    Double lambda = 1d / 3d;
    Double d = NGramUtils.calculateLinearInterpolationProbability("N", "V", "STOP", set, lambda, lambda, lambda);
    assertNotNull(d);
    assertTrue(d > 0);
    assertEquals("wrong result", Double.valueOf(0.75d), d);
  }

}
