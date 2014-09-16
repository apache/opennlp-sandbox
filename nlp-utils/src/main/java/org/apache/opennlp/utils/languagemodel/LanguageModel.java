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
package org.apache.opennlp.utils.languagemodel;

import java.util.Collection;

/**
 * A language model can calculate the probability <i>p</i> (between 0 and 1) of a
 * certain set of <code>T</code> objects, given a vocabulary.
 * <code>T</code> is usually an {@link java.lang.Iterable} or an array as language models are very commonly used for
 * sentences, so that T is e.g. an array of <code>String</code>s.
 */
public interface LanguageModel<T> {

  /**
   * Calculate the probability of a sample, given a vocabulary
   *
   * @param vocabulary a {@link Collection} of objects of type <code>T</code>
   * @param sample     the sample to evaluate the probability for
   * @return a <code>double</code> between <code>0</code> and <code>1</code>
   */
  double calculateProbability(Collection<T> vocabulary, T sample);

}
