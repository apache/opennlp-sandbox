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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a probabilistic CFG
 */
public class ProbabilisticContextFreeGrammar {

  private final Collection<String> nonTerminalSymbols;
  private final Collection<String> terminalSymbols;
  private final Map<Rule, Double> rules;
  private final String startSymbol;
  private boolean randomExpansion;

  private static final Rule emptyRule = new Rule("EMPTY~", "");

  private static final String nonTerminalMatcher = "[\\w\\~\\*\\-\\.\\,\\'\\:\\_\\\"]";
  private static final String terminalMatcher = "[\\*òàùìèé\\|\\w\\'\\.\\,\\:\\_Ù\\?È\\%\\;À\\-\\\"]";

  private static final Pattern terminalPattern = Pattern.compile("\\(("+nonTerminalMatcher+"+)\\s("+terminalMatcher+"+)\\)");
  private static final Pattern nonTerminalPattern = Pattern.compile(
          "\\(("+nonTerminalMatcher+"+)" + // source NT
                  "\\s("+nonTerminalMatcher+"+)((\\s"+nonTerminalMatcher+"+)*)\\)" // expansion NTs
  );

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

  public ParseTree pi(List<String> sentence, int i, int j, String x) {
    ParseTree parseTree = new ParseTree(0, 0, null);
    if (i == j) {
      Rule rule = new Rule(x, sentence.get(i));
      double q = q(rule);
      parseTree = new ParseTree(q, i, rule);
    } else {
      double max = 0;
      for (Rule rule : getNTRules()) {
        for (int s = i; s < j; s++) {
          double q = q(rule);
          ParseTree left = pi(sentence, i, s, rule.getExpansion()[0]);
          ParseTree right = pi(sentence, s + 1, j, rule.getExpansion()[1]);
          double cp = q * left.getProbability() * right.getProbability();
          if (cp > max) {
            max = cp;
            parseTree = new ParseTree(max, s, rule, left, right);
          }
        }
      }
    }
    return parseTree;
  }

  public ParseTree cky(List<String> sentence) {
    ParseTree parseTree = null;

    int n = sentence.size();
    for (int l = 1; l < n; l++) {
      for (int i = 0; i < n - l; i++) {
        int j = i + l;
        double max = 0;
        for (String x : getNonTerminalSymbols()) {
          for (Rule r : getRulesForNonTerminal(x)) {
            for (int s = i; s < j - 1; s++) {
              double q = q(r);
              ParseTree left = pi(sentence, i, s, r.getExpansion()[0]);
              ParseTree right = pi(sentence, s + 1, j, r.getExpansion()[1]);
              double cp = q * left.getProbability() * right.getProbability();
              if (cp > max) {
                max = cp;
                parseTree = new ParseTree(max, s, r, left, right);
              }
            }
          }
        }
      }
    }
    return parseTree;
  }

  private Collection<Rule> getRulesForNonTerminal(String x) {
    LinkedList<Rule> ntRules = new LinkedList<Rule>();
    for (Rule r : rules.keySet()) {
      String[] expansion = r.getExpansion();
      if (expansion.length == 2 && x.equals(r.getEntry()) && nonTerminalSymbols.contains(expansion[0]) && nonTerminalSymbols.contains(expansion[1])) {
        ntRules.add(r);
      }
    }
    return ntRules;
  }

  private Collection<Rule> getNTRules() {
    Collection<Rule> ntRules = new LinkedList<Rule>();
    for (Rule r : rules.keySet()) {
      String[] expansion = r.getExpansion();
      if (expansion.length == 2 && nonTerminalSymbols.contains(expansion[0]) && nonTerminalSymbols.contains(expansion[1])) {
        ntRules.add(r);
      }
    }
    return ntRules;
  }

  private double q(Rule rule) {
    return rules.keySet().contains(rule) ? rules.get(rule) : 0;
  }

  public class ParseTree {

    private final double probability;
    private final int splitPoint;
    private final Rule rule;
    private ParseTree leftTree;
    private ParseTree rightTree;

    private ParseTree(double probability, int splitPoint, Rule rule) {
      this.probability = probability;
      this.splitPoint = splitPoint;
      this.rule = rule;
    }

    public ParseTree(double probability, int splitPoint, Rule rule, ParseTree leftTree, ParseTree rightTree) {
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

    public ParseTree getLeftTree() {
      return leftTree;
    }

    public ParseTree getRightTree() {
      return rightTree;
    }

    @Override
    public String toString() {
      if (getRule() != emptyRule) {
        return "(" +
                (rule != null ? rule.getEntry() : null) + " " +
                (leftTree != null && rightTree != null ?
                        leftTree.toString() + " " + rightTree.toString() :
                        (rule != null ? rule.getExpansion()[0] : null)
                ) +
                ')';
      } else {
        return "";
      }
    }

  }

  public static Map<Rule, Double> parseRules(String... parseTreeString) {
    Map<Rule, Double> rules = new HashMap<>();
    parseRules(rules, false, parseTreeString);
    return rules;
  }

  public static void parseRules(Map<Rule, Double> rules, boolean trim, String... parseStrings) {
    parseGrammar(rules, "S", trim, parseStrings);
  }

  public static ProbabilisticContextFreeGrammar parseGrammar(boolean trim, String... parseTreeStrings) {
    return parseGrammar(new HashMap<Rule, Double>(), "S", trim, parseTreeStrings);
  }

  public static ProbabilisticContextFreeGrammar parseGrammar(String... parseTreeStrings) {
    return parseGrammar(new HashMap<Rule, Double>(), "S", true, parseTreeStrings);
  }

  public static ProbabilisticContextFreeGrammar parseGrammar(Map<Rule, Double> rulesMap, String startSymbol, boolean trim, String... parseStrings) {

    Map<Rule, Double> rules = new HashMap<>();

    Collection<String> nonTerminals = new HashSet<>();
    Collection<String> terminals = new HashSet<>();

    rules.put(emptyRule, 1d);
    rulesMap.put(emptyRule, 1d);
    nonTerminals.add(emptyRule.getEntry());
    terminals.add(emptyRule.getExpansion()[0]);

    for (String parseTreeString : parseStrings) {

      if (trim) {
        parseTreeString = parseTreeString.replaceAll("\n", "").replaceAll("\t", "").replaceAll("\\s+", " ");
      }

      String toConsume = String.valueOf(parseTreeString);

      Matcher m = terminalPattern.matcher(parseTreeString);
      while (m.find()) {
        String nt = m.group(1);
        String t = m.group(2);
        Rule key = new Rule(nt, t);
        if (!rules.containsKey(key)) {
          rules.put(key, 1d);
          terminals.add(t);
        }
        toConsume = toConsume.replace(m.group(), nt);
      }

      while (toConsume.contains(" ") && !toConsume.trim().equals("( " + startSymbol + " )")) {
        Matcher m2 = nonTerminalPattern.matcher(toConsume);
        while (m2.find()) {
          String nt = m2.group(1);
          String t1 = m2.group(2);
          String t2 = m2.group(3);

          Rule key;
          if (t2 != null) {
            String[] t2s = t2.trim().split(" ");
            String[] nts = new String[t2s.length + 1];
            nts[0] = t1;
            System.arraycopy(t2s, 0, nts, 1, t2s.length);
            key = new Rule(nt, nts);
            nonTerminals.addAll(Arrays.asList(nts));
          } else {
            key = new Rule(nt, t1);
            nonTerminals.add(t1);
          }
          nonTerminals.add(key.getEntry());

          if (!rules.containsKey(key)) {
            rules.put(key, 1d);
          }
          toConsume = toConsume.replace(m2.group(), nt);
        }
      }
    }

    for (Map.Entry<Rule, Double> entry : rules.entrySet()) {
      normalize(entry.getKey(), nonTerminals, terminals, rulesMap);
    }

    return new ProbabilisticContextFreeGrammar(nonTerminals, terminals, rulesMap, startSymbol, true);
  }

  /**
   * Normalize (check and eventually adjust) rules to make them respect CNF
   * @param rule
   * @param nonTerminals
   * @param terminals
   * @param rulesMap
   */
  private static void normalize(Rule rule, Collection<String> nonTerminals, Collection<String> terminals, Map<Rule, Double> rulesMap) {
    String[] expansion = rule.getExpansion();
    String firstExpansion = expansion[0];
    if (expansion.length == 1) {
      if (!terminals.contains(firstExpansion)) {
        if (nonTerminals.contains(firstExpansion)) {
          // nt1 -> nt2 should be expanded in nt1 -> nt2,E
          Rule newRule = new Rule(rule.getEntry(), firstExpansion, emptyRule.getEntry());
          addRule(newRule, rulesMap);
        } else {
          throw new RuntimeException("rule "+rule+" expands to neither a terminal or non terminal");
        }
      } else {
        addRule(rule, rulesMap);
      }
    } else if (expansion.length > 2){
      // nt1 -> nt2,nt3,...,ntn should be collapsed to a hierarchy of ntX -> ntY,ntZ rules
      int seed = nonTerminals.size();
      String generatedNT = "GEN~" + seed;
      nonTerminals.add(generatedNT);
      Rule newRule = new Rule(rule.getEntry(), firstExpansion, generatedNT);
      rulesMap.put(newRule, 1d);
      Rule chainedRule = new Rule(generatedNT, Arrays.copyOfRange(expansion, 1, expansion.length));
      rulesMap.put(chainedRule, 1d);
      normalize(chainedRule, nonTerminals, terminals, rulesMap);
    } else {
      addRule(rule, rulesMap);
    }
  }

  private static void addRule(Rule rule, Map<Rule, Double> rulesMap) {
    Double prob = rulesMap.get(rule);
    if (prob != null && prob > 0d) {
      if (prob > 0.9d) {
        prob += 1d - prob - 0.01d;
      } else {
        prob += 0.01;
      }
    } else {
      prob = 0.3d;
    }

    rulesMap.put(rule, prob);
  }
}
