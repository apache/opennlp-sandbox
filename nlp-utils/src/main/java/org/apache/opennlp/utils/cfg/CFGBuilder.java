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

import java.util.Collection;

/**
 * A builder for {@link ContextFreeGrammar}s
 */
public class CFGBuilder {

  private Collection<String> nonTerminalSymbols;
  private Collection<String> terminalSymbols;
  private Collection<Rule> rules;
  private String startSymbol;
  private boolean randomExpansion;

  public static CFGBuilder createCFG() {
    return new CFGBuilder();
  }

  public CFGBuilder withTerminals(Collection<String> terminalSymbols) {
    this.terminalSymbols = terminalSymbols;
    return this;
  }

  public CFGBuilder withNonTerminals(Collection<String> nonTerminalSymbols) {
    this.nonTerminalSymbols = nonTerminalSymbols;
    return this;
  }

  public CFGBuilder withRules(Collection<Rule> rules) {
    this.rules = rules;
    return this;
  }

  public CFGBuilder withStartSymbol(String startSymbol) {
    this.startSymbol = startSymbol;
    return this;
  }

  public CFGBuilder withRandomExpansion(boolean randomExpansion) {
    this.randomExpansion = randomExpansion;
    return this;
  }

  public ContextFreeGrammar build() {
    assert nonTerminalSymbols != null && terminalSymbols != null && rules != null && startSymbol != null :
            "missing definitions { V : " + nonTerminalSymbols + ", ∑ : " + terminalSymbols + ", R : " + rules + ", S : " + startSymbol + "}";
    return new ContextFreeGrammar(nonTerminalSymbols, terminalSymbols, rules, startSymbol, randomExpansion);
  }
}
