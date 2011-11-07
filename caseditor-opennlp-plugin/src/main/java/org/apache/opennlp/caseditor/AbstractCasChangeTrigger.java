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

import java.util.Collection;

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.caseditor.editor.ICasDocumentListener;

public abstract class AbstractCasChangeTrigger implements ICasDocumentListener {

  /**
   * Trigger a run of the opennlp component, a change to the CAS was detected.
   */
  protected abstract void trigger();
  
  @Override
  public void added(FeatureStructure fs) {
    trigger();
  }

  @Override
  public void added(Collection<FeatureStructure> featureStructures) {
    trigger();
  }

  @Override
  public void changed() {
    trigger();
  }

  @Override
  public void removed(FeatureStructure fs) {
    trigger();
  }

  @Override
  public void removed(Collection<FeatureStructure> featureStructures) {
    trigger();
  }

  @Override
  public void updated(FeatureStructure fs) {
    trigger();
  }

  @Override
  public void updated(Collection<FeatureStructure> featureStructures) {
    trigger();
    
  }

  @Override
  public void viewChanged(String oldView, String newView) {
    trigger();
  }
}
