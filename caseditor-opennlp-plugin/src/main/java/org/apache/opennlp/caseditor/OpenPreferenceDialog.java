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

import org.apache.opennlp.caseditor.namefinder.NameFinderPreferencePage;
import org.apache.opennlp.caseditor.sentdetect.SentenceDetectorPreferencePage;
import org.apache.opennlp.caseditor.tokenize.TokenizerPreferencePage;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferencePage;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.dialogs.PropertyDialog;

public class OpenPreferenceDialog extends Action {
  
  private Shell shell;
  private AnnotationEditor editor;

  public OpenPreferenceDialog(Shell shell, ICasEditor editor) {
    this.shell = shell;
    this.editor = (AnnotationEditor) editor;
  }
  
  @Override
  public void run() {
    super.run();
    
    PreferenceManager mgr = new PreferenceManager();
    
    IPreferencePage opennlpPage = new OpenNLPPreferencePage(
        editor.getDocument().getCAS().getTypeSystem());
    opennlpPage.setTitle("General");
    mgr.addToRoot(new PreferenceNode("1", opennlpPage));
    
    IPreferencePage sentenceDetectorPage = new SentenceDetectorPreferencePage(
        editor.getDocument().getCAS().getTypeSystem());
    sentenceDetectorPage.setTitle("Sentence Detector");
    mgr.addToRoot(new PreferenceNode("1", sentenceDetectorPage));
    
    IPreferencePage tokenizerPage = new TokenizerPreferencePage();
    tokenizerPage.setTitle("Tokenizer");
    mgr.addToRoot(new PreferenceNode("1", tokenizerPage));
    
    IPreferencePage nameFinderPage = new NameFinderPreferencePage(
        editor.getDocument().getCAS().getTypeSystem());
    nameFinderPage.setTitle("Name Finder");
    mgr.addToRoot(new PreferenceNode("1", nameFinderPage));
    
    PropertyDialog dialog = new PropertyDialog(shell, mgr, null);
    dialog.setPreferenceStore(((AnnotationEditor) editor).
        getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput()));
    dialog.create();
    dialog.setMessage(nameFinderPage.getTitle());
    dialog.open();
    
    editor.getCasDocumentProvider().saveTypeSystemPreferenceStore(editor.getEditorInput());
  }
}