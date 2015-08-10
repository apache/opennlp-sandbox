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

package opennlp.tools.disambiguator.contextclustering;

import java.security.InvalidParameterException;

import opennlp.tools.disambiguator.WSDParameters;
import opennlp.tools.disambiguator.WSDSample;
import opennlp.tools.disambiguator.WSDisambiguator;
import opennlp.tools.util.Span;

/**
 * Implementation of the <b>Context Clustering</b> approach. This approach
 * returns uses n-gram based clusters.
 * 
 * This implementation is based on {@link http://nlp.cs.rpi.edu/paper/wsd.pdf}
 */
public class ContextClusterer implements WSDisambiguator {

  protected ContextClustererParameters params;

  @Override
  public WSDParameters getParams() {
    return params;
  }

  @Override
  public void setParams(WSDParameters params) throws InvalidParameterException {
    if (params == null) {
      this.params = new ContextClustererParameters();
    } else {
      if (params.isValid()) {
        this.params = (ContextClustererParameters) params;
      } else {
        throw new InvalidParameterException("wrong params");
      }
    }
  }

  @Override
  public String[] disambiguate(String[] tokenizedContext, String[] tokenTags,
      int ambiguousTokenIndex, String ambiguousTokenLemma) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String[][] disambiguate(String[] tokenizedContext, String[] tokenTags,
      Span ambiguousTokenIndexSpan, String ambiguousTokenLemma) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String[] disambiguate(WSDSample sample) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String[] disambiguate(String[] inputText, int inputWordIndex) {
    // TODO Auto-generated method stub
    return null;
  }

}
