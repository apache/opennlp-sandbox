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

package org.apache.opennlp.namefinder;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PredictionConfiguration {

  private final String vocabWords;
  private final String vocabChars;
  private final String vocabTags;
  private final String savedModel;

  public PredictionConfiguration(String vocabWords, String vocabChars, String vocabTags, String savedModel) {
    this.vocabWords = vocabWords;
    this.vocabChars = vocabChars;
    this.vocabTags = vocabTags;
    this.savedModel = savedModel;
  }

  public String getVocabWords() {
    return vocabWords;
  }

  public String getVocabChars() {
    return vocabChars;
  }

  public String getVocabTags() {
    return vocabTags;
  }

  public String getSavedModel() {
    return savedModel;
  }

  public InputStream getVocabWordsInputStream() throws IOException{
    return new FileInputStream(getVocabWords());
  }
}
