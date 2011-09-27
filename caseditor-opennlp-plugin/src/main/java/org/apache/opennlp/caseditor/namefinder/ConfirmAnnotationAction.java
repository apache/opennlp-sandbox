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

import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.ui.actions.BaseSelectionListenerAction;

public class ConfirmAnnotationAction extends BaseSelectionListenerAction {
  
  private TableViewer entityList;
  private ICasDocument document;
  
  public ConfirmAnnotationAction(TableViewer entityList, ICasDocument document) {
    super("Confirm");
    
    if (entityList == null || document == null)
      throw new IllegalArgumentException("null values are not allowed!");
    
    this.entityList = entityList;
    this.document = document;
  }
  
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    
    boolean result = false;
    
    if (!selection.isEmpty()) {
      Entity entity = (Entity) selection.getFirstElement();
      return !entity.isConfirmed();
    }
    
    return result;
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
      Entity selectedEntity = (Entity) elements[0];
      if (!selectedEntity.isConfirmed()) {
        
        FeatureStructure nameAnnotation = document.getCAS().createAnnotation(
            document.getCAS().getTypeSystem().getType(selectedEntity.getType()),
            selectedEntity.getBeginIndex(), selectedEntity.getEndIndex());
//        document.getCAS().addFsToIndexes(nameAnnotation);
        document.addFeatureStructure(nameAnnotation);
      }
    }
  }
}