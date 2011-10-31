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

package org.apache.opennlp.caseditor.tokenize;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.ModelUtil;
import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public class TokenizerJob extends Job {

  private String algorithm;
  
  private String modelPath;
  
  private String text;
  
  private Tokenizer tokenizer;
  
  private Span[] tokens;
  
  public TokenizerJob() {
    super("Tokenizer Job");
  }

  void setTokenizerAlgorithm(String algorithm) {
    this.algorithm = algorithm;
    tokenizer = null;
  }
  
  void setModelPath(String modelPath) {
    this.modelPath = modelPath;
  }
  
  void setText(String text) {
    this.text = text;
  }
  
  @Override
  protected IStatus run(IProgressMonitor monitor) {
    
    if (OpenNLPPreferenceConstants.TOKENIZER_ALGO_WHITESPACE.equals(algorithm)) {
      tokenizer = WhitespaceTokenizer.INSTANCE;
    } else if (OpenNLPPreferenceConstants.TOKENIZER_ALGO_SIMPLE.equals(algorithm)) {
      tokenizer = SimpleTokenizer.INSTANCE;
    } else if (OpenNLPPreferenceConstants.TOKENIZER_ALGO_STATISTICAL.equals(algorithm)) {
      if (tokenizer == null) {
        InputStream modelIn;
        try {
          modelIn = ModelUtil.openModelIn(modelPath);
        } catch (IOException e1) {
          return new Status(IStatus.CANCEL, OpenNLPPlugin.ID, "Failed to load tokenizer model!");
        }
        
        try {
          TokenizerModel model = new TokenizerModel(modelIn);
          tokenizer = new TokenizerME(model);
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          if (modelIn != null) {
            try {
              modelIn.close();
            } catch (IOException e) {
            }
          }
        }
      }
    }
    else {
      // TODO: Report an error!
    }
    
    tokens = tokenizer.tokenizePos(text);
    
    return new Status(IStatus.OK, OpenNLPPlugin.ID, "OK");
  }
  
  Span[] getTokens() {
    return tokens;
  }
}
