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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testcase for {@link org.apache.opennlp.utils.cfg.ProbabilisticContextFreeGrammar}
 */
public class ProbabilisticContextFreeGrammarTest {

  private static LinkedList<String> nonTerminals;
  private static String startSymbol;
  private static LinkedList<String> terminals;
  private static Map<Rule, Double> rules;

  @BeforeClass
  public static void setUp() throws Exception {
    nonTerminals = new LinkedList<String>();
    nonTerminals.add("S");
    nonTerminals.add("NP");
    nonTerminals.add("VP");
    nonTerminals.add("DT");
    nonTerminals.add("Vi");
    nonTerminals.add("Vt");
    nonTerminals.add("NN");
    nonTerminals.add("IN");
    nonTerminals.add("NNP");
    nonTerminals.add("Adv");

    startSymbol = "S";

    terminals = new LinkedList<String>();
    terminals.add("works");
    terminals.add("saw");
    terminals.add("man");
    terminals.add("woman");
    terminals.add("dog");
    terminals.add("the");
    terminals.add("with");
    terminals.add("in");
    terminals.add("joe");
    terminals.add("john");
    terminals.add("sam");
    terminals.add("michael");
    terminals.add("michelle");
    terminals.add("scarlett");
    terminals.add("and");
    terminals.add("but");
    terminals.add("while");
    terminals.add("of");
    terminals.add("for");
    terminals.add("badly");
    terminals.add("nicely");

    rules = new HashMap<Rule, Double>();
    rules.put(new Rule("S", "NP", "VP"), 1d);
    rules.put(new Rule("VP", "Vi", "Adv"), 0.3);
    rules.put(new Rule("VP", "Vt", "NP"), 0.7);
    rules.put(new Rule("NP", "DT", "NN"), 1d);
    rules.put(new Rule("Vi", "works"), 1d);
    rules.put(new Rule("Vt", "saw"), 1d);
    rules.put(new Rule("NN", "man"), 0.5);
    rules.put(new Rule("NN", "woman"), 0.2);
    rules.put(new Rule("NN", "dog"), 0.3);
    rules.put(new Rule("DT", "the"), 1d);
    rules.put(new Rule("IN", "with"), 0.2);
    rules.put(new Rule("IN", "in"), 0.1);
    rules.put(new Rule("IN", "for"), 0.4);
    rules.put(new Rule("IN", "of"), 0.4);
    rules.put(new Rule("NNP", "joe"), 0.1);
    rules.put(new Rule("NNP", "john"), 0.1);
    rules.put(new Rule("NNP", "sam"), 0.1);
    rules.put(new Rule("NNP", "michael"), 0.1);
    rules.put(new Rule("NNP", "michelle"), 0.1);
    rules.put(new Rule("NNP", "scarlett"), 0.5);
    rules.put(new Rule("Adv", "badly"), 0.3);
    rules.put(new Rule("Adv", "nicely"), 0.7);
  }

  @Test
  public void testIntermediateProbability() throws Exception {
    ArrayList<String> sentence = new ArrayList<String>();
    sentence.add("the");
    sentence.add("dog");
    sentence.add("saw");
    sentence.add("the");
    sentence.add("man");
    sentence.add("with");
    sentence.add("the");
    sentence.add("woman");

    ProbabilisticContextFreeGrammar pcfg = new ProbabilisticContextFreeGrammar(nonTerminals, terminals, rules, startSymbol);

    double pi = pcfg.pi(sentence, 0, 1, pcfg.getStartSymbol()).getProbability();
    assertTrue(pi <= 1 && pi >= 0);

    pi = pcfg.pi(sentence, 2, 7, "VP").getProbability();
    assertTrue(pi <= 1 && pi >= 0);
  }

  @Test
  public void testFullSentenceCKY() throws Exception {
    ProbabilisticContextFreeGrammar pcfg = new ProbabilisticContextFreeGrammar(nonTerminals, terminals, rules, startSymbol, true);

    // fixed sentence one
    List<String> sentence = new ArrayList<String>();
    sentence.add("the");
    sentence.add("dog");
    sentence.add("saw");
    sentence.add("the");
    sentence.add("man");

    ProbabilisticContextFreeGrammar.BackPointer backPointer = pcfg.cky(sentence, pcfg);
    check(pcfg, backPointer, sentence);

    // fixed sentence two
    sentence = new ArrayList<String>();
    sentence.add("the");
    sentence.add("man");
    sentence.add("works");
    sentence.add("nicely");

    backPointer = pcfg.cky(sentence, pcfg);
    check(pcfg, backPointer, sentence);

    // random sentence generated by the grammar
    String[] expansion = pcfg.leftMostDerivation("S");
    sentence = Arrays.asList(expansion);

    backPointer = pcfg.cky(sentence, pcfg);
    check(pcfg, backPointer, sentence);
  }

  private void check(ProbabilisticContextFreeGrammar pcfg, ProbabilisticContextFreeGrammar.BackPointer backPointer, List<String> sentence) {
    Rule rule = backPointer.getRule();
    assertNotNull(rule);
    assertEquals(pcfg.getStartSymbol(), rule.getEntry());
    int s = backPointer.getSplitPoint();
    assertTrue(s >= 0);
    double pi = backPointer.getProbability();
    assertTrue(pi <= 1 && pi >= 0);
    List<String> expandedTerminals = getTerminals(backPointer);
    for (int i = 0; i < sentence.size(); i++) {
      assertEquals(sentence.get(i), expandedTerminals.get(i));
    }

  }

  private List<String> getTerminals(ProbabilisticContextFreeGrammar.BackPointer backPointer) {
    if (backPointer.getLeftTree() == null && backPointer.getRightTree() == null) {
      return Arrays.asList(backPointer.getRule().getExpansion());
    }

    ArrayList<String> list = new ArrayList<String>();
    list.addAll(getTerminals(backPointer.getLeftTree()));
    list.addAll(getTerminals(backPointer.getRightTree()));
    return list;
  }

}
