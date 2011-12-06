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
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;

/**
 * Field Editor for a list of UIMA type names.
 */
// TODO: Enforce that no duplicate entries can be created
public class TypeListFieldEditor extends FieldEditor {
  
  private List typeList;
  private TypeSystem ts;
  private Button removeButton;
  
  public TypeListFieldEditor(String name, String labelText,
      TypeSystem ts, Composite parent) {
    super(name, labelText, parent);
    this.ts = ts;
  }
  
  @Override
  protected void adjustForNumColumns(int numColumns) {
  }

  private void checkState() {
    removeButton.setEnabled(typeList.getSelectionCount() > 0);
  }
  
  @Override
  protected void doFillIntoGrid(final Composite parent,
      int numColumns) {
    Label messageLabel = getLabelControl(parent);
    
    GridData messageGridDate = new GridData();
    messageGridDate.horizontalSpan = numColumns;
    messageLabel.setLayoutData(messageGridDate);
    
    typeList = new List(parent, SWT.BORDER | SWT.SINGLE
        | SWT.V_SCROLL | SWT.H_SCROLL);
    GridData gd = new GridData();
    gd.horizontalSpan = numColumns - 1;
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    gd.verticalAlignment = GridData.FILL;
    
    typeList.setLayoutData(gd);
    typeList.addListener(SWT.Selection, new Listener(){

      @Override
      public void handleEvent(Event event) {
        checkState();
      }});
    
    Composite buttonGroup = new Composite(parent, SWT.NONE);
    GridLayout buttonLayout = new GridLayout();
    buttonGroup.setLayout(buttonLayout);
    
    Button addButton = new Button(buttonGroup, SWT.PUSH);
    addButton.setText("Add");
    addButton.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {
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
    });
    
    addButton.setLayoutData(GridDataFactory.fillDefaults().create());
    
    // TODO: only enabled when an item in the list is selected
    removeButton = new Button(buttonGroup, SWT.PUSH);
    removeButton.setText("Remove");
    removeButton.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {
        int selectedItem = typeList.getSelectionIndex();
        if (selectedItem != -1) {
          typeList.remove(selectedItem);
        }
        
        checkState();
        
      }
    });
    
    removeButton.setLayoutData(GridDataFactory.fillDefaults().create());
    
    checkState();
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
    getPreferenceStore().setValue(getPreferenceName(), listToString(typeList.getItems()));
  }

  @Override
  public int getNumberOfControls() {
    return 3;
  }
  
  public static String listToString(String types[]) {
    StringBuilder typeListString = new StringBuilder();
    
    for (String type : types) {
      typeListString.append(type);
      typeListString.append(",");
    }
    
    if (typeListString.length() > 0) {
      typeListString.setLength(typeListString.length() - 1);
    }
    
    return typeListString.toString();
  }
  
  public static String[] getTypeList(String typeListString) {
    String types[] = typeListString.split(",");
    
    for (int i = 0; i < types.length; i++) {
      types[i] = types[i].trim();
    }
    
    return types;
  }
}
