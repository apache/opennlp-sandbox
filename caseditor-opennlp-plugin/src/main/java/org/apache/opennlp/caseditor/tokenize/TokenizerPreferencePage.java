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

import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class TokenizerPreferencePage extends FieldEditorPreferencePage
    implements IWorkbenchPreferencePage {

  public TokenizerPreferencePage() {
    setDescription("Tokenizer Preferences.");
  }
  
  @Override
  public void init(IWorkbench workbench) {
  }

  // TODO: Do it like in the "Build Path" path preference page ... 
  //       radio combined with text field
  @Override
  protected void createFieldEditors() {
    
    // TODO: Need to set a default value
    
    String[][] namesAndValues = new String[][]{
        new String[]{"Statistical", OpenNLPPreferenceConstants.TOKENIZER_ALGO_STATISTICAL},
        new String[]{"Whitespace", OpenNLPPreferenceConstants.TOKENIZER_ALGO_WHITESPACE},
        new String[]{"Simple", OpenNLPPreferenceConstants.TOKENIZER_ALGO_SIMPLE}
    };
    
    ComboFieldEditor algorithmCombo = new ComboFieldEditor(OpenNLPPreferenceConstants.TOKENIZER_ALGORITHM, 
        "Algorithm", namesAndValues, getFieldEditorParent());
    addField(algorithmCombo);

    // Activate only if statistical is selected .. how to do that?
    StringFieldEditor modelPath = new StringFieldEditor(
        OpenNLPPreferenceConstants.TOKENIZER_MODEL_PATH,
        "Model Path", getFieldEditorParent());
    addField(modelPath);
  }

}
