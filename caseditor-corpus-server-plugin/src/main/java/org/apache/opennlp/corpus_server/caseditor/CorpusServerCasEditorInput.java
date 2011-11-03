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

package org.apache.opennlp.corpus_server.caseditor;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;

// Serialization must be supported, right?!
public class CorpusServerCasEditorInput implements IEditorInput {

  private String corpusUrl;
  private String casId;
  
  CorpusServerCasEditorInput(String corpusUrl, String casId) {
    this.corpusUrl = corpusUrl;
    this.casId = casId;
  }
  
  @Override
  public Object getAdapter(@SuppressWarnings("rawtypes") Class clazz) {
    return null;
  }

  // can be checked through an http lookup
  @Override
  public boolean exists() {
    return false;
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    return null;
  }

  @Override
  public String getName() {
    return casId;
  }

  // it is ... 
  @Override
  public IPersistableElement getPersistable() {
    return null;
  }

  @Override
  public String getToolTipText() {
    return "";
  }

  public String getServerUrl() {
    return corpusUrl;
  }
  
  @Override
  public boolean equals(Object obj) {
    
    if (obj == this) {
      return true;
    }
    else if (obj instanceof CorpusServerCasEditorInput) {
      
      CorpusServerCasEditorInput input = 
          (CorpusServerCasEditorInput) obj;
      
      return (corpusUrl + casId).equals(input.corpusUrl + input.casId);
    }
    else {
      return false;
    }
  }
  
  @Override
  public int hashCode() {
    return (corpusUrl + casId).hashCode();
  }
}
