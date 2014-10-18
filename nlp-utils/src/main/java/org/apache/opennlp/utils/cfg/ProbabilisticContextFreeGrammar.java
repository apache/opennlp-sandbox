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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * a probabilistic CFG
 */
public class ProbabilisticContextFreeGrammar {

  private final Collection<String> nonTerminalSymbols;
  private final Collection<String> terminalSymbols;
  private final Map<Rule, Double> rules;
  private final String startSymbol;
  private boolean randomExpansion;

  public ProbabilisticContextFreeGrammar(Collection<String> nonTerminalSymbols, Collection<String> terminalSymbols,
                                         Map<Rule, Double> rules, String startSymbol, boolean randomExpansion) {

    assert nonTerminalSymbols.contains(startSymbol) : "start symbol doesn't belong to non-terminal symbols set";

    this.nonTerminalSymbols = nonTerminalSymbols;
    this.terminalSymbols = terminalSymbols;
    this.rules = rules;
    this.startSymbol = startSymbol;
    this.randomExpansion = randomExpansion;
  }

  public ProbabilisticContextFreeGrammar(Collection<String> nonTerminalSymbols, Collection<String> terminalSymbols, Map<Rule, Double> rules, String startSymbol) {
    this(nonTerminalSymbols, terminalSymbols, rules, startSymbol, false);
  }

  public Collection<String> getNonTerminalSymbols() {
    return nonTerminalSymbols;
  }

  public Collection<String> getTerminalSymbols() {
    return terminalSymbols;
  }

  public Map<Rule, Double> getRules() {
    return rules;
  }

  public String getStartSymbol() {
    return startSymbol;
  }


  public String[] leftMostDerivation(String... words) {
    ArrayList<String> expansion = new ArrayList<String>(words.length);

    assert words.length > 0 && startSymbol.equals(words[0]);

    for (String word : words) {
      expansion.addAll(getTerminals(word));
    }
    return expansion.toArray(new String[expansion.size()]);

  }

  private Collection<String> getTerminals(String word) {
    if (terminalSymbols.contains(word)) {
      Collection<String> c = new LinkedList<String>();
      c.add(word);
      return c;
    } else {
      assert nonTerminalSymbols.contains(word) : "word " + word + " is not contained in non terminals";
      String[] expansions = getExpansionForSymbol(word);
      Collection<String> c = new LinkedList<String>();
      for (String e : expansions) {
        c.addAll(getTerminals(e));
      }
      return c;
    }
  }

  private String[] getExpansionForSymbol(String currentSymbol) {
    Rule r = getRuleForSymbol(currentSymbol);
    return r.getExpansion();
  }

  private Rule getRuleForSymbol(String word) {
    ArrayList<Rule> possibleRules = new ArrayList<Rule>();
    for (Rule r : rules.keySet()) {
      if (word.equals(r.getEntry())) {
        if (!randomExpansion) {
          return r;
        }
        possibleRules.add(r);
      }
    }
    if (possibleRules.size() > 0) {
      return possibleRules.get(new Random().nextInt(possibleRules.size()));
    } else {
      throw new RuntimeException("could not find a rule for expanding symbol " + word);
    }
  }

  public BackPointer pi(List<String> sentence, int i, int j, String x) {
    BackPointer backPointer = new BackPointer(0, 0, null);
    if (i == j) {
      Rule rule = new Rule(x, sentence.get(i));
      double q = q(rule);
      backPointer = new BackPointer(q, i, rule);
    } else {
      double max = 0;
      for (Rule rule : getNTRules()) {
        for (int s = i; s < j; s++) {
          double q = q(rule);
          BackPointer left = pi(sentence, i, s, rule.getExpansion()[0]);
          BackPointer right = pi(sentence, s + 1, j, rule.getExpansion()[1]);
          double cp = q * left.getProbability() * right.getProbability();
          if (cp > max) {
            max = cp;
            backPointer = new BackPointer(max, s, rule, left, right);
          }
        }
      }
    }
    return backPointer;
  }

  public BackPointer cky(List<String> sentence, ProbabilisticContextFreeGrammar pcfg) {
    BackPointer backPointer = null;

    int n = sentence.size();
    for (int l = 1; l < n; l++) {
      for (int i = 0; i < n - l; i++) {
        int j = i + l;
        double max = 0;
        for (String x : pcfg.getNonTerminalSymbols()) {
          for (Rule r : getRulesForNonTerminal(x)) {
            for (int s = i; s < j - 1; s++) {
              double q = q(r);
              BackPointer left = pi(sentence, i, s, r.getExpansion()[0]);
              BackPointer right = pi(sentence, s + 1, j, r.getExpansion()[1]);
              double cp = q * left.getProbability() * right.getProbability();
              if (cp > max) {
                max = cp;
                backPointer = new BackPointer(max, s, r, left, right);
              }
            }
          }
        }
      }
    }
    return backPointer;
  }

  private Collection<Rule> getRulesForNonTerminal(String x) {
    LinkedList<Rule> ntRules = new LinkedList<Rule>();
    for (Rule r : rules.keySet()) {
      if (x.equals(r.getEntry()) && nonTerminalSymbols.contains(r.getExpansion()[0]) && nonTerminalSymbols.contains(r.getExpansion()[1])) {
        ntRules.add(r);
      }
    }
    return ntRules;
  }

  private Collection<Rule> getNTRules() {
    Collection<Rule> ntRules = new LinkedList<Rule>();
    for (Rule r : rules.keySet()) {
      if (nonTerminalSymbols.contains(r.getExpansion()[0]) && nonTerminalSymbols.contains(r.getExpansion()[1])) {
        ntRules.add(r);
      }
    }
    return ntRules;
  }

  private double q(Rule rule) {
    return rules.keySet().contains(rule) ? rules.get(rule) : 0;
  }

  public class BackPointer {

    private final double probability;
    private final int splitPoint;
    private final Rule rule;
    private BackPointer leftTree;
    private BackPointer rightTree;

    private BackPointer(double probability, int splitPoint, Rule rule) {
      this.probability = probability;
      this.splitPoint = splitPoint;
      this.rule = rule;
    }

    public BackPointer(double probability, int splitPoint, Rule rule, BackPointer leftTree, BackPointer rightTree) {
      this.probability = probability;
      this.splitPoint = splitPoint;
      this.rule = rule;
      this.leftTree = leftTree;
      this.rightTree = rightTree;
    }

    public double getProbability() {
      return probability;
    }

    public int getSplitPoint() {
      return splitPoint;
    }

    public Rule getRule() {
      return rule;
    }

    public BackPointer getLeftTree() {
      return leftTree;
    }

    public BackPointer getRightTree() {
      return rightTree;
    }

    @Override
    public String toString() {
      return "BackPointer{" +
              "probability=" + probability +
              ", splitPoint=" + splitPoint +
              ", rule=" + rule +
              ", leftTree=" + leftTree +
              ", rightTree=" + rightTree +
              '}';
    }
  }

}
