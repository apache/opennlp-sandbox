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

import org.apache.uima.cas.TypeSystem;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.swt.widgets.Composite;

/**
 * Field editor to input a single UIMA type.
 */
public class TypeFieldEditor extends StringButtonFieldEditor {

  private TypeSystem ts;

  public TypeFieldEditor(String name, String labelText, TypeSystem ts, Composite parent) {
    super(name, labelText, parent);

    this.ts = ts;
    
    setChangeButtonText("Browse...");
  }
  
  @Override
  protected boolean doCheckState() {
    
    boolean isInputValid = ts.getType(getStringValue()) != null;
    
    if (!isInputValid) {
      setErrorMessage("Entered type name does not exist or ist not valid!");
    }
     
    return isInputValid;
  }
  
  @Override
  protected String changePressed() {
    TypeInputDialog dialog = new TypeInputDialog(getShell(), ts);
    dialog.open();
    if (InputDialog.OK == dialog.getReturnCode()) {
      return dialog.getValue();
    }
    else {
      return getStringValue();
    }
  }
}
