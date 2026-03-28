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

package org.apache.opennlp.caseditor;

public class OpenNLPPreferenceConstants {

  // General settings
  public static final String SENTENCE_TYPE = OpenNLPPlugin.ID + ".SENTENCE_TYPE";
  public static final String TOKEN_TYPE = OpenNLPPlugin.ID + ".TOKEN_TYPE";

  // Sentence detector
  public static final String PARAGRAPH_TYPE = OpenNLPPlugin.ID + ".PARAGRAPH_TYPE";
  public static final String SENTENCE_DETECTOR_MODEL_PATH = OpenNLPPlugin.ID + ".SENTENCE_DETECTOR_MODEL_PATH";
  public static final String SENT_EXCLUSION_TYPE = OpenNLPPlugin.ID + ".SENT_EXCLUSION_TYPE";

  // Name Finder  
  public static final String ADDITIONAL_SENTENCE_TYPE = OpenNLPPlugin.ID + ".ADDITIONAL_SENTENCE_TYPE";
  public static final String NAME_TYPE = OpenNLPPlugin.ID + ".NAME_TYPE";
  public static final String NAME_FINDER_MODEL_PATH = OpenNLPPlugin.ID + ".NAME_FINDER_MODEL_PATH";
  public static final String ENABLE_CONFIRMED_NAME_DETECTION = OpenNLPPlugin.ID + ".ENABLE_RECALL_BOOSTING";
  public static final String IGNORE_SHORT_TOKENS = OpenNLPPlugin.ID + ".IGNORE_SHORT_TOKENS";
  public static final String ONLY_CONSIDER_ALL_LETTER_TOKENS = OpenNLPPlugin.ID + ".ONLY_CONSIDER_ALL_LETTER_TOKENS";
  public static final String ONLY_CONSIDER_INITIAL_CAPITAL_TOKENS = OpenNLPPlugin.ID + ".ONLY_CONSIDER_INITIAL_CAPITAL_TOKENS";

  // Tokenizer
  public static final String TOKENIZER_MODEL_PATH = OpenNLPPlugin.ID + ".TOKENIZER_MODEL_PATH";
  public static final String TOKENIZER_ALGORITHM = OpenNLPPlugin.ID + ".TOKENIZER_ALGORITHM";
  public static final String TOKENIZER_ALGO_STATISTICAL = OpenNLPPlugin.ID + ".TOKENIZER_ALGO_STATISTICAL";
  public static final String TOKENIZER_ALGO_WHITESPACE = OpenNLPPlugin.ID + ".TOKENIZER_ALGO_WHITESPACE";
  public static final String TOKENIZER_ALGO_SIMPLE = OpenNLPPlugin.ID + ".TOKENIZER_ALGO_SIMPLE";
}
