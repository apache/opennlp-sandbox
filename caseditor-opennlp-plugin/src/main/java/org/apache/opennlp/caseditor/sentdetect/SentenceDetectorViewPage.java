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

import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.opennlp.caseditor.OpenPreferenceDialog;
import org.apache.opennlp.caseditor.namefinder.ConfirmAnnotationAction;
import org.apache.opennlp.caseditor.namefinder.Entity;
import org.apache.opennlp.caseditor.namefinder.EntityLabelProvider;
import org.apache.uima.caseditor.CasEditorPlugin;
import org.apache.uima.caseditor.Images;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
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
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.part.Page;

public class SentenceDetectorViewPage extends Page {
  
  private static final String QUICK_ANNOTATE_ACTION_ID = "QuickAnnotate";

  private ICasEditor editor;
  
  private TableViewer sentenceList; 
  
  private SentenceContentProvider contentProvider;
  
  private String modelPath;
  private String sentenceTypeName;
  
  public SentenceDetectorViewPage(ICasEditor editor) {
    this.editor = editor;
   
    IPreferenceStore store = OpenNLPPlugin.getDefault().getPreferenceStore();
    modelPath = store.getString(OpenNLPPreferenceConstants.SENTENCE_DETECTOR_MODEL_PATH);
    sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);
  }

  @Override
  public void createControl(Composite parent) {
    sentenceList = new TableViewer(parent, SWT.NONE);
    
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
    sentenceList.setLabelProvider(new EntityLabelProvider());
    
    SentenceDetectorJob sentenceDetector = new SentenceDetectorJob();
    
    sentenceDetector.setModelPath(modelPath);
    
    contentProvider = new SentenceContentProvider(sentenceDetector, sentenceList);
    
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
          Entity entity = (Entity) selection.getFirstElement();

          if (entity.isConfirmed()) {
            // TODO: Send corresponding annotation selection event ...
          } else {
            if (editor instanceof AnnotationEditor) {
              ((AnnotationEditor) editor).selectAndReveal(
                  entity.getBeginIndex(),
                  entity.getEndIndex() - entity.getBeginIndex());
            }
          }
        }
      }
    });
  }

  @Override
  public Control getControl() {
    return sentenceList.getTable();
  }

  @Override
  public void setFocus() {
    sentenceList.getTable().setFocus();
  }
  
  @Override
  public void setActionBars(IActionBars actionBars) {
    super.setActionBars(actionBars);
    
    IToolBarManager toolBarManager = actionBars.getToolBarManager();
    
    BaseSelectionListenerAction detectAction = new BaseSelectionListenerAction("Detect") {
      @Override
      public void run() {
        contentProvider.triggerSentenceDetector();
      }
    };
    
    toolBarManager.add(detectAction);
    
    BaseSelectionListenerAction confirmAction =
        new ConfirmAnnotationAction(sentenceList, editor.getDocument());
    confirmAction.setActionDefinitionId(QUICK_ANNOTATE_ACTION_ID);
    actionBars.setGlobalActionHandler(QUICK_ANNOTATE_ACTION_ID, confirmAction);
    getSite().getSelectionProvider().addSelectionChangedListener(confirmAction); // need also to unregister!!!!
    toolBarManager.add(confirmAction);
    
    IAction action = new OpenPreferenceDialog(getSite().getShell(), editor);
    
    action.setImageDescriptor(CasEditorPlugin
        .getTaeImageDescriptor(Images.MODEL_PROCESSOR_FOLDER));
    
    toolBarManager.add(action);
  }
    
}
