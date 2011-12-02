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

import org.apache.opennlp.caseditor.ConfirmAnnotationAction;
import org.apache.opennlp.caseditor.OpenPreferenceDialog;
import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.apache.opennlp.caseditor.PotentialAnnotationComperator;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.CasEditorPlugin;
import org.apache.uima.caseditor.Images;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.apache.uima.caseditor.editor.ICasEditorInputListener;
import org.apache.uima.caseditor.editor.util.AnnotationSelection;
import org.eclipse.jface.action.IAction;
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
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.part.PageBook;


// TODO: Selected entities should be highlighted in the annotation editor!
//       How can that be done? Should we simply create feature structures, which are not added to the index!?         

// TODO: There should be a way to display error messages in this view, e.g.
//       when no names are detected. -> give an indication what could be wrong!
class NameFinderViewPage extends Page implements ISelectionListener {

  private static final String QUICK_ANNOTATE_ACTION_ID = "QuickAnnotate";
  
  private NameFinderView nameFinderView;
  
  private ICasEditor editor;

  private PageBook book;

  private Text messageText;
  private TableViewer entityList;

  NameFinderViewPage(NameFinderView nameFinderView, ICasEditor editor, ICasDocument document) {
    this.nameFinderView = nameFinderView;
    this.editor = editor;
    
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    NameFinderPreferenceInitializer.initializeDefaultPreferences(store);
  }

  public void createControl(Composite parent) {
    
    book = new PageBook(parent, SWT.NONE);
    
    messageText = new Text(book, SWT.WRAP | SWT.READ_ONLY);
    messageText.setText("Loading name finder models ...");
    
    entityList = new TableViewer(book, SWT.NONE);
    
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
    
    TableViewerColumn typeViewerColumn = new TableViewerColumn(entityList, SWT.NONE);
    TableColumn typeColumn = typeViewerColumn.getColumn();
    typeColumn.setText("Type");
    typeColumn.setWidth(40);
    
    entityList.setLabelProvider(new PotentialEntityAnnotationLabelProvider());
    entityList.setContentProvider(new EntityContentProvider(this, (AnnotationEditor) editor, entityList));
    getSite().setSelectionProvider(entityList);
    
    entityList.setComparator(new PotentialAnnotationComperator());
    
    entityList.setInput(editor.getDocument());
    
    getSite().setSelectionProvider(entityList);
    
    entityList.addSelectionChangedListener(new ISelectionChangedListener() {
		
		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			StructuredSelection selection = (StructuredSelection) event.getSelection();
			
			// There are two types of entities, confirmed and un-confirmed.
			// Confirmed entities are linked with the according annotation and
			// are selected through the entity lists selection provider.
			
			// Unconfirmed entities are not selected, but the span they are covering
			// is highlighted and revealed in the Annotation Editor.
			
			if (!selection.isEmpty()) {
			  PotentialAnnotation entity = (PotentialAnnotation) selection.getFirstElement();
				
			  if (editor instanceof AnnotationEditor) {
			    ((AnnotationEditor) editor).selectAndReveal(entity.getBeginIndex(),
			        entity.getEndIndex() - entity.getBeginIndex());
			  }
			}
		}
	});
    
    // Display the messageLabel after start up
    book.showPage(messageText);
    
    getSite().getPage().addSelectionListener(this);
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
          PotentialAnnotation entity = new PotentialAnnotation(firstAnnotation.getBegin(), firstAnnotation.getEnd(),
              firstAnnotation.getCoveredText(), null, firstAnnotation.getType().getName());
          
          ISelection tableSelection = new StructuredSelection(entity);
          entityList.setSelection(tableSelection, true);
        }
      }
    }
  }

  public Control getControl() {
    return book;
  }

  public void setFocus() {
    getControl().setFocus();
  }

  void setMessage(String message) {
    
    if (message != null) {
      messageText.setText(message);
      book.showPage(messageText);
    }
    else {
      messageText.setText("");
      book.showPage(entityList.getControl());
    }
  }
  
  @Override
  public void setActionBars(IActionBars actionBars) {
    super.setActionBars(actionBars);
    
    // TODO: We need a confirm icon
    
    IToolBarManager toolBarManager = actionBars.getToolBarManager();
    
    BaseSelectionListenerAction confirmAction = new ConfirmAnnotationAction(entityList, editor);
    confirmAction.setActionDefinitionId(QUICK_ANNOTATE_ACTION_ID);
    actionBars.setGlobalActionHandler(QUICK_ANNOTATE_ACTION_ID, confirmAction);
    getSite().getSelectionProvider().addSelectionChangedListener(confirmAction); // need also to unregister!!!!
    
    toolBarManager.add(confirmAction);
    
    
    // TODO: Create a preference action
    // Open a dialog like already done for the annotation styles
    // Provide the preference store to this dialog
    // Provide a type system to this preference store
    
    IAction action = new OpenPreferenceDialog(getSite().getShell(), editor);
    
    action.setImageDescriptor(CasEditorPlugin
        .getTaeImageDescriptor(Images.MODEL_PROCESSOR_FOLDER));
    
    toolBarManager.add(action);
    
    // TODO:
    // How to make an action which changes the selection in the editor? Must be possible to make
    // it large smaller on both sides, like the actions we already have, but not for an annotation!
    // Key short cuts
    // alt + left/right key for right annotation side
    // ctrl + left/right key for left annotation side
    
  }
  
  
  boolean isActive() {
    IWorkbenchPart activePart = getSite().getPage().getActivePart();
    
    return nameFinderView == activePart;
  }
}
