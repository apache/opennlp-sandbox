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

import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.Page;

public class TokenizerViewPage extends Page {
  
  private ICasEditor editor;

  private Label testLabel;
  
  TokenizerViewPage(ICasEditor editor) {
    this.editor = editor;
  }

  @Override
  public void createControl(Composite parent) {
    testLabel = new Label(parent, SWT.NONE);
    testLabel.setText("This is a test :)");
  }

  @Override
  public Control getControl() {
    return testLabel;
  }

  @Override
  public void setFocus() {
    testLabel.setFocus();
  }
}
