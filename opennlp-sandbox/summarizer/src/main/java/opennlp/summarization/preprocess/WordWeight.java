/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.summarization.preprocess;

/**
 * Represents a type which can compute the weight of a word in a certain context, e.g. a sentence or a text.
 */
public interface WordWeight {

  /**
   * @param token The input token (word) to get a weight for. Must not be {@code null}.
   * @return The associated weight for the specified {@code token}.
   */
  double getWordWeight(String token);
}
