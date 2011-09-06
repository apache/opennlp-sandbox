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

import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.apache.uima.caseditor.editor.util.AnnotationSelection;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.part.Page;


// TODO: Selected entities should be highlighted in the annotation editor!
//       How can that be done? Should we simply create feature structures, which are not added to the index!?         

// TODO: There should be a way to display error messages in this view, e.g.
//       when no names are detected. -> give an indication what could be wrong!
class NameFinderViewPage extends Page implements ISelectionListener {

  private ICasEditor editor;

  private TableViewer entityList;

  private String nameTypeName;
  
  NameFinderViewPage(ICasEditor editor, ICasDocument document) {
    this.editor = editor;
    
    IPreferenceStore store = OpenNLPPlugin.getDefault().getPreferenceStore();
    nameTypeName = store.getString(OpenNLPPreferenceConstants.NAME_TYPE);
  }

  public void createControl(Composite parent) {
    
    getSite().getPage().addSelectionListener(this);
    
    entityList = new TableViewer(parent, SWT.NONE);
    
    Table entityTable = entityList.getTable();
    entityTable.setHeaderVisible(true);
    entityTable.setLinesVisible(true);
    
    TableViewerColumn confidenceViewerColumn = new TableViewerColumn(entityList, SWT.NONE);
    TableColumn confidenceColumn = confidenceViewerColumn.getColumn();
    confidenceColumn.setText("%");
    confidenceColumn.setWidth(40);
    
    TableViewerColumn entityViewerColumn = new TableViewerColumn(entityList, SWT.NONE);
    TableColumn entityColumn = entityViewerColumn.getColumn();
    entityColumn.setText("Entity");
    entityColumn.setWidth(135);
    
    TableViewerColumn confirmedViewerColumn = new TableViewerColumn(entityList, SWT.NONE);
    TableColumn confirmedColumn = confirmedViewerColumn.getColumn();
    confirmedColumn.setText("Confirmed");
    confirmedColumn.setWidth(60);
    
    entityList.setLabelProvider(new EntityLabelProvider());
    entityList.setContentProvider(new EntityContentProvider(new NameFinderJob(), entityList));
    getSite().setSelectionProvider(entityList);
    
    entityList.setComparator(new EntityComperator());
    
    entityList.setInput(editor.getDocument());
    
    getSite().setSelectionProvider(entityList);
    
    entityList.addSelectionChangedListener(new ISelectionChangedListener() {
		
		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			// if confirmed, send selection event for FS
			// else, do selectAndReveal
			StructuredSelection selection = (StructuredSelection) event.getSelection();
			
			if (!selection.isEmpty()) {
				Entity entity = (Entity) selection.getFirstElement();
				
				if (entity.isConfirmed()) {
				  // How to find corresponding annotation ?!
				  // Annotation could be linked to entity
				  
				  // A selection provider needs to communicate the selection to other views,
				  // the selection must contain a certrain object ... AnnotationTreeNode ?
				}
				else  {
					if (editor instanceof AnnotationEditor) {
						((AnnotationEditor) editor).selectAndReveal(entity.getBeginIndex(),
								entity.getEndIndex() - entity.getBeginIndex());
					}
				}
			}
		}
	});
  }

  public void selectionChanged(IWorkbenchPart part, ISelection selection) {
 
    boolean isForeignSelection = !(part instanceof NameFinderView && 
        ((NameFinderView) part).getCurrentPage() == this);
    
    if (isForeignSelection) {
      if (selection instanceof StructuredSelection) {
        AnnotationSelection annotations = new AnnotationSelection((StructuredSelection) selection);

        if (!annotations.isEmpty()) {
          AnnotationFS firstAnnotation = annotations.getFirst();
          
          // If that annotation exist, then match it.
          // Bug: Need to check the type also ...
          Entity entity = new Entity(firstAnnotation.getBegin(), firstAnnotation.getEnd(),
              firstAnnotation.getCoveredText(), null, true);
          
          ISelection tableSelection = new StructuredSelection(entity);
          entityList.setSelection(tableSelection, true);
        }
      }
    }
  }

  public Control getControl() {
    return entityList.getControl();
  }

  public void setFocus() {
    entityList.getControl().setFocus();
  }

  public void makeContributions(IMenuManager menuManager,
      IToolBarManager toolBarManager, IStatusLineManager statusLineManager) {
    super.makeContributions(menuManager, toolBarManager, statusLineManager);

    // TODO: Action is missing keyboard shortcut
    // TODO: We need a confirm icon

    BaseSelectionListenerAction confirmAction = new ConfirmAnnotationAction(entityList, editor.getDocument(), nameTypeName);
    
    getSite().getSelectionProvider().addSelectionChangedListener(confirmAction); // need also to unregister!!!!
    
    toolBarManager.add(confirmAction);
  }
}
