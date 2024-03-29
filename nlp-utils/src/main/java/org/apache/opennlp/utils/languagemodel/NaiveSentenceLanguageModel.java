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
import java.util.Collections;

/**
 * Simple sentence language model which just counts the occurrences of
 * a sentence over the number of sentences in the vocabulary.
 */
public class NaiveSentenceLanguageModel<T> implements LanguageModel<T[]> {

  @Override
  public double calculateProbability(Collection<T[]> vocabulary, T[] sentence) {
    return vocabulary.isEmpty() ? 0 : (double) Collections.frequency(vocabulary, sentence) / vocabulary.size();
  }

}
