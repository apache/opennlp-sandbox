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

package opennlp.tools.coref.mention;

/**
 * Interface for finding head words in noun phrases and head noun-phrases in parses.
 *
 * @see Parse
 */
public interface HeadFinder {

  /** 
   * Returns the child parse which contains the lexical head of the specified {@link Parse}.
   * 
   * @param parse The {@link Parse} in which to find the head.
   * @return The parse containing the lexical head of the specified parse. If no head is
   * available or the constituent has no subcomponents that are eligible heads then null is returned.
   */
  Parse getHead(Parse parse);

  /** 
   * Returns which index the specified list of token is the head word.
   * 
   * @param parse The parse in which to find the head index.
   * @return The index of the head token.
   */
  int getHeadIndex(Parse parse);

  /** 
   * Returns the parse bottom-most head of a {@link Parse}. If no
   * head is available which is a child of <code>p</code> then <code>p</code> is returned.
   * 
   * @param p The {@link Parse} to find the head of.
   * @return bottom-most head of p.
   */
  Parse getLastHead(Parse p);

  /** 
   * Returns head token for the specified np {@link Parse}.
   * 
   * @param np The noun {@link Parse} to get head from.
   * @return head token parse.
   */
  Parse getHeadToken(Parse np);
}
