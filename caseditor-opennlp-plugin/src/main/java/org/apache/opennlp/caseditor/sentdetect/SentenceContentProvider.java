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

import org.apache.opennlp.caseditor.namefinder.Entity;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;

public class SentenceContentProvider implements IStructuredContentProvider {

  private ICasDocument document;
  
  private SentenceDetectorJob sentenceDetector;
  
  private TableViewer sentenceList;
  
  public SentenceContentProvider(SentenceDetectorJob sentenceDetector, TableViewer sentenceList) {
    this.sentenceDetector = sentenceDetector;
    this.sentenceList = sentenceList;
    
    sentenceDetector.addJobChangeListener(new JobChangeAdapter() {
      public void done(final IJobChangeEvent event) {
        Display.getDefault().asyncExec(new Runnable() {
          
          @Override
          public void run() {
            IStatus status = event.getResult();
            
            if (status.getSeverity() == IStatus.OK) {
              
              Entity sentences[] = SentenceContentProvider.this.
                  sentenceDetector.getDetectedSentences();
              
              SentenceContentProvider.this.sentenceList.refresh();
              SentenceContentProvider.this.sentenceList.add(sentences);
            }
          }
        });
      }
    });
    
  }
  
  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    
    if (oldInput != null) {
      // Remove listeners ...
    }
    
    if (newInput != null) {
      document = (ICasDocument) newInput;
    }
  }
  
  void triggerSentenceDetector() {

    // Add paragraph support ...
    
    sentenceDetector.setText(document.getCAS().getDocumentText());
    sentenceDetector.schedule();
  }
  

  @Override
  public Object[] getElements(Object inputElement) {
    return new Object[0];
  }
  
  @Override
  public void dispose() {
  }
}
