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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.StringPattern;

import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

// Add error handling, if something goes wrong, an error should be reported!
// Need a rule, only one name finder job at a time ...
// don't change setting, while job is running!
public class NameFinderJob extends Job {
  
  
  private MultiModelNameFinder nameFinder;
  
  private String modelPath[];
  
  private String modelTypes[];
  
  private String text;
  private Span sentences[];
  private Span tokens[];
  private Span verifiedNames[] = new Span[0];
  
  private List<PotentialAnnotation> nameList;

  private boolean ignoreShortTokens;

  private boolean onlyConsiderAllLetterTokens;

  private boolean onlyConsiderInitialLetterTokens;
  
  NameFinderJob() {
    super("Name Finder Job");
  }
  
  /**
   * @param modelPath
   */
  synchronized void setModelPath(String modelPathes[], String modelTypes[]) {
    this.modelPath = modelPathes;
    this.modelTypes = modelTypes;
  }
  
  synchronized void setText(String text) {
    this.text = text;
  }
  
  synchronized void setSentences(Span sentences[]) {
    this.sentences = sentences;
  }
  
  synchronized void setTokens(Span tokens[]) {
    this.tokens = tokens;
  }
  
  synchronized void setVerifiedNames(Span verifiedNames[]) {
    this.verifiedNames = verifiedNames;
  }

  synchronized void setIgnoreShortTokens(boolean ignoreShortTokens) {
    this.ignoreShortTokens = ignoreShortTokens;
  }
  
  synchronized void setOnlyConsiderAllLetterTokens(boolean onlyConsiderAllLetterTokens) {
    this.onlyConsiderAllLetterTokens = onlyConsiderAllLetterTokens; 
  }
  
  synchronized void setOnlyConsiderInitialCapitalLetterTokens(boolean onlyConsiderInitialLetterTokens) {
    this.onlyConsiderInitialLetterTokens = onlyConsiderInitialLetterTokens;
  }
  
  // maybe report result, through an interface?!
  // Note: Concurrency issue ... here! Editor might already be closed after model is loaded!
  // The job change listener in the Entity Content Provider must handle that!
  @Override
  protected synchronized IStatus run(IProgressMonitor monitor) {

    
    // TODO: Check if model path changed, compared to last run, if so reload
    // TODO: Check if the model itself changed, compared to last run, if so reload
    if (nameFinder == null) {
      try {
        nameFinder = new MultiModelNameFinder(modelPath, modelTypes);
      } catch (IOException e) {
        return new Status(IStatus.CANCEL, OpenNLPPlugin.ID, e.getMessage());
      }
    }

    if (nameFinder != null) {
      nameFinder.clearAdaptiveData(); // TODO: If model loading fails we get a NPE here!
    
      nameList = new ArrayList<PotentialAnnotation>();
      
      // TODO: Name tokens, should be for the entire text,
      // not just the prev sentences ...
      Set<String> nameTokens = new HashSet<String>();
      
      for (Span sentence : sentences) {
        
        // Create token list for sentence
        List<Span> sentenceTokens = new ArrayList<Span>();
        
        for (Span token : tokens) {
          if (sentence.contains(token)) {
            sentenceTokens.add(token);
          }
        }
        
        String tokenStrings[] = new String[sentenceTokens.size()];
        
        for (int i = 0; i < sentenceTokens.size(); i++) {
          Span token = sentenceTokens.get(i);
          tokenStrings[i] = token.getCoveredText(text).toString();
        }
        
        Map<Integer, String> verifiedNameTokens = new HashMap<Integer, String>();
        
        // Note: This is slow!
        // iterate over names, to find token indexes
        if (verifiedNames != null) {
          for (Span verifiedName : verifiedNames) {
            boolean isStart = true;
          	
            for (int i = 0; i < sentenceTokens.size(); i++) {
              if (verifiedName.contains(sentenceTokens.get(i))) {
                
                String outcome;
                
                // Need better mechanism here, first token in entity should be start!
                if (isStart) {
                  outcome = NameFinderME.START;
                  isStart = false;
                }
                else {
                  outcome = NameFinderME.CONTINUE;
                }
                
                // TODO: Overlapping names are dangerous here!
                
                // TODO: We could use type information here ... 
                // as part of the outcome!
                verifiedNameTokens.put(i, verifiedName.getType() + "-" + outcome);
                
                StringPattern pattern = StringPattern.recognize(tokenStrings[i]);
                
                boolean useToken = true;
                
                if (ignoreShortTokens && tokenStrings[i].length() < 4) {
                  useToken = false;
                }
                else if (onlyConsiderAllLetterTokens && !pattern.isAllLetter()) {
                  useToken = false;
                }
                else if (onlyConsiderInitialLetterTokens && !pattern.isInitialCapitalLetter()) {
                  useToken = false;
                }
                  
                if (useToken) {
              	  nameTokens.add(verifiedName.getType() + "-" + tokenStrings[i]);
                }
              }
            }
          }
        }
        nameFinder.setRestriction(verifiedNameTokens);
        nameFinder.setNameOnlyTokens(nameTokens);
        
        // TODO: Use multiple name finders here .... 
        ConfidenceSpan names[] = nameFinder.find(tokenStrings);
        
        for (int i = 0; i < names.length; i++) {
          
          // add sentence offset here ...
          
          int beginIndex = sentenceTokens.get(names[i].getStart()).getStart();
          int endIndex = sentenceTokens.get(names[i].getEnd() - 1).getEnd();
          
          String coveredText = text.substring(beginIndex, endIndex);
          
          nameList.add(new PotentialAnnotation(beginIndex, endIndex, coveredText,
              names[i].getConfidence(), names[i].getType()));
        }
      }
    }
    
    // TODO: If there is a problem return an error status,
    // and calling client can fetch error message via method call
    // Use OpenNLPPlugin to log errors ...
    return new Status(IStatus.OK, OpenNLPPlugin.ID, "OK");
  }

  public List<PotentialAnnotation> getNames() {
    List<PotentialAnnotation> names = new ArrayList<PotentialAnnotation>();
    names.addAll(nameList);
    return names;
  }
}
