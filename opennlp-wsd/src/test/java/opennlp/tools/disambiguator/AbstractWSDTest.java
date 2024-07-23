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

import opennlp.tools.lemmatizer.Lemmatizer;
import org.junit.jupiter.api.BeforeAll;

abstract class AbstractWSDTest extends AbstractTest {

  static final String test1 = "We need to discuss an important topic, please write to me soon.";
  static final String test2 = "The component was highly radioactive to the point that"
          + " it has been activated the second it touched water";
  static final String test3 = "The summer is almost over and I did not go to the beach even once";

  static String[] sentence1;
  static String[] sentence2;
  static String[] sentence3;

  static String[] tags1;
  static String[] tags2;
  static String[] tags3;

  static String[] lemmas1;
  static String[] lemmas2;
  static String[] lemmas3;

  @BeforeAll
  public static void prepareResources() {
    sentence1 = WSDHelper.getTokenizer().tokenize(test1);
    sentence2 = WSDHelper.getTokenizer().tokenize(test2);
    sentence3 = WSDHelper.getTokenizer().tokenize(test3);

    tags1 = WSDHelper.getTagger().tag(sentence1);
    tags2 = WSDHelper.getTagger().tag(sentence2);
    tags3 = WSDHelper.getTagger().tag(sentence3);

    final Lemmatizer lemmatizer = WSDHelper.getLemmatizer();
    lemmas1 = lemmatizer.lemmatize(sentence1, tags1);
    lemmas2 = lemmatizer.lemmatize(sentence2, tags2);
    lemmas3 = lemmatizer.lemmatize(sentence3, tags3);
  }
}
