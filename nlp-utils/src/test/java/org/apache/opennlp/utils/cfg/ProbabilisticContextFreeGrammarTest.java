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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

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
    assertEquals(0.3d, pi, 0d);

    pi = pcfg.pi(sentence, 2, 4, "VP").getProbability();
    assertEquals(0.35d, pi, 0d);
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

    ProbabilisticContextFreeGrammar.ParseTree parseTree = pcfg.cky(sentence);
    check(pcfg, parseTree, sentence);

    // fixed sentence two
    sentence = new ArrayList<String>();
    sentence.add("the");
    sentence.add("man");
    sentence.add("works");
    sentence.add("nicely");

    parseTree = pcfg.cky(sentence);
    check(pcfg, parseTree, sentence);

    // random sentence generated by the grammar
    String[] expansion = pcfg.leftMostDerivation("S");
    sentence = Arrays.asList(expansion);

    parseTree = pcfg.cky(sentence);
    check(pcfg, parseTree, sentence);
  }

  private void check(ProbabilisticContextFreeGrammar pcfg, ProbabilisticContextFreeGrammar.ParseTree parseTree, List<String> sentence) {
    Rule rule = parseTree.getRule();
    assertNotNull(rule);
    assertEquals(pcfg.getStartSymbol(), rule.getEntry());
    int s = parseTree.getSplitPoint();
    assertTrue(s >= 0);
    double pi = parseTree.getProbability();
    assertTrue(pi <= 1 && pi >= 0);
    List<String> expandedTerminals = getTerminals(parseTree);
    for (int i = 0; i < sentence.size(); i++) {
      assertEquals(sentence.get(i), expandedTerminals.get(i));
    }

  }

  private List<String> getTerminals(ProbabilisticContextFreeGrammar.ParseTree parseTree) {
    if (parseTree.getLeftTree() == null && parseTree.getRightTree() == null) {
      return Arrays.asList(parseTree.getRule().getExpansion());
    }

    ArrayList<String> list = new ArrayList<String>();
    list.addAll(getTerminals(parseTree.getLeftTree()));
    list.addAll(getTerminals(parseTree.getRightTree()));
    return list;
  }

  @Test
  public void testParseString() throws Exception {
    String string = "(S (VP (Adv last) (Vb tidy)) (NP (Adj biogenic) (NN Gainesville)))";
    Map<Rule, Double> rules = ProbabilisticContextFreeGrammar.parseRules(string);
    assertNotNull(rules);
    assertEquals(8, rules.size());
  }

  @Test
  public void testReadingItalianPennTreebankParseTreeSamples() throws Exception {
    String newsSample = "( (S \n" +
            "    (VP (VMA~RE Slitta) \n" +
            "        (PP-LOC (PREP a) \n" +
            "            (NP (NOU~PR Tirana))) \n" +
            "         (NP-EXTPSBJ-433 \n" +
            "             (NP (ART~DE la) (NOU~CS decisione)) \n" +
            "             (PP (PREP sullo) \n" +
            "                 (NP \n" +
            "                     (NP (ART~DE sullo) (NOU~CS stato)) \n" +
            "                     (PP (PREP di) \n" +
            "                         (NP (NOU~CS emergenza))))))) \n" +
            "          (NP-SBJ (-NONE- *-433)) \n" +
            "          (. .)) ) ";
    Map<Rule, Double> rules = new HashMap<>();
    ProbabilisticContextFreeGrammar.parseRules(rules, true, newsSample);
    assertNotNull(rules);

    String newsSample2 = "( (S \n" +
            "    (NP-SBJ (ART~DE La) (NOU~CS mafia) (ADJ~QU italiana)) \n" +
            "    (VP (VMA~RE opera) \n" +
            "        (PP-LOC (PREP in) \n" +
            "            (NP (NOU~PR Albania)))) \n" +
            "      (. .)) ) ";
    Map<Rule, Double> rules2 = new HashMap<>();
    ProbabilisticContextFreeGrammar.parseRules(rules2, true, newsSample2);
    assertNotNull(rules2);

    // aggregated
    Map<Rule, Double> rules3 = new HashMap<>();
    ProbabilisticContextFreeGrammar.parseRules(rules3, true, newsSample, newsSample2);
    assertNotNull(rules3);

    ProbabilisticContextFreeGrammar contextFreeGrammar = ProbabilisticContextFreeGrammar.parseGrammar(newsSample, newsSample2);
    assertNotNull(contextFreeGrammar);
    String[] derivation = contextFreeGrammar.leftMostDerivation("S");
    assertNotNull(derivation);
    assertTrue(derivation.length > 1);
  }

  @Ignore
  @Test
  public void testReadingItalianPennTreebankParseTree() throws Exception {
    InputStream resourceAsStream = getClass().getResourceAsStream("/it-tb-news.txt");
    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resourceAsStream));
    Collection<String> sentences = parseSentences(bufferedReader);
    ProbabilisticContextFreeGrammar cfg = ProbabilisticContextFreeGrammar.parseGrammar(sentences.toArray(new String[sentences.size()]));
    assertNotNull(cfg);
    String[] derivation = cfg.leftMostDerivation("S");
    assertNotNull(derivation);
    System.err.println(Arrays.toString(derivation));
    ProbabilisticContextFreeGrammar.ParseTree parseTree1 = cfg.cky(Arrays.asList(derivation));
    assertNotNull(parseTree1);
    System.err.println(parseTree1);

    String sentence = "Il Governo di Berisha appare in difficolta'";
    List<String> fixedSentence = Arrays.asList(sentence.split(" "));
    ProbabilisticContextFreeGrammar.ParseTree parseTree2 = cfg.cky(fixedSentence);
    assertNotNull(parseTree2);
  }

  private Collection<String> parseSentences(BufferedReader bufferedReader) throws IOException {

    Collection<String> sentences = new LinkedList<>();
    String line;
    StringBuilder sentence = new StringBuilder();
    while ((line = bufferedReader.readLine()) != null) {
      if (line.contains("(") || line.contains(")")) {
        sentence.append(line);
      } else if (line.contains("*****")){
        // only use single sentences
        String s = sentence.toString();
        if (s.trim().split("\\(S ").length == 2 && s.trim().startsWith("( (S")) {
          sentences.add(s);
        }
        sentence = new StringBuilder();
      }
    }
    if (sentence.length() > 0) {
      sentences.add(sentence.toString());
    }

    return sentences;
  }
}
