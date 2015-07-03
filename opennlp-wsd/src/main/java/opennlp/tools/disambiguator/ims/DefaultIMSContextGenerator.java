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

package opennlp.tools.disambiguator.ims;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import opennlp.tools.disambiguator.FeaturesExtractor;
import opennlp.tools.disambiguator.ims.WTDIMS;

public class DefaultIMSContextGenerator implements IMSContextGenerator {

  FeaturesExtractor fExtractor = new FeaturesExtractor();

  /**
   * Default context generator for IMS.
   */

  public DefaultIMSContextGenerator() {
  }

  /**
   * Get Context of a word To disambiguate
   */
  @Override
  public String[] getContext(WTDIMS word) {
    return fExtractor.serializeIMSFeatures(word);
  }

  /**
   * Returns an {@link ArrayList} of features for the object of type WTDIMS
   * Extensions of this class can override this method to create a customized
   * {@link IMSContextGenerator}
   *
   * @param word
   *          : the word to disambiguate {@link WTDIMS} along with its sentence
   *          [Check the Class WTDIMS]
   * @param numberOfSurroundingWords
   *          : the number of surrounding words used in the feature
   *          "POS Tags of Surrounding Words" Default value is 3
   * @param ngram
   *          : the number of words used to extract the feature
   *          "Local Collocations" Default value is 2
   * 
   * @return an {@link ArrayList} of features
   */

  protected List<String> createContext(WTDIMS word) {
    return Arrays.asList(getContext(word));
  }
}
