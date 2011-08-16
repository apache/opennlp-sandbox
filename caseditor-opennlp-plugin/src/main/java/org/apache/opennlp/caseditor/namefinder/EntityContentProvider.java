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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasDocumentListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;

// Need its own list (or map), otherwise it is complicated to compute updates ...
// Maybe we should create again, a "View" map of indexes to its annotations?!
public class EntityContentProvider implements IStructuredContentProvider {

  // TODO: Triggering should be more refined, and only happen if
  // Sentences, Tokens, or entities change ..
  class NameFinderTrigger implements ICasDocumentListener {

    @Override
    public void added(FeatureStructure fs) {
      runNameFinder();
    }

    @Override
    public void added(Collection<FeatureStructure> featureStructures) {
      runNameFinder();
    }

    @Override
    public void changed() {
      runNameFinder();
    }

    @Override
    public void removed(FeatureStructure fs) {
      runNameFinder();
    }

    @Override
    public void removed(Collection<FeatureStructure> featureStructures) {
      runNameFinder();
    }

    @Override
    public void updated(FeatureStructure fs) {
      runNameFinder();
    }

    @Override
    public void updated(Collection<FeatureStructure> featureStructures) {
      runNameFinder();
      
    }

    @Override
    public void viewChanged(String oldView, String newView) {
      runNameFinder();
    }
    
  }
  
  // Question, how to determine overlaps between two annotations ?!
  
  class ConfirmedEntityListener implements ICasDocumentListener {
    
    @Override
    public void added(FeatureStructure fs) {
      
      if (fs instanceof AnnotationFS && fs.getType().getName().equals(nameTypeName)) {
        // TODO: Check that type matches ...
        AnnotationFS annotation = (AnnotationFS) fs;
        
        Entity newEntity = new Entity(annotation.getBegin(), annotation.getEnd(),
            annotation.getCoveredText(), null, true);
        
        Entity potentialEntity = searchEntity(EntityContentProvider.this.potentialEntities,
            annotation.getBegin(), annotation.getEnd());
        
        if (potentialEntity != null)
          EntityContentProvider.this.entityList.remove(potentialEntity);
        
        confirmedEntities.add(newEntity);
        EntityContentProvider.this.entityList.add(newEntity);
      }
    }

    @Override
    public void added(Collection<FeatureStructure> featureStructures) {
      for (FeatureStructure fs : featureStructures){
        added(fs);
      }
    }

    @Override
    public void changed() {
      // just refresh ...
    }

    @Override
    public void removed(FeatureStructure fs) {
      
      if (fs instanceof AnnotationFS && fs.getType().getName().equals(nameTypeName)) {
        AnnotationFS annotation = (AnnotationFS) fs;
        
        Entity confirmedEntity = searchEntity(EntityContentProvider.this.confirmedEntities,
            annotation.getBegin(), annotation.getEnd());
        
        if (confirmedEntity != null) {
          EntityContentProvider.this.confirmedEntities.remove(confirmedEntity);
          EntityContentProvider.this.entityList.remove(confirmedEntity);
        }
      }
      
      // TODO: Eventually add it to a black list, so tokens in this
      // area cannot be detected as a name
    }

    @Override
    public void removed(Collection<FeatureStructure> featureStructures) {
      for (FeatureStructure fs : featureStructures) {
        removed(fs);
      }
    }

    @Override
    public void updated(FeatureStructure fs) {
    }

    @Override
    public void updated(Collection<FeatureStructure> featureStructures) {
      
    }

    @Override
    public void viewChanged(String oldView, String newView) {
    }
  }
  
  private NameFinderJob nameFinder;
  
  private NameFinderTrigger nameFinderTrigger = new NameFinderTrigger();
  private ConfirmedEntityListener casChangeListener = new ConfirmedEntityListener();
  
  private TableViewer entityList;
  
  private ICasDocument input;
  
  // contains all existing entity annotations and is synchronized!
  // needed by name finder to calculate updates ... 
  private List<Entity> confirmedEntities = new ArrayList<Entity>();
  private List<Entity> potentialEntities = new ArrayList<Entity>();
  
  private String nameTypeName;
  
  EntityContentProvider(NameFinderJob nameFinder, TableViewer entityList) {
    this.nameFinder = nameFinder;
    this.entityList = entityList;
    
    IPreferenceStore store = OpenNLPPlugin.getDefault().getPreferenceStore();
    nameTypeName = store.getString(OpenNLPPreferenceConstants.NAME_TYPE);
    
    nameFinder.addJobChangeListener(new JobChangeAdapter() {
      public void done(final IJobChangeEvent event) {
        
        Display.getDefault().asyncExec(new Runnable() {
          
          @Override
          public void run() {
            IStatus status = event.getResult();
            
            if (status.getSeverity() == IStatus.OK) {
              
              // 
              List<Entity> newPotentialEntities = EntityContentProvider.this.nameFinder.getNames();
              
              // Remove all potential annotations from list ?! Yes! Note: We should compute a delta here in the future ...
              EntityContentProvider.this.entityList.remove(potentialEntities.toArray());
              // Then add like described below:
              
              for (Entity newPotentialEntity : newPotentialEntities) {
                
                // A confirmed entity already exists, update its confidence score
                Entity confirmedEntity = searchEntity(confirmedEntities, newPotentialEntity.getBeginIndex(), newPotentialEntity.getEndIndex());
                if (confirmedEntity != null) {
                  confirmedEntity.setConfidence(newPotentialEntity.getConfidence());
                  EntityContentProvider.this.entityList.refresh(confirmedEntity);
                  continue;
                }
                
                // potential entity should be added!
                // TODO: that is slow and should be done in a bulk update ...
                EntityContentProvider.this.entityList.add(newPotentialEntity);
              }
              
              // Remember entities for next update
              potentialEntities = newPotentialEntities;
            }
          }
        });
      };
    });
  }
  
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    
    IPreferenceStore store = OpenNLPPlugin.getDefault().getPreferenceStore();
    String nameTypeName = store.getString(OpenNLPPreferenceConstants.NAME_TYPE);
    
    if (input != newInput) {
      
      input = (ICasDocument) newInput;
      
      if (oldInput != null && oldInput != newInput) {
        
        ICasDocument oldDocument = (ICasDocument) oldInput;
        oldDocument.removeChangeListener(casChangeListener);
        oldDocument.removeChangeListener(nameFinderTrigger);
      }
      
      if (newInput != null) {
        // Note: Name Finder might run to often ... 
        input.addChangeListener(casChangeListener);
        input.addChangeListener(nameFinderTrigger);
        
        // Create initial list of confirmed entities ...
        Type nameType = input.getCAS().getTypeSystem().getType(nameTypeName); 
        
        FSIndex<AnnotationFS> nameAnnotations = input.getCAS()
            .getAnnotationIndex(nameType);

        for (Iterator<AnnotationFS> nameIterator = nameAnnotations
            .iterator(); nameIterator.hasNext();) {
          
          AnnotationFS nameAnnotation = (AnnotationFS) nameIterator.next();
          
          confirmedEntities.add(new Entity(nameAnnotation.getBegin(), nameAnnotation.getEnd(), nameAnnotation.getCoveredText(), null, true));
        }
        
        runNameFinder();
      }
    }
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
    // Note: 
    // Called directly after showing the view, the
    // name finder is triggered to produce names
    // which will be added to the viewer
    return confirmedEntities.toArray();
  }
  
  public void dispose() {
  }
  
  static Entity searchEntity(List<Entity> entities, int begin, int end) {
    
    Span testSpan = new Span(begin, end);
    
    for (Entity entity : entities) {
      
      Span entitySpan = new Span(entity.getBeginIndex(),
          entity.getEndIndex());
      
      if (entitySpan.intersects(testSpan)) {
        return entity;
      }
    }
    
    return null;
  }
}
