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

package opennlp.tools;

import opennlp.tools.disambiguator.WSDHelper;
import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractTest {

  protected static final String SENSEVAL_DIR = "src/test/resources/senseval3/";
  protected static final String SEMCOR_DIR = "src/test/resources/semcor3.0/";

  @BeforeAll
  static void initEnv() {
    String lang = "en";
    WSDHelper.loadTokenizer(lang);
    WSDHelper.loadTagger(lang);
    WSDHelper.loadLemmatizer(lang);
  }
}
