/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.coref.sim;

public class SemanticEnum {

  private final String compatibility;

  /** Semantically compatible. */
  public static final SemanticEnum COMPATIBLE = new SemanticEnum("compatible");
  /** Semantically incompatible. */
  public static final SemanticEnum INCOMPATIBLE = new SemanticEnum("incompatible");
  /** Semantic compatibility Unknown. */
  public static final SemanticEnum UNKNOWN = new SemanticEnum("unknown");

  private SemanticEnum(String g) {
    compatibility = g;
  }

  @Override
  public String toString() {
    return compatibility;
  }
}
