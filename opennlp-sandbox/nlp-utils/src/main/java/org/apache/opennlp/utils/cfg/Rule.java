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

import java.util.Arrays;

/**
 * A rule for context free grammars
 */
public class Rule implements Comparable<Rule> {
  private final String entry;
  private final String[] expansion;

  public Rule(String entry, String... expansion) {
    this.entry = entry;
    this.expansion = expansion;
  }

  public String getEntry() {
    return entry;
  }

  public String[] getExpansion() {
    return expansion;
  }

  @Override
  public int compareTo(Rule o) {
    int c = entry.compareTo(o.getEntry());
    return c != 0 ? c : Arrays.toString(expansion).compareTo(Arrays.toString(o.getExpansion()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Rule rule = (Rule) o;

    return !(entry != null ? !entry.equals(rule.entry) : rule.entry != null) && Arrays.equals(expansion, rule.expansion);

  }

  @Override
  public int hashCode() {
    int result = entry != null ? entry.hashCode() : 0;
    result = 31 * result + (expansion != null ? Arrays.hashCode(expansion) : 0);
    return result;
  }

  @Override
  public String toString() {
    return "{" +
            "'" + entry + '\'' +
            " -> " + Arrays.toString(expansion) +
            '}';
  }
}
