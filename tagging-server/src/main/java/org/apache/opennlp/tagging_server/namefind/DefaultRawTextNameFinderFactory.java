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

package org.apache.opennlp.tagging_server.namefind;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public class DefaultRawTextNameFinderFactory implements RawTextNameFinderFactory {
  
  private final SentenceModel sentModel;
  private final TokenizerModel tokenModel;
  private final TokenNameFinderModel nameModels[];

  // TODO: How can this be an array of models with blueprint?!
  
  public DefaultRawTextNameFinderFactory(SentenceModel sentModel,
      TokenizerModel tokenModel, TokenNameFinderModel nameModels[]) {
    this.sentModel = sentModel;
    this.tokenModel = tokenModel;
    this.nameModels = nameModels;
  }

  @Override
  public SentenceDetector createSentenceDetector() {
    return new SentenceDetectorME(sentModel);
  }

  @Override
  public Tokenizer createTokenizer() {
    return new TokenizerME(tokenModel);
  }

  @Override
  public TokenNameFinder[] createNameFinders() {
    
    TokenNameFinder nameFinders[] = new TokenNameFinder[nameModels.length];
    
    for (int i = 0; i < nameFinders.length; i++) {
      nameFinders[i] = new NameFinderME(nameModels[i]);
    }
    
    return nameFinders;
  }
}
