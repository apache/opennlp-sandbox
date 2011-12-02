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

package org.apache.opennlp.caseditor.sentdetect;

import org.apache.opennlp.caseditor.ConfirmAnnotationAction;
import org.apache.opennlp.caseditor.OpenPreferenceDialog;
import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.apache.uima.caseditor.CasEditorPlugin;
import org.apache.uima.caseditor.Images;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
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
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.part.Page;
import org.eclipse.ui.part.PageBook;

public class SentenceDetectorViewPage extends Page {
  
  private static final String QUICK_ANNOTATE_ACTION_ID = "QuickAnnotate";

  private ICasEditor editor;
  
  private PageBook book;
    
  private Text messageText;
  
  private TableViewer sentenceList; 
  
  private SentenceContentProvider contentProvider;

  private SentenceDetectorView sentenceDetectorView;
  
  public SentenceDetectorViewPage(SentenceDetectorView sentenceDetectorView, ICasEditor editor) {
    this.sentenceDetectorView = sentenceDetectorView;
    this.editor = editor;
  }

  @Override
  public void createControl(Composite parent) {
    
    book = new PageBook(parent, SWT.NONE);
    
    messageText = new Text(book, SWT.WRAP | SWT.READ_ONLY);
    messageText.setText("Loading tokenizer model ...");
    
    sentenceList = new TableViewer(book, SWT.NONE);
    
    Table entityTable = sentenceList.getTable();
    entityTable.setHeaderVisible(true);
    entityTable.setLinesVisible(true);
    
    TableViewerColumn confidenceViewerColumn = new TableViewerColumn(sentenceList, SWT.NONE);
    TableColumn confidenceColumn = confidenceViewerColumn.getColumn();
    confidenceColumn.setText("%");
    confidenceColumn.setWidth(40);
    
    TableViewerColumn entityViewerColumn = new TableViewerColumn(sentenceList, SWT.NONE);
    TableColumn entityColumn = entityViewerColumn.getColumn();
    entityColumn.setText("Sentence");
    entityColumn.setWidth(135);
    
    // TODO: Label provider needs support to display being and end of long texts ...
    //       text in-between can be replaced by three dots.
    sentenceList.setLabelProvider(new SentenceLabelProvider());
    
    SentenceDetectorJob sentenceDetector = new SentenceDetectorJob();
    
    contentProvider = new SentenceContentProvider(this, (AnnotationEditor) editor,
        sentenceDetector, sentenceList);
    
    sentenceList.setContentProvider(contentProvider);
    getSite().setSelectionProvider(sentenceList);
    sentenceList.setInput(editor.getDocument());

    sentenceList.addSelectionChangedListener(new ISelectionChangedListener() {

      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        // if confirmed, send selection event for FS
        // else, do selectAndReveal
        StructuredSelection selection = (StructuredSelection) event
            .getSelection();

        if (!selection.isEmpty()) {
          PotentialAnnotation entity = (PotentialAnnotation) selection.getFirstElement();

          if (editor instanceof AnnotationEditor) {
            ((AnnotationEditor) editor).selectAndReveal(
                entity.getBeginIndex(),
                entity.getEndIndex() - entity.getBeginIndex());
          }
        }
      }
    });
    
    book.showPage(messageText);
  }

  void setMessage(String message) {
    
    if (message != null) {
      messageText.setText(message);
      book.showPage(messageText);
    }
    else {
      messageText.setText("");
      book.showPage(sentenceList.getControl());
    }
  }
  
  @Override
  public Control getControl() {
    return book;
  }

  @Override
  public void setFocus() {
    getControl().setFocus();
  }
  
  @Override
  public void setActionBars(IActionBars actionBars) {
    super.setActionBars(actionBars);
    
    IToolBarManager toolBarManager = actionBars.getToolBarManager();
    
    BaseSelectionListenerAction confirmAction =
        new ConfirmAnnotationAction(sentenceList, editor);
    confirmAction.setActionDefinitionId(QUICK_ANNOTATE_ACTION_ID);
    actionBars.setGlobalActionHandler(QUICK_ANNOTATE_ACTION_ID, confirmAction);
    getSite().getSelectionProvider().addSelectionChangedListener(confirmAction); // need also to unregister!!!!
    toolBarManager.add(confirmAction);
    
    IAction action = new OpenPreferenceDialog(getSite().getShell(), editor);
    
    action.setImageDescriptor(CasEditorPlugin
        .getTaeImageDescriptor(Images.MODEL_PROCESSOR_FOLDER));
    
    toolBarManager.add(action);
    
    // TODO: Add an action which can move editor selection to the right based on EOS chars like
    //       sentence detector would do.
    
    // TODO: Write the same action which can move editor selection to the left based on EOS chars
    //       like the sentence detector would do.
    
    // TODO: Confirm action should use selection bounds in the editor!
    
    // Note: The same mechanism could be used in the name finder view, to change token bounds of an annotation!
  }
  
  boolean isActive() {
    IWorkbenchPart activePart = getSite().getPage().getActivePart();
    
    return sentenceDetectorView == activePart;
  }
}
