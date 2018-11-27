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
import java.util.Random;

/**
 * A context free grammar
 */
public class ContextFreeGrammar {

  private final Collection<String> nonTerminalSymbols;
  private final Collection<String> terminalSymbols;
  private final Collection<Rule> rules;
  private final String startSymbol;
  private final boolean randomExpansion;

  public Collection<String> getNonTerminalSymbols() {
    return nonTerminalSymbols;
  }

  public Collection<String> getTerminalSymbols() {
    return terminalSymbols;
  }

  public Collection<Rule> getRules() {
    return rules;
  }

  public String getStartSymbol() {
    return startSymbol;
  }

  public ContextFreeGrammar(Collection<String> nonTerminalSymbols, Collection<String> terminalSymbols, Collection<Rule> rules, String startSymbol, boolean randomExpansion) {
    assert nonTerminalSymbols.contains(startSymbol) : "start symbol doesn't belong to non-terminal symbols set";

    this.nonTerminalSymbols = nonTerminalSymbols;
    this.terminalSymbols = terminalSymbols;
    this.rules = rules;
    this.startSymbol = startSymbol;
    this.randomExpansion = randomExpansion;
  }

  public ContextFreeGrammar(Collection<String> nonTerminalSymbols, Collection<String> terminalSymbols, Collection<Rule> rules, String startSymbol) {
    this(nonTerminalSymbols, terminalSymbols, rules, startSymbol, false);
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
    for (Rule r : rules) {
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

}
