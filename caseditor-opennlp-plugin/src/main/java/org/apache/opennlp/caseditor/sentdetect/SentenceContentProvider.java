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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.AbstractCasChangeTrigger;
import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.apache.opennlp.caseditor.namefinder.EntityContentProvider;
import org.apache.opennlp.caseditor.util.UIMAUtil;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.AnnotationEditor;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasDocumentListener;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;

public class SentenceContentProvider implements IStructuredContentProvider {

  private class CasChangeSDTrigger extends AbstractCasChangeTrigger {
    @Override
    protected void trigger() {
      triggerSentenceDetector();
    }
  }
  
  /**
   * Listeners which triggers a run of the name finder when a related preferences changed.
   */
  private class PreferenceChangeTrigger implements IPropertyChangeListener{

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      // Filter all changes of preferences which do not belong to this plugin
      if (event.getProperty().startsWith(OpenNLPPlugin.ID)) {
        triggerSentenceDetector();
      }
    }
  }
  
  private SentenceDetectorViewPage sentenceDetectorView;
  
  private AnnotationEditor editor;
  
  private ICasDocumentListener casChangedTrigger;
  private PreferenceChangeTrigger preferenceChangeTrigger = new PreferenceChangeTrigger();
  
  private SentenceDetectorJob sentenceDetector;
  
  private TableViewer sentenceList;

  public SentenceContentProvider(SentenceDetectorViewPage sentenceDetectorView, AnnotationEditor editor,
      SentenceDetectorJob sentenceDetector, TableViewer sentenceList) {
    this.sentenceDetectorView = sentenceDetectorView;
    this.editor = editor;
    this.sentenceDetector = sentenceDetector;
    this.sentenceList = sentenceList;
    
    sentenceDetector.addJobChangeListener(new JobChangeAdapter() {
      public void done(final IJobChangeEvent event) {
        Display.getDefault().asyncExec(new Runnable() {

          @Override
          public void run() {
            if (event.getResult().isOK()) {
              
              SentenceContentProvider.this.sentenceDetectorView.setMessage(null);
              
              List<PotentialAnnotation> confirmedSentences = new ArrayList<PotentialAnnotation>();
              // TODO: Create a list of existing sentence annotations.
              
              // get sentence annotation index ...
              CAS cas = SentenceContentProvider.this.editor.getDocument().getCAS();
              
              IPreferenceStore store = SentenceContentProvider.this.editor.
                  getCasDocumentProvider().getTypeSystemPreferenceStore(
                  SentenceContentProvider.this.editor.getEditorInput());
              
              String sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);;
              Type sentenceType = cas.getTypeSystem().getType(sentenceTypeName);
              
              for (Iterator<AnnotationFS> it = cas.getAnnotationIndex(sentenceType).iterator();
                  it.hasNext(); ) {
                AnnotationFS sentenceAnnotation = it.next();
                confirmedSentences.add(new PotentialAnnotation(sentenceAnnotation.getBegin(),
                    sentenceAnnotation.getEnd(), sentenceAnnotation.getCoveredText(), 1d, sentenceTypeName));
              }
             
              
              PotentialAnnotation sentences[] = SentenceContentProvider.this.
                  sentenceDetector.getDetectedSentences();
              
              // TODO:
              // Remove all detected sentences from the last run which are not detected anymore
              Table sentenceTable = SentenceContentProvider.this.sentenceList.getTable();
              
              int selectionIndex = sentenceTable.getSelectionIndex();
              
              SentenceContentProvider.this.sentenceList.refresh();
              
              // TODO: Update sentence if it already exist
              
              // Add a new potential sentence
              // Only add if it is not a confirmed sentence yet!
              // for each annotation, search confirmed sentence array above ...
              for (PotentialAnnotation sentence : sentences) {
                if (EntityContentProvider.searchEntity(confirmedSentences,
                    sentence.getBeginIndex(), sentence.getEndIndex(),
                    sentence.getType()) == null) {
                  SentenceContentProvider.this.sentenceList.add(sentence);
                }
              }
              
              // TODO: Try to reuse selection computation code
              
              // is sentence detector view active ?!
              if (SentenceContentProvider.this.sentenceDetectorView.isActive()) {
                int newSelectionIndex = -1;
                
                if (sentenceTable.getItemCount() > 0) {
                  if (sentenceTable.getSelectionIndex() == -1) {
                    newSelectionIndex = 0;
                  }
                  
                  if (selectionIndex < sentenceTable.getItemCount()) {
                    newSelectionIndex = selectionIndex;
                  }
                  else if (selectionIndex >= sentenceTable.getItemCount()) {
                    newSelectionIndex = sentenceTable.getItemCount() - 1;
                  }
                }
                
                if (newSelectionIndex != -1) {
                  SentenceContentProvider.this.sentenceList.setSelection(
                      new StructuredSelection(SentenceContentProvider.this.sentenceList.getElementAt(newSelectionIndex)));
                }
              }
            }
            else {
              SentenceContentProvider.this.sentenceDetectorView.setMessage(event.getResult().getMessage());
            }
          }
        });
      }
    });
    
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    
    store.addPropertyChangeListener(preferenceChangeTrigger);
  }
  
  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    
    // Clears the sentence list
    sentenceList.refresh();
    
    if (oldInput != null) {
      ((ICasDocument) oldInput).removeChangeListener(casChangedTrigger);
    }
    
    if (newInput != null) {
      ICasDocument document = (ICasDocument) newInput;
      casChangedTrigger = new CasChangeSDTrigger();
      
      document.addChangeListener(casChangedTrigger);
      
      triggerSentenceDetector();
    }
  }
  
  void triggerSentenceDetector() {
    
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    
    CAS cas = editor.getDocument().getCAS();
    
    String paragraphTypeNames = store.getString(OpenNLPPreferenceConstants.PARAGRAPH_TYPE);
    Type paragraphTypes[] = UIMAUtil.splitTypes(paragraphTypeNames, ',', cas.getTypeSystem());
    
    List<Span> paragraphSpans = new ArrayList<Span>();
    
    if (paragraphTypes != null) {
      
      for (Iterator<AnnotationFS> sentenceIterator = UIMAUtil.createMultiTypeIterator(cas, paragraphTypes);
            sentenceIterator.hasNext();) {

        AnnotationFS paragraphAnnotation = sentenceIterator.next();
        
        paragraphSpans.add(
            new Span(paragraphAnnotation.getBegin(), paragraphAnnotation.getEnd()));
      }
    }
    else {
      if (paragraphTypeNames.trim().isEmpty()) {
        paragraphSpans.add(new Span(0, cas.getDocumentText().length()));
      }
      else {
        sentenceDetectorView.setMessage("A paragraph type cannot be found in the type system!");
        return;
      }
    }
    
    sentenceDetector.setParagraphs(paragraphSpans);

    
    String sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);
    
    if (sentenceTypeName.isEmpty()) {
      sentenceDetectorView.setMessage("Sentence type name is not set!");
      return;
    }
    
    Type sentenceType = cas.getTypeSystem().getType(sentenceTypeName);
    // TODO: Add all existing sentences to the exclusion spans ...
    
    if (sentenceType == null) {
      sentenceDetectorView.setMessage("Type system does not contain sentence type!");
      return;
    }
    
    sentenceDetector.setSentenceType(sentenceType.getName());
    
    String exclusionSpanTypeNames = store.getString(OpenNLPPreferenceConstants.SENT_EXCLUSION_TYPE);
    
    Type exclusionSpanTypes[] = UIMAUtil.splitTypes(exclusionSpanTypeNames, ',', cas.getTypeSystem());

    if (exclusionSpanTypes == null) {
      exclusionSpanTypes = new Type[0];
    }
    
    if (Arrays.binarySearch(exclusionSpanTypes, sentenceType) < 0) {
      exclusionSpanTypes = Arrays.copyOf(exclusionSpanTypes, exclusionSpanTypes.length + 1);
      exclusionSpanTypes[exclusionSpanTypes.length - 1] = sentenceType;
    }
    
    List<Span> exclusionSpans = new ArrayList<Span>();
    
    for (Iterator<AnnotationFS> exclusionAnnIterator = UIMAUtil.createMultiTypeIterator(cas, exclusionSpanTypes);
        exclusionAnnIterator.hasNext();) {
      
      AnnotationFS exclusionAnnotation = exclusionAnnIterator.next();
      exclusionSpans.add(new Span(exclusionAnnotation.getBegin(), exclusionAnnotation.getEnd()));
    }
    
    sentenceDetector.setExclusionSpans(exclusionSpans);
    
    String modelPath = store.getString(OpenNLPPreferenceConstants.SENTENCE_DETECTOR_MODEL_PATH);
    sentenceDetector.setModelPath(modelPath);
    
    sentenceDetector.setText(editor.getDocument().getCAS().getDocumentText());
    
    sentenceDetector.schedule();
  }
  

  @Override
  public Object[] getElements(Object inputElement) {
    return new Object[0];
  }
  
  @Override
  public void dispose() {
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    store.removePropertyChangeListener(preferenceChangeTrigger);
  }
}
