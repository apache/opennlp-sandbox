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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;

public class EntityContentProvider implements IStructuredContentProvider {

  private NameFinderJob nameFinder;
  
  private TableViewer entityList;
  
  private ICasDocument input;
  
  EntityContentProvider(NameFinderJob nameFinder, TableViewer entityList) {
    this.nameFinder = nameFinder;
    this.entityList = entityList;
    
    nameFinder.addJobChangeListener(new JobChangeAdapter() {
      public void done(final IJobChangeEvent event) {
        
        Display.getDefault().asyncExec(new Runnable() {
          
          @Override
          public void run() {
            IStatus status = event.getResult();
            
            if (status.getSeverity() == IStatus.OK) {
              List<Entity> potentialEntities = EntityContentProvider.this.nameFinder.getNames();
              EntityContentProvider.this.entityList.add(potentialEntities.toArray());
            }
          }
        });
      };
    });
  }
  
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    
    input = (ICasDocument) newInput;
    
    runNameFinder();
  }
  
  void runNameFinder() {
    IPreferenceStore store = OpenNLPPlugin.getDefault().getPreferenceStore();
    String sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);
    String nameTypeName = store.getString(OpenNLPPreferenceConstants.NAME_TYPE);
    
    CAS cas = input.getCAS();
    
    // just get it from preference store?!
    // Should have a good way to display an error when the type is incorrect ...
    Type sentenceType = cas.getTypeSystem().getType(sentenceTypeName); 
    
    String text = cas.getDocumentText();

    if (text != null) {

      // get list of sentence annotations
      // get list of token annotations

      List<Span> sentences = new ArrayList<Span>();
      List<Span> tokens = new ArrayList<Span>();
      // get a list on name annotations, they will force the
      // name finder to detect them ... and maybe maintain a negative list

      FSIndex<AnnotationFS> sentenceAnnotations = cas
          .getAnnotationIndex(sentenceType);

      for (Iterator<AnnotationFS> sentenceIterator = sentenceAnnotations
          .iterator(); sentenceIterator.hasNext();) {

        AnnotationFS sentenceAnnotation = (AnnotationFS) sentenceIterator
            .next();

        sentences.add(new Span(sentenceAnnotation.getBegin(), sentenceAnnotation.getEnd()));

        String sentText = sentenceAnnotation.getCoveredText();

        Span tokenSpans[] = SimpleTokenizer.INSTANCE.tokenizePos(sentText);

        int sentenceOffset = sentenceAnnotation.getBegin();

        for (Span token : tokenSpans) {
          tokens.add(new Span(sentenceOffset + token.getStart(),
              sentenceOffset + token.getEnd()));
        }
      }

      List<Span> nameSpans = new ArrayList<Span>();

      Type nameType = cas.getTypeSystem().getType(nameTypeName); 

      FSIndex<AnnotationFS> nameAnnotations = cas
          .getAnnotationIndex(nameType);

      for (Iterator<AnnotationFS> nameIterator = nameAnnotations
          .iterator(); nameIterator.hasNext();) {

        AnnotationFS nameAnnotation = (AnnotationFS) nameIterator.next();

        nameSpans.add(new Span(nameAnnotation.getBegin(), nameAnnotation.getEnd()));
      }
      
      // This will cause issues when it is done while it is running!
      nameFinder.setText(text);
      nameFinder.setSentences(sentences.toArray(new Span[sentences.size()]));
      nameFinder.setTokens(tokens.toArray(new Span[tokens.size()]));
      nameFinder.setVerifiedNames(nameSpans.toArray(new Span[nameSpans.size()]));
      
      nameFinder.schedule();
    }
  }
  
  public Object[] getElements(Object inputElement) {
    return new Entity[] {};
  }
  
  public void dispose() {
  }
}
