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

package org.apache.opennlp.caseditor.namefinder;

import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.opennlp.caseditor.TypeListFieldEditor;
import org.apache.uima.cas.TypeSystem;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class NameFinderPreferencePage extends FieldEditorPreferencePage
    implements IWorkbenchPreferencePage {

  private TypeSystem ts;

  public NameFinderPreferencePage(TypeSystem ts) {
    setDescription("Name Finder Preferences.");
    this.ts = ts;
  }

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected void createFieldEditors() {
    TypeListFieldEditor additionalSentenceTypes = new TypeListFieldEditor(
        OpenNLPPreferenceConstants.ADDITIONAL_SENTENCE_TYPE,
        "Additional Sentence Types", ts, getFieldEditorParent());
    addField(additionalSentenceTypes);
    
    // TODO: We need a new input control for this one
    // user needs to enter model path
    // and type at the same time 
    
    NameFinderModelFieldEditor modelPath = new NameFinderModelFieldEditor(
            OpenNLPPreferenceConstants.NAME_FINDER_MODEL_PATH, 
            "Model paths and types", ts, getFieldEditorParent());
    addField(modelPath);
    
    // TODO: We need a view settings which are enabled/disabled based on this one
//    BooleanFieldEditor enableRecallBoosting = new BooleanFieldEditor(
//        OpenNLPPreferenceConstants.ENABLE_CONFIRMED_NAME_DETECTION,
//        "Force the detection of confirmed names", getFieldEditorParent());
//    addField(enableRecallBoosting);
    
    ConfirmedNameDetectionFieldEditor forceNameDetection =
        new ConfirmedNameDetectionFieldEditor(getFieldEditorParent());
    addField(forceNameDetection);
    // Add a group
    // Other options should be have an indent ...
  }
}
