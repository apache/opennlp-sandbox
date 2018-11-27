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
import java.util.ArrayList;
import java.util.List;
import opennlp.tools.util.Span;

/**
 * A word sense disambiguator that determines which sense of a word is meant in
 * a particular context. It is a classification task, where the classes are the
 * different senses of the ambiguous word. Disambiguation can be achieved in
 * either supervised or un-supervised approaches. A disambiguator returns a
 * sense ID.
 * 
 * <b>How it works :<b> Just supply the context as an array of tokens and the
 * index of the target word to the disambiguate method.
 * 
 * Otherwise for multiple words, you can set a word span instead of simply one
 * index. For the moment the source of sense definitions is from WordNet. *
 * 
 * Examples on how to use each approach are provided in the test section.
 *
 */
public abstract class WSDisambiguator {

  protected WSDParameters params;

  /**
   * @return the parameters of the disambiguation algorithm
   */
  public WSDParameters getParams() {
    return params;
  }

  /**
   * @param params disambiguation implementation specific parameters.
   * @throws InvalidParameterException
   */
  public void setParams(WSDParameters params) throws InvalidParameterException {
    this.params = params;
  }

  /**
   * @param tokenizedContext
   * @param tokenTags
   * @param lemmas
   * @param ambiguousTokenIndex
   * @return result as an array of WordNet IDs
   */
  public String disambiguate(String[] tokenizedContext, String[] tokenTags,
      String[] lemmas, int ambiguousTokenIndex) {
    return disambiguate(new WSDSample(tokenizedContext, tokenTags, lemmas,
        ambiguousTokenIndex));
  }

  /**
   * The disambiguation method for all the words in a Span
   * 
   * @param tokenizedContext
   * @param tokenTags
   * @param lemmas
   * @param ambiguousTokenIndexSpan
   * @return result as an array of WordNet IDs
   */
  public List<String> disambiguate(String[] tokenizedContext,
      String[] tokenTags, String[] lemmas, Span ambiguousTokenIndexSpan) {
    List<String> senses = new ArrayList<String>();

    int start = Math.max(0, ambiguousTokenIndexSpan.getStart());

    int end = Math.max(start,
        Math.min(tokenizedContext.length, ambiguousTokenIndexSpan.getEnd()));

    for (int i = start; i < end + 1; i++) {

      if (WSDHelper.isRelevantPOSTag(tokenTags[i])) {
        WSDSample sample = new WSDSample(tokenizedContext, tokenTags, lemmas,
            i);
        String sense = disambiguate(sample);
        senses.add(sense);
      } else {

        if (WSDHelper.getNonRelevWordsDef(tokenTags[i]) != null) {
          String sense = WSDParameters.SenseSource.WSDHELPER.name() + " "
              + WSDHelper.getNonRelevWordsDef(tokenTags[i]);
          senses.add(sense);
        } else {
          senses.add(null);
        }
      }

    }

    return senses;
  }

  /**
   * The disambiguation method for all the words of the context
   * 
   * @param tokenizedContext
   *          : the text containing the word to disambiguate
   * @param tokenTags
   *          : the tags corresponding to the context
   * @param lemmas
   *          : the lemmas of ALL the words in the context
   * @return a List of arrays, each corresponding to the senses of each word of
   *         the context which are to be disambiguated
   */
  public List<String> disambiguate(String[] tokenizedContext,
      String[] tokenTags, String[] lemmas) {

    List<String> senses = new ArrayList<String>();

    for (int i = 0; i < tokenizedContext.length; i++) {

      if (WSDHelper.isRelevantPOSTag(tokenTags[i])) {
        WSDSample sample = new WSDSample(tokenizedContext, tokenTags, lemmas,
            i);
        senses.add(disambiguate(sample));
      } else {

        if (WSDHelper.getNonRelevWordsDef(tokenTags[i]) != null) {
          String sense = WSDParameters.SenseSource.WSDHELPER.name() + " "
              + WSDHelper.getNonRelevWordsDef(tokenTags[i]);
          senses.add(sense);
        } else {
          senses.add(null);
        }
      }

    }

    return senses;
  }

  /**
   * @param sample
   * @return result as an array of WordNet IDs
   */
  public abstract String disambiguate(WSDSample sample);

}