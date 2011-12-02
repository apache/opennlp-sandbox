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

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.ui.actions.BaseSelectionListenerAction;

public class ConfirmAnnotationAction extends BaseSelectionListenerAction {
  
  private TableViewer entityList;
  
  private ICasEditor editor;
  
  public ConfirmAnnotationAction(TableViewer entityList, ICasEditor editor) {
    super("Confirm");
    
    if (entityList == null || editor == null)
      throw new IllegalArgumentException("null values are not allowed!");
    
    this.entityList = entityList;
    this.editor = editor;
  }
  
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    return !selection.isEmpty();
  }
  
  // Note: Action can only handle one element.
  //       Must be extended to handle "bulk" confirms
  @Override
  public void run() {
    super.run();
    
    // get selected entities and add annotations to the CAS
    IStructuredSelection selection = 
        (IStructuredSelection) entityList.getSelection();
    
    Object elements[] = selection.toArray();

    if (elements.length > 0) {
      PotentialAnnotation selectedEntity = (PotentialAnnotation) elements[0];
        
      FeatureStructure nameAnnotation = editor.getDocument().getCAS().createAnnotation(
          editor.getDocument().getCAS().getTypeSystem().getType(selectedEntity.getType()),
          selectedEntity.getBeginIndex(), selectedEntity.getEndIndex());
      editor.getDocument().addFeatureStructure(nameAnnotation);
    }
  }
}