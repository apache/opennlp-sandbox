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

import java.util.ArrayList;
import java.util.Collection;

import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.BaseSelectionListenerAction;
import org.eclipse.ui.part.Page;

public class TokenizerViewPage extends Page {
  
  private ICasEditor editor;

  private Label workInProgress;
  
  TokenizerViewPage(ICasEditor editor) {
    this.editor = editor;
  }

  @Override
  public void createControl(Composite parent) {
    workInProgress = new Label(parent, SWT.NONE);
    workInProgress.setText("Click on the detect button.");
  }

  // Add action to trigger detection, just add all tokens to CAS
  
  @Override
  public void setActionBars(IActionBars actionBars) {
    super.setActionBars(actionBars);
    
    IToolBarManager toolBarManager = actionBars.getToolBarManager();
    
    BaseSelectionListenerAction detectAction = new BaseSelectionListenerAction("Detect") {
      @Override
      public void run() {
        
        IPreferenceStore prefStore = 
            editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
        
        TokenizerJob tokenizerJob = new TokenizerJob();
        
        tokenizerJob.setModelPath(prefStore.getString(OpenNLPPreferenceConstants.TOKENIZER_MODEL_PATH));
        tokenizerJob.setTokenizerAlgorithm(prefStore.getString(OpenNLPPreferenceConstants.TOKENIZER_ALGORITHM));
        
        tokenizerJob.setText(editor.getDocument().getCAS().getDocumentText());
        
        tokenizerJob.schedule();
        
        try {
          tokenizerJob.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        
        Span tokens[] = tokenizerJob.getTokens();
        
        CAS cas = editor.getDocument().getCAS();
        
        Type tokenType = cas.getTypeSystem().getType(prefStore.getString(OpenNLPPreferenceConstants.TOKEN_TYPE));
        
        Collection<AnnotationFS> tokenAnnotations = new ArrayList<AnnotationFS>(tokens.length);
        
        for (Span token : tokens) {
          tokenAnnotations.add(cas.createAnnotation(tokenType, token.getStart(), token.getEnd()));
        }
        
        editor.getDocument().addFeatureStructures(tokenAnnotations);
      }
    };
    
    toolBarManager.add(detectAction);
  }
  
  @Override
  public Control getControl() {
    return workInProgress;
  }

  @Override
  public void setFocus() {
    workInProgress.setFocus();
  }
}
