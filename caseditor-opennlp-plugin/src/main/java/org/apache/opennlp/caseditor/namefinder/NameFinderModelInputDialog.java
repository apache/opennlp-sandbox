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

import org.apache.uima.cas.TypeSystem;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to add or edit a model and type mapping.
 */
// TODO: Add validation
public class NameFinderModelInputDialog extends Dialog {

  private TypeSystem ts;

  private String dialogTitle;

  private String modelTextValue = "";
  private String typeNameTextValue = "";

  private Text modelText;

  private Text typeNameText;

  public NameFinderModelInputDialog(Shell parentShell, String dialogTitle, TypeSystem ts) {
    super(parentShell);
    this.dialogTitle = dialogTitle;

    this.ts = ts;
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);

    if (dialogTitle != null) {
      newShell.setText(dialogTitle);
    }

    newShell.setSize(600, 150);
  }

  @Override
  protected Control createDialogArea(Composite parent) {

    Composite dialogArea = (Composite) super.createDialogArea(parent);

    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    dialogArea.setLayout(layout);

    Label modelLabel = new Label(dialogArea, SWT.NONE);
    modelLabel.setText("Model path");

    modelText = new Text(dialogArea, SWT.BORDER);
    modelText.setLayoutData(GridDataFactory.swtDefaults().
        align(SWT.FILL, SWT.CENTER).grab(true,  false).create());
    modelText.setText(modelTextValue);
    modelText.addListener(SWT.Modify, new Listener(){

      @Override
      public void handleEvent(Event event) {
        modelTextValue = modelText.getText();
      }});

// TODO: Implement browse button
//    Button browseButton = new Button(dialogArea, SWT.PUSH);
//    browseButton.setText("Browse...");

    Label typeNameLabel = new Label(dialogArea, SWT.NONE);
    typeNameLabel.setText("Type name");

    typeNameText = new Text(dialogArea, SWT.BORDER);
    typeNameText.setLayoutData(GridDataFactory.swtDefaults().
        align(SWT.FILL, SWT.CENTER).grab(true,  false).create());
    typeNameText.setText(typeNameTextValue);
    typeNameText.addListener(SWT.Modify, new Listener() {

      @Override
      public void handleEvent(Event event) {
        typeNameTextValue = typeNameText.getText();
      }
    });

    return dialogArea;
  }

  public void setModelPath(String value) {
    modelTextValue = value;
  }

  String getModelPath() {
    return modelTextValue;
  }

  String getTypeName() {
    return typeNameTextValue;
  }

  public void setTypeName(String value) {
    typeNameTextValue = value;
  }
}
