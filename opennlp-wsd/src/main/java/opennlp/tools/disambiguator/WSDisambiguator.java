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

package opennlp.tools.disambiguator;

import java.security.InvalidParameterException;
import opennlp.tools.util.Span;

/**
 * A word sense disambiguator that determines which sense of a word is meant in
 * a particular context. It is a classification task, where the classes are the
 * different senses of the ambiguous word. Disambiguation can be achieved in
 * either supervised or un-supervised approaches. For the moment this component
 * relies on WordNet to retrieve sense definitions. It returns an array of
 * WordNet sense IDs ordered by their disambiguation score. The sense with
 * highest score is the most likely sense of the word.
 * 
 * Please see {@link Lesk} for an un-supervised approach. Please see {@link IMS}
 * for a supervised approach.
 * 
 * @see Lesk
 * @see IMS
 */
public interface WSDisambiguator {

  /**
   * @return the parameters of the disambiguation algorithm
   */
  public WSDParameters getParams();

  /**
   * @param the
   *          disambiguation implementation specific parameters.
   * @throws InvalidParameterException
   */
  public void setParams(WSDParameters params) throws InvalidParameterException;

  /**
   * @param tokenizedContext
   * @param ambiguousTokenIndex
   * @return result as an array of WordNet IDs
   */
  public String[] disambiguate(String[] tokenizedContext,
      int ambiguousTokenIndex);

  /**
   * @param tokenizedContext
   * @param ambiguousTokenIndexSpans
   * @return result as an array of WordNet IDs
   */
  public String[][] disambiguate(String[] tokenizedContext,
      Span[] ambiguousTokenIndexSpans);
}