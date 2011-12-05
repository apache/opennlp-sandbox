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
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;

/**
 * Field Editor for a list of UIMA type names.
 */
public class TypeListFieldEditor extends FieldEditor {
  
  private List typeList;
  private TypeSystem ts;
  
  public TypeListFieldEditor(String name, String labelText,
      TypeSystem ts, Composite parent) {
    super(name, labelText, parent);
    this.ts = ts;
  }
  
  @Override
  protected void adjustForNumColumns(int numColumns) {
  }

  @Override
  protected void doFillIntoGrid(final Composite parent,
      int numColumns) {
    Label messageLabel = getLabelControl(parent);
    
    // TODO: Should be on top of list control .. then it has more space!
    
    typeList = new List(parent, SWT.BORDER | SWT.SINGLE
        | SWT.V_SCROLL | SWT.H_SCROLL);
    GridData gd = new GridData();
    gd.horizontalSpan = numColumns - 1;
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    gd.verticalAlignment = GridData.FILL;
    
    typeList.setLayoutData(gd);
    
    // TODO: The buttons should be moved to the right next to the control ...
    
    Button addButton = new Button(parent, SWT.PUSH);
    addButton.setText("Add");
    addButton.addSelectionListener(new SelectionListener() {
      
      @Override
      public void widgetSelected(SelectionEvent event) {
        // We need a reference to the type system here ...
        // open dialog to ask for new type ...
        // dialog should contain a list of existing types ...
        TypeInputDialog dialog = new TypeInputDialog(parent.getShell(), ts);
        dialog.open();
        String typeName = dialog.getValue();
        
        if (typeName != null) {
          typeList.add(typeName);
        }
      }
      
      @Override
      public void widgetDefaultSelected(SelectionEvent event) {
        // will never be called
      }
    });
    
    // TODO: only enabled when an item in the list is selected
    Button removeButton = new Button(parent, SWT.PUSH);
    removeButton.setText("Remove");
    removeButton.addSelectionListener(new SelectionListener() {
      
      @Override
      public void widgetSelected(SelectionEvent event) {
        int selectedItem = typeList.getSelectionIndex();
        if (selectedItem != -1) {
          typeList.remove(selectedItem);
        }
      }
      
      @Override
      public void widgetDefaultSelected(SelectionEvent arg0) {
        // will never be called
      }
    });
    
  }

  @Override
  protected void doLoad() {
    if (typeList != null) {
      String value = getPreferenceStore().getString(getPreferenceName());
      
      String types[] = getTypeList(value);
      
      for (String type : types) {
        typeList.add(type);
      }
    }
  }

  @Override
  protected void doLoadDefault() {
    // do nothing, there are no defaults
  }

  @Override
  protected void doStore() {
    
    StringBuilder typeListString = new StringBuilder();
    
    for (String type : typeList.getItems()) {
      typeListString.append(type);
      typeListString.append(",");
    }
    
    if (typeListString.length() > 0) {
      typeListString.setLength(typeListString.length() - 1);
    }
    
    // create string value ...
    getPreferenceStore().setValue(getPreferenceName(), typeListString.toString());
  }

  @Override
  public int getNumberOfControls() {
    return 4;
  }
  
  public static String[] getTypeList(String typeListString) {
    String types[] = typeListString.split(",");
    
    for (int i = 0; i < types.length; i++) {
      types[i] = types[i].trim();
    }
    
    return types;
  }
}
