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
 * either supervised or un-supervised approaches. A disambiguator returns an
 * array of sense IDs ordered by their disambiguation score as well their
 * source. The first sense ID is the most probable sense in the set context. The
 * context is a sentence or a chunk of text where the target word exists.
 * 
 * <b>How it works :<b> Just supply the context as an array of tokens and the
 * index of the target word to the disambiguate method.
 * 
 * Otherwise for multiple words, you can set a word span instead of simply one
 * index. For the moment the source of sense definitions is from WordNet. *
 * Please see {@link Lesk} for an un-supervised approach. Please see {@link IMS}
 * for a supervised approach.
 * 
 * Examples on how to use each approach are provided in the test section.
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
   * @param tokenTags 
   * @param ambiguousTokenIndex
   * @param ambiguousTokenLemma
   * @return result as an array of WordNet IDs
   */
  public String[] disambiguate(String[] tokenizedContext, String[] tokenTags,
      int ambiguousTokenIndex, String ambiguousTokenLemma);

  /**
   * @param tokenizedContext
   * @param tokenTags
   * @param ambiguousTokenIndexSpan
   * @param ambiguousTokenLemma
   * @return result as an array of WordNet IDs
   */
  public String[][] disambiguate(String[] tokenizedContext, String[] tokenTags,
      Span ambiguousTokenIndexSpan, String ambiguousTokenLemma);
  
  /**
   * @param WSDSample
   * @return result as an array of WordNet IDs
   */
  public String[] disambiguate(WSDSample sample);
  
  @Deprecated
  public String[] disambiguate(String[] inputText, int inputWordIndex);
}