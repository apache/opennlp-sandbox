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

import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.Page;

class NameFinderViewPage extends Page implements ISelectionListener {

  private ICasEditor editor;

  private TableViewer entityList;
  
  NameFinderViewPage(ICasEditor editor, ICasDocument document) {
    this.editor = editor;
  }

  public void createControl(Composite parent) {
    entityList = new TableViewer(parent, SWT.NONE);
  }

  public void selectionChanged(IWorkbenchPart part, ISelection selection) {
  }

  public Control getControl() {
    return entityList.getControl();
  }

  public void setFocus() {
    entityList.getControl().setFocus();
  }

}
