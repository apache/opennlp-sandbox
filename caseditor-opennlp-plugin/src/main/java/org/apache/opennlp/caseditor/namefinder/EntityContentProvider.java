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

import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.AbstractCasChangeTrigger;
import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.OpenNLPPreferenceConstants;
import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.apache.opennlp.caseditor.util.ContainingConstraint;
import org.apache.opennlp.caseditor.util.UIMAUtil;
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
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Display;

/**
 * The EntityContentProvider is responsible to trigger the detection of entities
 * and turn these into potential entity annotations.
 */
// Need its own list (or map), otherwise it is complicated to compute updates ...
// Maybe we should create again, a "View" map of indexes to its annotations?!
public class EntityContentProvider implements IStructuredContentProvider {

  /**
   * Listener which triggers a run of the name finder if something in the CAS changed.
   * <p>
   * TODO: Listener should only trigger a run if something changed which might change the results
   * of the name finder run.
   */
  private class CasChangeNameFinderTrigger extends AbstractCasChangeTrigger {

    @Override
    protected void trigger() {
      runNameFinder();
    }
  }
  
  /**
   * Listeners which triggers a run of the name finder when a related preferences changed.
   */
  private class PreferenceChangeNameFinderTrigger implements IPropertyChangeListener{

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      // Filter all changes of preferences which do not belong to this plugin
      if (event.getProperty().startsWith(OpenNLPPlugin.ID)) {
        runNameFinder();
      }
    }
  }
  
  class ConfirmedEntityListener implements ICasDocumentListener {
    
    
    @Override
    public void added(FeatureStructure fs) {
      
      if (fs instanceof AnnotationFS && contains(nameTypeNames, fs.getType().getName())) {
        // TODO: Check that type matches ...
        AnnotationFS annotation = (AnnotationFS) fs;
        
        List<PotentialAnnotation> entityList = searchEntities(EntityContentProvider.this.candidateEntities,
            annotation.getBegin(), annotation.getEnd());
        
        // Remove all entities from the view and candidate list
        // TODO: Refactor this code branch ...
        //       Now it only needs to remove all intersecting entities from the
        //       candidate list and add the entity itself to the confirmed list
        
        int selectionIndex = EntityContentProvider.this.entityListViewer.
            getTable().getSelectionIndex();
        
        if (!entityList.isEmpty()) {
          PotentialAnnotation entity = entityList.get(0);
          entity.setBeginIndex(annotation.getBegin());
          entity.setEndIndex(annotation.getEnd());
          entity.setEntityText(annotation.getCoveredText());
          entity.setConfidence(null);
          
          entityListViewer.remove(entity);
          candidateEntities.remove(entity);
          
          confirmedEntities.add(entity);

          // Delete all other entities which match
          for (int i = 1; i < entityList.size(); i++) {
            PotentialAnnotation removeEntity = entityList.get(i);
            
            entityListViewer.remove(removeEntity);
            candidateEntities.remove(removeEntity);
          }
          
          if (nameFinderView.isActive()) {
            if (selectionIndex != -1) {
              if (selectionIndex < entityListViewer.
                  getTable().getItemCount()) {
                      entityListViewer.setSelection(
                          new StructuredSelection(entityListViewer.getElementAt(selectionIndex)));
              }
              else {
                if (entityListViewer.getTable().getItemCount() > 0) {
                  entityListViewer.setSelection(new StructuredSelection(
                          entityListViewer.getElementAt(
                          entityListViewer.getTable().getItemCount() - 1)));
                }
              }
            }
            else {
              if (entityListViewer.getTable().getItemCount() > 0) {
                entityListViewer.setSelection(new StructuredSelection(entityListViewer.getElementAt(0)));
              }
            }
          }
        }
        else {
          PotentialAnnotation newEntity = new PotentialAnnotation(annotation.getBegin(), annotation.getEnd(),
              annotation.getCoveredText(), null, annotation.getType().getName());
          
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
        
        PotentialAnnotation confirmedEntity = searchEntity(EntityContentProvider.this.confirmedEntities,
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
  
  private class NameFinderJobListener extends JobChangeAdapter {
    public void done(final IJobChangeEvent event) {
      
      Display.getDefault().asyncExec(new Runnable() {
        
        @Override
        public void run() {
          
          // TODO: Check if view is still available, that might be called after view is disposed.
          
          IStatus status = event.getResult();
          
          if (status.isOK()) {
            EntityContentProvider.this.nameFinderView.setMessage(null);
            
            List<PotentialAnnotation> detectedEntities = EntityContentProvider.this.nameFinder.getNames();
            
            // Remove all detected entities from the last run which are not detected anymore
            for (Iterator<PotentialAnnotation> it = candidateEntities.iterator(); it.hasNext();) {
              PotentialAnnotation entity = it.next();
              if (searchEntity(detectedEntities, entity.getBeginIndex(),
                  entity.getEndIndex(), entity.getType()) == null)  {
                
                // TODO: Create an array of entities that should be removed, much faster ...
                EntityContentProvider.this.entityListViewer.remove(entity);
                
                // Can safely be removed, since it can only be an un-confirmed entity
                it.remove();
              }
            }
            
            // Update if entity already exist, or add it
            for (PotentialAnnotation detectedEntity : detectedEntities) {
              
              // Bug: 
              // There can be multiple entities in this span!
              // In this case we want to keep the first, update it, and discard the others!
              
              // Case: One entity spanning two tokens replaces 
              
              PotentialAnnotation entity = searchEntity(candidateEntities, detectedEntity.getBeginIndex(),
                  detectedEntity.getEndIndex(), detectedEntity.getType());
              
              // A confirmed entity already exists, update its confidence score
              if (entity != null) {
                  entity.setBeginIndex(detectedEntity.getBeginIndex());
                  entity.setEndIndex(detectedEntity.getEndIndex());
                  entity.setEntityText(detectedEntity.getEntityText());
                  entity.setConfidence(detectedEntity.getConfidence());
                  
                  EntityContentProvider.this.entityListViewer.refresh(entity);
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
          else {
            EntityContentProvider.this.nameFinderView.setMessage(status.getMessage());
          }
        }
      });
    };
  }
  
  private NameFinderJob nameFinder;
  
  private CasChangeNameFinderTrigger casChangeTrigger = new CasChangeNameFinderTrigger();
  private PreferenceChangeNameFinderTrigger preferenceChangeTrigger = new PreferenceChangeNameFinderTrigger();
  private ConfirmedEntityListener casChangeListener = new ConfirmedEntityListener();
  
  private TableViewer entityListViewer;
  
  private ICasDocument input;
  
  private AnnotationEditor editor;
  
  // contains all existing entity annotations and is synchronized!
  // needed by name finder to calculate updates ... 
  private List<PotentialAnnotation> candidateEntities = new ArrayList<PotentialAnnotation>();
  private List<PotentialAnnotation> confirmedEntities = new ArrayList<PotentialAnnotation>();
  
  private String nameTypeNames[];

  private NameFinderViewPage nameFinderView;
  
  EntityContentProvider(NameFinderViewPage nameFinderView, AnnotationEditor editor, TableViewer entityList) {
    this.nameFinder = new NameFinderJob();
    this.entityListViewer = entityList;
    this.editor = editor;
    this.nameFinderView = nameFinderView;
    
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    
    store.addPropertyChangeListener(preferenceChangeTrigger);
  }
  
  private static boolean contains(String array[], String element) {
    
    for (String arrayElement : array) {
      if (element.equals(arrayElement))
        return true;
    }
    
    return false;
  }
  
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {

    // Problem: "The viewer should not be updated during this call, as it might be in 
   //           the process of being disposed." (Javadoc)
    // Does it mean that the name finder listener must check if the viewer is still alive?
    
    if (oldInput != null) {
      ICasDocument oldDocument = (ICasDocument) oldInput;
      oldDocument.removeChangeListener(casChangeListener);
      oldDocument.removeChangeListener(casChangeTrigger);      
    }
    
    if (newInput != null) {
      input = (ICasDocument) newInput;
      
      // Note: Name Finder might run to often ... 
      input.addChangeListener(casChangeListener);
      input.addChangeListener(casChangeTrigger);
      
      runNameFinder();
    }
  }
  
  void runNameFinder() {
    
    // TODO: Check if sentences do overlap
    // TODO: Check if tokens do overlap
    // TODO: Check that tokens do not intersect with sentence span
    
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    
    // TODO: All preferences should be retrieved when the name finder executed!
    // Just move it down the run method ...
    nameTypeNames = store.getString(OpenNLPPreferenceConstants.NAME_TYPE).split(",");
    
    for (int i = 0; i < nameTypeNames.length; i++) {
      nameTypeNames[i] = nameTypeNames[i].trim();
      
      if (nameTypeNames[i].isEmpty()) {
        nameFinderView.setMessage("Name type name(s) must be set!");
        return;
      }
    }
    
    confirmedEntities.clear();
    
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
        PotentialAnnotation entity = new PotentialAnnotation(nameAnnotation.getBegin(),
            nameAnnotation.getEnd(), nameAnnotation.getCoveredText(), null,
            nameAnnotation.getType().getName());
        confirmedEntities.add(entity); // TODO: This needs to go into a second list!
      }
    }
    
    nameFinder.addJobChangeListener(new NameFinderJobListener());
    
    String sentenceTypeName = store.getString(OpenNLPPreferenceConstants.SENTENCE_TYPE);
    
    if (sentenceTypeName.isEmpty()) {
      nameFinderView.setMessage("Sentence type is not set!");
      return;
    }
    
    String modelPathes[] = store.getString(OpenNLPPreferenceConstants.NAME_FINDER_MODEL_PATH).split(",");
    
    for (int i = 0; i < modelPathes.length; i++) {
      modelPathes[i] = modelPathes[i].trim();
      
      if (modelPathes[i].isEmpty()) {
        nameFinderView.setMessage("Model path is not set!");
        return;
      }
    }
    
    CAS cas = input.getCAS();
    
    String additionalSentenceTypes = store.getString(OpenNLPPreferenceConstants.ADDITIONAL_SENTENCE_TYPE);
    
    String text = cas.getDocumentText();

    if (text != null) {

      Type sentenceTypes[] = UIMAUtil.splitTypes(
          sentenceTypeName + "," +  additionalSentenceTypes, ',', cas.getTypeSystem());
      
      if (sentenceTypes == null) {
        nameFinderView.setMessage("Sentence type does not exist in type system!");
        return;
      }
      
      String tokenName = store.getString(OpenNLPPreferenceConstants.TOKEN_TYPE);
      
      if (tokenName.isEmpty()) {
        nameFinderView.setMessage("Token type name is not set!");
        return;
      }
      
      Type tokenType = cas.getTypeSystem().getType(tokenName);
      
      if (tokenType == null) {
        nameFinderView.setMessage("Token type does not exist in type system!");
        return;
      }
      
      List<Span> sentences = new ArrayList<Span>();
      List<Span> tokens = new ArrayList<Span>();
      
      for (Iterator<AnnotationFS> sentenceIterator = 
          UIMAUtil.createMultiTypeIterator(cas, sentenceTypes);
          sentenceIterator.hasNext();) {
        
        AnnotationFS sentenceAnnotation = (AnnotationFS) sentenceIterator
            .next();
        
        // TODO: Add code to detect overlapping sentences ... not allowed!
        
        sentences.add(new Span(sentenceAnnotation.getBegin(), sentenceAnnotation.getEnd()));
        
        // Performance Note: 
        // The following code has O(n^2) complexity, can be optimized
        // by using a token iterate over all tokens and manual weaving.                  
        
        FSIndex<AnnotationFS> allTokens = cas.getAnnotationIndex(tokenType);
        
        ContainingConstraint containingConstraint = 
            new ContainingConstraint(sentenceAnnotation);
        
        Iterator<AnnotationFS> containingTokens = cas.createFilteredIterator(
            allTokens.iterator(), containingConstraint);
        
        while (containingTokens.hasNext()) {
          AnnotationFS token = (AnnotationFS) containingTokens.next();
          
          tokens.add(new Span(token.getBegin(), token.getEnd()));
        }
      }
      
      List<Span> nameSpans = new ArrayList<Span>();

      for (String nameTypeName : nameTypeNames) {
        
        Type nameType = cas.getTypeSystem().getType(nameTypeName); 
  
        if (nameType == null) {
          nameFinderView.setMessage("Name type " + nameTypeName + " does not exist in type system!");
          return;
        }
        
        FSIndex<AnnotationFS> nameAnnotations = cas
            .getAnnotationIndex(nameType);
  
        for (Iterator<AnnotationFS> nameIterator = nameAnnotations
            .iterator(); nameIterator.hasNext();) {
  
          AnnotationFS nameAnnotation = (AnnotationFS) nameIterator.next();
  
          nameSpans.add(new Span(nameAnnotation.getBegin(), nameAnnotation.getEnd(),
              nameAnnotation.getType().getName()));
        }
      }
      
      // Bug: Changing the data of the name finder will cause an issue if it is already running!
      
      nameFinder.setText(text);
      
      if (sentences.size() == 0) {
        nameFinderView.setMessage("CAS must at least contain one sentence!");
        return;
      }
      
      nameFinder.setSentences(sentences.toArray(new Span[sentences.size()]));
      
      if (tokens.size() == 0) {
        nameFinderView.setMessage("CAS must at least contain one token within a sentence!");
        return;
      }
      
      nameFinder.setTokens(tokens.toArray(new Span[tokens.size()]));
      nameFinder.setModelPath(modelPathes, nameTypeNames);
      
      if (!nameFinder.isSystem()) {
        nameFinder.setSystem(true);
      }
      
      boolean isRecallBoostingEnabled = 
          store.getBoolean(OpenNLPPreferenceConstants.ENABLE_CONFIRMED_NAME_DETECTION);
      
      if (isRecallBoostingEnabled) {
        nameFinder.setVerifiedNames(nameSpans.toArray(new Span[nameSpans.size()]));
      }
      else {
        nameFinder.setVerifiedNames(null);
      }
      
      nameFinder.setIgnoreShortTokens(store.getBoolean(
          OpenNLPPreferenceConstants.IGNORE_SHORT_TOKENS));
      
      nameFinder.setOnlyConsiderAllLetterTokens(store.getBoolean(
          OpenNLPPreferenceConstants.ONLY_CONSIDER_ALL_LETTER_TOKENS));
      
      nameFinder.setOnlyConsiderInitialCapitalLetterTokens(store.getBoolean(
          OpenNLPPreferenceConstants.ONLY_CONSIDER_INITIAL_CAPITAL_TOKENS));
      
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
    IPreferenceStore store = editor.getCasDocumentProvider().getTypeSystemPreferenceStore(editor.getEditorInput());
    store.removePropertyChangeListener(preferenceChangeTrigger);
  }
  
  static List<PotentialAnnotation> searchEntities(List<PotentialAnnotation> entities, int begin, int end) {
    
    List<PotentialAnnotation> intersectingEntities = new ArrayList<PotentialAnnotation>();
    
    Span testSpan = new Span(begin, end);
    
    for (PotentialAnnotation entity : entities) {
      
      Span entitySpan = new Span(entity.getBeginIndex(),
          entity.getEndIndex());
      
      if (entitySpan.intersects(testSpan)) {
        intersectingEntities.add(entity);
      }
    }
    
    return intersectingEntities;
  }
  
  // Could pass null, means any type
  public static PotentialAnnotation searchEntity(List<PotentialAnnotation> entities, int begin, int end, String type) {
    
    Span testSpan = new Span(begin, end);
    
    for (PotentialAnnotation entity : entities) {
      
      Span entitySpan = new Span(entity.getBeginIndex(),
          entity.getEndIndex());
      
      if (entitySpan.intersects(testSpan) && (type == null || type.equals(entity.getType()))) {
        return entity;
      }
    }
    
    return null;
  }
}
