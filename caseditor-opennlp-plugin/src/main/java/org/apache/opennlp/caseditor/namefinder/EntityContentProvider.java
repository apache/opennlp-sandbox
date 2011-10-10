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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.caseditor.editor.AnnotationEditor;
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
  
  private static boolean contains(String array[], String element) {
    
    for (String arrayElement : array) {
      if (element.equals(arrayElement))
        return true;
    }
    
    return false;
  }
  
  class ConfirmedEntityListener implements ICasDocumentListener {
    
    
    @Override
    public void added(FeatureStructure fs) {
      
      if (fs instanceof AnnotationFS && contains(nameTypeNames, fs.getType().getName())) {
        // TODO: Check that type matches ...
        AnnotationFS annotation = (AnnotationFS) fs;
        
        List<Entity> entityList = searchEntities(EntityContentProvider.this.candidateEntities,
            annotation.getBegin(), annotation.getEnd());
        
        // Remove all entities from the view and candidate list
        // TODO: Refactor this code branch ...
        //       Now it only needs to remove all intersecting entites from the
        //       candidate list and add the entity itself to the confirmed list
        if (!entityList.isEmpty()) {
          Entity entity = entityList.get(0);
          entity.setBeginIndex(annotation.getBegin());
          entity.setEndIndex(annotation.getEnd());
          entity.setEntityText(annotation.getCoveredText());
          entity.setConfirmed(true);
          entity.setLinkedAnnotation(annotation);
          entity.setConfidence(null);
          
          EntityContentProvider.this.entityListViewer.remove(entity);
          EntityContentProvider.this.candidateEntities.remove(entity);
          
          EntityContentProvider.this.confirmedEntities.add(entity);

          // Delete all other entities which match
          for (int i = 1; i < entityList.size(); i++) {
            Entity removeEntity = entityList.get(i);
            
            if (!removeEntity.isConfirmed()) {
              EntityContentProvider.this.entityListViewer.remove(removeEntity);
              EntityContentProvider.this.candidateEntities.remove(removeEntity);
            }
          }
        }
        else {
          Entity newEntity = new Entity(annotation.getBegin(), annotation.getEnd(),
              annotation.getCoveredText(), null, true, annotation.getType().getName());
          
          EntityContentProvider.this.confirmedEntities.add(newEntity);
        }
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
      
      if (fs instanceof AnnotationFS && contains(nameTypeNames, fs.getType().getName())) {
        AnnotationFS annotation = (AnnotationFS) fs;
        
        Entity confirmedEntity = searchEntity(EntityContentProvider.this.confirmedEntities,
            annotation.getBegin(), annotation.getEnd(), annotation.getType().getName());
        
        if (confirmedEntity != null) {
          EntityContentProvider.this.confirmedEntities.remove(confirmedEntity);
        }
      }
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
  
  private TableViewer entityListViewer;
  
  private ICasDocument input;
  
  private AnnotationEditor editor;
  
  // contains all existing entity annotations and is synchronized!
  // needed by name finder to calculate updates ... 
  private List<Entity> candidateEntities = new ArrayList<Entity>();
  private List<Entity> confirmedEntities = new ArrayList<Entity>();
  
  private String nameTypeNames[];
  
  EntityContentProvider(AnnotationEditor editor, NameFinderJob nameFinder, TableViewer entityList) {
    this.nameFinder = nameFinder;
    this.entityListViewer = entityList;
    this.editor = editor;
    
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    nameTypeNames = store.getString(OpenNLPPreferenceConstants.NAME_TYPE).split(",");
    
    for (int i = 0; i < nameTypeNames.length; i++) {
      nameTypeNames[i] = nameTypeNames[i].trim();
    }
    
    nameFinder.addJobChangeListener(new JobChangeAdapter() {
      public void done(final IJobChangeEvent event) {
        
        Display.getDefault().asyncExec(new Runnable() {
          
          @Override
          public void run() {
            IStatus status = event.getResult();
            
            if (status.getSeverity() == IStatus.OK) {
              
              List<Entity> detectedEntities = EntityContentProvider.this.nameFinder.getNames();
              
              // Remove all detected entities from the last run which are not detected anymore
              for (Iterator<Entity> it = candidateEntities.iterator(); it.hasNext();) {
                Entity entity = it.next();
                if (searchEntity(detectedEntities, entity.getBeginIndex(),
                    entity.getEndIndex(), entity.getType()) == null)  {
                  
                  // TODO: Create an array of entities that should be removed, much faster ...
                  EntityContentProvider.this.entityListViewer.remove(entity);
                  
                  // Can safely be removed, since it can only be an un-confirmed entity
                  it.remove();
                }
              }
              
              // Update if entity already exist, or add it
              for (Entity detectedEntity : detectedEntities) {
                
                // Bug: 
                // There can be multiple entities in this span!
                // In this case we want to keep the first, update it, and discard the others!
                
                // Case: One entity spanning two tokens replaces 
                
                Entity entity = searchEntity(candidateEntities, detectedEntity.getBeginIndex(),
                    detectedEntity.getEndIndex(), detectedEntity.getType());
                
                // A confirmed entity already exists, update its confidence score
                if (entity != null) {
                  if (entity.isConfirmed()) {
                    entity.setConfidence(detectedEntity.getConfidence());
                    EntityContentProvider.this.entityListViewer.refresh(entity);
                    continue;
                  }
                  else {
                    entity.setBeginIndex(detectedEntity.getBeginIndex());
                    entity.setEndIndex(detectedEntity.getEndIndex());
                    entity.setEntityText(detectedEntity.getEntityText());
                    entity.setConfidence(detectedEntity.getConfidence());
                    
                    EntityContentProvider.this.entityListViewer.refresh(entity);
                  }
                }
                else {
                  // Only add if it is not a confirmed entity!
                  if (searchEntity(confirmedEntities, detectedEntity.getBeginIndex(),
                    detectedEntity.getEndIndex(), detectedEntity.getType()) == null) {
                    EntityContentProvider.this.entityListViewer.add(detectedEntity);
                    candidateEntities.add(detectedEntity);
                  }
                }
              }
            }
          }
        });
      };
    });
  }
  
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {

    // Problem: "The viewer should not be updated during this call, as it might be in 
   //           the process of being disposed." (Javadoc)
    // Does it mean that the name finder listener must check if the viewer is still alive?
    
    if (oldInput != null) {
      ICasDocument oldDocument = (ICasDocument) oldInput;
      oldDocument.removeChangeListener(casChangeListener);
      oldDocument.removeChangeListener(nameFinderTrigger);      
    }
    
    if (newInput != null) {
      input = (ICasDocument) newInput;
      
      // Note: Name Finder might run to often ... 
      input.addChangeListener(casChangeListener);
      input.addChangeListener(nameFinderTrigger);
      
      // Create initial list of confirmed entities ...
      
      for (String nameTypeName : nameTypeNames) {
        Type nameType = input.getCAS().getTypeSystem().getType(nameTypeName); 
        
        // TODO: Do error handling!
        if (nameType == null)
          return;
        
        FSIndex<AnnotationFS> nameAnnotations = input.getCAS()
            .getAnnotationIndex(nameType);
        
        for (Iterator<AnnotationFS> nameIterator = nameAnnotations
            .iterator(); nameIterator.hasNext();) {
          
          AnnotationFS nameAnnotation = (AnnotationFS) nameIterator.next();
          
          // TODO: Entity must have a type ...
          Entity entity = new Entity(nameAnnotation.getBegin(),
              nameAnnotation.getEnd(), nameAnnotation.getCoveredText(), null, true,
              nameAnnotation.getType().getName());
          entity.setLinkedAnnotation(nameAnnotation);
          confirmedEntities.add(entity); // TODO: This needs to go into a second list!
        }
      }
      
      runNameFinder();
    }
  }
  
  void runNameFinder() {
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    String sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);
    
    // TODO: Add check for sentence type name
    if (sentenceTypeName.isEmpty())
      return;
    
    String additionalSentenceTypes = store.getString(OpenNLPPreferenceConstants.ADDITIONAL_SENTENCE_TYPE);

    // TODO: Add check for additional sentence type names
    
    String modelPathes[] = store.getString(OpenNLPPreferenceConstants.NAME_FINDER_MODEL_PATH).split(",");
    
    for (int i = 0; i < modelPathes.length; i++) {
      modelPathes[i] = modelPathes[i].trim();
      
      // TODO: Add checks for model path names
      if (modelPathes[i].isEmpty())
        return;
    }

    
    CAS cas = input.getCAS();
    
    // just get it from preference store?!
    // Should have a good way to display an error when the type is incorrect ...
    
    String text = cas.getDocumentText();

    if (text != null) {

      List<Span> sentences = new ArrayList<Span>();

      String sentenceTypeNames[] = (sentenceTypeName + "," +  additionalSentenceTypes).split(",");
      
      for (String typeName : sentenceTypeNames) {
        Type sentenceType = cas.getTypeSystem().getType(typeName.trim()); 
      
        // TODO: If type cannot be mapped, it throws a null pointer exception ...
        
        FSIndex<AnnotationFS> sentenceAnnotations = cas
            .getAnnotationIndex(sentenceType);
        
        for (Iterator<AnnotationFS> sentenceIterator = sentenceAnnotations
            .iterator(); sentenceIterator.hasNext();) {
          
          AnnotationFS sentenceAnnotation = (AnnotationFS) sentenceIterator
              .next();
          
          sentences.add(new Span(sentenceAnnotation.getBegin(), sentenceAnnotation.getEnd()));
        }
      }

      // sort sentences list ... ascending
      Collections.sort(sentences);
      
      // iterate again and create tokens ...
      List<Span> tokens = new ArrayList<Span>();
      
      for (Span sentence : sentences) {
          String sentText = sentence.getCoveredText(text).toString();
          
          // TODO: Extract tokens here! Instead of using the simple tokenizer!
          
          Span tokenSpans[] = SimpleTokenizer.INSTANCE.tokenizePos(sentText);

          int sentenceOffset = sentence.getStart();

          for (Span token : tokenSpans) {
            tokens.add(new Span(sentenceOffset + token.getStart(),
                sentenceOffset + token.getEnd()));
          }
      }
      
      List<Span> nameSpans = new ArrayList<Span>();

      for (String nameTypeName : nameTypeNames) {
        
        Type nameType = cas.getTypeSystem().getType(nameTypeName); 
  
        FSIndex<AnnotationFS> nameAnnotations = cas
            .getAnnotationIndex(nameType);
  
        for (Iterator<AnnotationFS> nameIterator = nameAnnotations
            .iterator(); nameIterator.hasNext();) {
  
          AnnotationFS nameAnnotation = (AnnotationFS) nameIterator.next();
  
          nameSpans.add(new Span(nameAnnotation.getBegin(), nameAnnotation.getEnd(),
              nameAnnotation.getType().getName()));
        }
      }
      
      // This will cause issues when it is done while it is running!
      nameFinder.setText(text);
      nameFinder.setSentences(sentences.toArray(new Span[sentences.size()]));
      nameFinder.setTokens(tokens.toArray(new Span[tokens.size()]));
      nameFinder.setVerifiedNames(nameSpans.toArray(new Span[nameSpans.size()]));
      nameFinder.setModelPath(modelPathes, nameTypeNames);
      
      nameFinder.schedule();
    }
  }
  
  public Object[] getElements(Object inputElement) {
    // Note: 
    // Called directly after showing the view, the
    // name finder is triggered to produce names
    // which will be added to the viewer
    return candidateEntities.toArray();
  }
  
  public void dispose() {
  }
  
  static List<Entity> searchEntities(List<Entity> entities, int begin, int end) {
    
    List<Entity> intersectingEntities = new ArrayList<Entity>();
    
    Span testSpan = new Span(begin, end);
    
    for (Entity entity : entities) {
      
      Span entitySpan = new Span(entity.getBeginIndex(),
          entity.getEndIndex());
      
      if (entitySpan.intersects(testSpan)) {
        intersectingEntities.add(entity);
      }
    }
    
    return intersectingEntities;
  }
  
  // Could pass null, means any type
  static Entity searchEntity(List<Entity> entities, int begin, int end, String type) {
    
    Span testSpan = new Span(begin, end);
    
    for (Entity entity : entities) {
      
      Span entitySpan = new Span(entity.getBeginIndex(),
          entity.getEndIndex());
      
      if (entitySpan.intersects(testSpan) && (type == null || type.equals(entity.getType()))) {
        return entity;
      }
    }
    
    return null;
  }
}
