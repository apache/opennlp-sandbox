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

import java.util.ArrayList;
import java.util.List;

import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.opennlp.caseditor.TypeListFieldEditor;
import org.apache.uima.cas.TypeSystem;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

/**
 * Field editor for configuring name finder models and corresponding types.
 */

// TODO:
// Model type names are encoded into two string and saved to the pref store.
// That should be enhanced and in a reliable way coded into one string.

class NameFinderModelFieldEditor extends FieldEditor {

  private static final String MODEL_PATH_KEY = "ModelPathKey";
  private static final String TYPE_NAME_KEY = "TypeNameKey";

  private TypeSystem ts;

  private Table modelTypeTable;
  private Button editButton;
  private Button removeButton;

  NameFinderModelFieldEditor(String name, String labelText, TypeSystem ts, Composite parent) {
    super(name, labelText, parent);
    this.ts = ts;
  }

  @Override
  protected void adjustForNumColumns(int numColumns) {
  }

  private void createTableItem(String modelPath, String typeName) {
    TableItem item = new TableItem(modelTypeTable, SWT.NONE);
    item.setData(MODEL_PATH_KEY, modelPath);
    item.setText(0, modelPath);

    item.setData(TYPE_NAME_KEY, typeName);
    item.setText(1, typeName);
  }

  private void checkState() {
    editButton.setEnabled(modelTypeTable.getSelectionCount() == 1);
    removeButton.setEnabled(modelTypeTable.getSelectionCount() == 1);
  }

  @Override
  protected void doFillIntoGrid(final Composite parent, int numColumns) {
    Label messageLabel = getLabelControl(parent);

    GridData messageGridDate = new GridData();
    messageGridDate.horizontalSpan = numColumns;
    messageLabel.setLayoutData(messageGridDate);

    modelTypeTable = new Table(parent, SWT.BORDER);

    GridData gd = new GridData();
    gd.horizontalSpan = numColumns - 1;
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    gd.verticalAlignment = GridData.FILL;
    modelTypeTable.setLayoutData(gd);

    modelTypeTable.setLinesVisible(true);
    modelTypeTable.setHeaderVisible(true);

    modelTypeTable.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {
        checkState();
      }});

    TableColumn modelColumn = new TableColumn(modelTypeTable, SWT.NONE);
    modelColumn.setText("Model Path");
    modelColumn.setWidth(220);

    TableColumn typeColumn = new TableColumn(modelTypeTable, SWT.NONE);
    typeColumn.setText("Type");
    typeColumn.setWidth(220);

    Composite buttonGroup = new Composite(parent, SWT.NONE);
    GridLayout buttonLayout = new GridLayout();
    buttonGroup.setLayout(buttonLayout);

    Button addButton = new Button(buttonGroup, SWT.PUSH);
    addButton.setLayoutData(GridDataFactory.fillDefaults().create());
    addButton.setText("Add");

    addButton.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {

        NameFinderModelInputDialog dialog = new NameFinderModelInputDialog(
            parent.getShell(), "Add a name finder model", ts);

        if (Dialog.OK == dialog.open()) {
          createTableItem(dialog.getModelPath(), dialog.getTypeName());
        }
      }});

    editButton = new Button(buttonGroup, SWT.PUSH);
    editButton.setLayoutData(GridDataFactory.fillDefaults().create());
    editButton.setText("Edit");

    editButton.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {
        NameFinderModelInputDialog dialog = new NameFinderModelInputDialog(parent.getShell(),
            "Edit name finder model", ts);

        TableItem item = modelTypeTable.getItem(modelTypeTable.getSelectionIndex());

        dialog.setModelPath((String) item.getData(MODEL_PATH_KEY));
        dialog.setTypeName((String) item.getData(TYPE_NAME_KEY));

        if (Dialog.OK == dialog.open()) {
          item.setData(MODEL_PATH_KEY, dialog.getModelPath());
          item.setText(0, dialog.getModelPath());

          item.setData(TYPE_NAME_KEY, dialog.getTypeName());
          item.setText(1, dialog.getTypeName());
        }
      }});

    removeButton = new Button(buttonGroup, SWT.PUSH);
    removeButton.setLayoutData(GridDataFactory.fillDefaults().create());
    removeButton.setText("Remove");

    removeButton.addListener(SWT.Selection, new Listener() {

      @Override
      public void handleEvent(Event event) {

        modelTypeTable.remove(modelTypeTable.getSelectionIndex());

        checkState();
      }});

    checkState();
  }

  @Override
  protected void doLoad() {
    if (modelTypeTable != null) {
      String modelPathsString = getPreferenceStore().getString(OpenNLPPreferenceConstants.NAME_FINDER_MODEL_PATH);
      String modelPaths[] = TypeListFieldEditor.getTypeList(modelPathsString);

      String typeNamesString = getPreferenceStore().getString(OpenNLPPreferenceConstants.NAME_TYPE);
      String typeNames[] = TypeListFieldEditor.getTypeList(typeNamesString);

      // Don't load anything ...
      if (modelPaths.length != typeNames.length) {
        // TODO: Log error message
        return;
      }

      for (int i = 0; i < modelPaths.length; i++) {
        createTableItem(modelPaths[i], typeNames[i]);
      }
    }
  }

  @Override
  protected void doLoadDefault() {
    // there is no default
  }

  @Override
  protected void doStore() {

    List<String> modelPaths = new ArrayList<String>();
    List<String> typeNames = new ArrayList<String>();

    // iterate over table
    for (int i = 0; i < modelTypeTable.getItemCount(); i++) {
      TableItem item = modelTypeTable.getItem(i);

      String modelPath = (String) item.getData(MODEL_PATH_KEY);
      modelPaths.add(modelPath);

      String typeName = (String) item.getData(TYPE_NAME_KEY);
      typeNames.add(typeName);
    }

    String modelPathsString = TypeListFieldEditor.listToString(modelPaths.toArray(new String[modelPaths.size()]));
    String typeNamesString = TypeListFieldEditor.listToString(typeNames.toArray(new String[typeNames.size()]));

    getPreferenceStore().setValue(OpenNLPPreferenceConstants.NAME_FINDER_MODEL_PATH, modelPathsString);
    getPreferenceStore().setValue(OpenNLPPreferenceConstants.NAME_TYPE, typeNamesString);
  }

  @Override
  public int getNumberOfControls() {
    return 3;
  }
}
