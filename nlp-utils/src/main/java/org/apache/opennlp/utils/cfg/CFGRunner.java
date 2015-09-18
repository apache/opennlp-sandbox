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
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Runner for {@link ContextFreeGrammar}s
 */
public class CFGRunner {

    public static void main(String[] args) throws Exception {
        CFGBuilder builder = new CFGBuilder();

        Arrays.sort(args);
        boolean useWn = Arrays.binarySearch(args, "-wn") >= 0;

        Collection<String> adverbsCollection;
        Collection<String> verbsCollection;
        Collection<String> adjectivesCollection;
        Collection<String> nounsCollection;
        if (useWn) {
            adverbsCollection = getTokens("/opennlp/cfg/wn/adv.txt");
            adjectivesCollection = getTokens("/opennlp/cfg/wn/adj.txt");
            nounsCollection = getTokens("/opennlp/cfg/wn/noun.txt");
            verbsCollection = getTokens("/opennlp/cfg/wn/verb.txt");
        } else {
            adverbsCollection = getTokens("/opennlp/cfg/an/adv.txt");
            adjectivesCollection = getTokens("/opennlp/cfg/an/adj.txt");
            nounsCollection = getTokens("/opennlp/cfg/an/noun.txt");
            verbsCollection = getTokens("/opennlp/cfg/an/verb.txt");
        }

        Collection<String> terminals = new LinkedList<>();
        terminals.addAll(adverbsCollection);
        terminals.addAll(verbsCollection);
        terminals.addAll(adjectivesCollection);
        terminals.addAll(nounsCollection);

        builder.withTerminals(terminals);

        Collection<String> nonTerminals = new LinkedList<String>();
        String startSymbol = "START_SYMBOL";
        nonTerminals.add(startSymbol);
        nonTerminals.add("NP");
        nonTerminals.add("NN");
        nonTerminals.add("Adv");
        nonTerminals.add("Adj");
        nonTerminals.add("VP");
        nonTerminals.add("Vb");
        builder.withNonTerminals(nonTerminals);

        builder.withStartSymbol(startSymbol);

        Collection<Rule> rules = new LinkedList<Rule>();
        rules.add(new Rule(startSymbol, "VP", "NP"));
        rules.add(new Rule("VP", "Adv", "Vb"));
        rules.add(new Rule("NP", "Adj", "NN"));

        for (String v : verbsCollection) {
            rules.add(new Rule("Vb", v));
        }
        for (String adj : adjectivesCollection) {
            rules.add(new Rule("Adj", adj));
        }
        for (String n : nounsCollection) {
            rules.add(new Rule("NN", n));
        }
        for (String adv : adverbsCollection) {
            rules.add(new Rule("Adv", adv));
        }
        builder.withRules(rules);
        ContextFreeGrammar cfg = builder.withRandomExpansion(true).build();
        String[] sentence = cfg.leftMostDerivation(startSymbol);
        String toString = Arrays.toString(sentence);

        if (toString.length() > 0) {
            System.out.println(toString.substring(1, toString.length() - 1).replaceAll(",", ""));
        }

        boolean pt = Arrays.binarySearch(args, "-pt") >= 0;

        if (pt) {
            Map<Rule, Double> rulesMap = new HashMap<>();
            rulesMap.put(new Rule(startSymbol, "VP", "NP"), 1d);
            rulesMap.put(new Rule("VP", "Adv", "Vb"), 1d);
            rulesMap.put(new Rule("NP", "Adj", "NN"), 1d);

            SecureRandom secureRandom = new SecureRandom();

            double remainingP = 1d;
            for (String v : verbsCollection) {
                double p = (double) secureRandom.nextInt(1000) / 1001d;
                if (rulesMap.size() == verbsCollection.size() - 1) {
                    p = remainingP;
                }
                if (remainingP - p <= 0) {
                    p /= 10;
                }
                rulesMap.put(new Rule("Vb", v), p);
                remainingP -= p;
            }
            for (String a : adjectivesCollection) {
                double p = (double) secureRandom.nextInt(1000) / 1001d;
                if (rulesMap.size() == adjectivesCollection.size() - 1) {
                    p = remainingP;
                }
                if (remainingP - p <= 0) {
                    p /= 10;
                }
                rulesMap.put(new Rule("Adj", a), p);
                remainingP -= p;
            }
            for (String n : nounsCollection) {
                double p = (double) secureRandom.nextInt(1000) / 1001d;
                if (rulesMap.size() == nounsCollection.size() - 1) {
                    p = remainingP;
                } else if (remainingP - p <= 0) {
                    p /= 10;
                }
                rulesMap.put(new Rule("NN", n), p);
                remainingP -= p;
            }
            for (String a : adverbsCollection) {
                double p = (double) secureRandom.nextInt(1000) / 1001d;
                if (rulesMap.size() == adverbsCollection.size() - 1) {
                    p = remainingP;
                }
                if (remainingP - p <= 0) {
                    p /= 10;
                }
                rulesMap.put(new Rule("Adv", a), p);
                remainingP -= p;
            }
            ProbabilisticContextFreeGrammar pcfg = new ProbabilisticContextFreeGrammar(cfg.getNonTerminalSymbols(), cfg.getTerminalSymbols(),
                    rulesMap, startSymbol, true);
            ProbabilisticContextFreeGrammar.ParseTree parseTree = pcfg.cky(Arrays.asList(sentence));
            System.out.println(parseTree);
        }
    }

    private static Collection<String> getTokens(String s) throws IOException {
        Collection<String> tokens = new LinkedList<>();
        InputStream resourceStream = CFGRunner.class.getResourceAsStream(s);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resourceStream));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            tokens.add(line);
        }
        bufferedReader.close();
        resourceStream.close();
        return tokens;
    }

}
