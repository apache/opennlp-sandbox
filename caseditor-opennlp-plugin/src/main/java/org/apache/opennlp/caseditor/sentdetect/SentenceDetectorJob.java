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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;

import org.apache.opennlp.caseditor.ModelUtil;
import org.apache.opennlp.caseditor.OpenNLPPlugin;
import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public class SentenceDetectorJob extends Job {

  private SentenceDetectorME sentenceDetector;
  
  private String modelPath;
  
  private String text;
  
  private String sentenceType;
  
  private List<Span> paragraphs;
  
  private List<PotentialAnnotation> detectedSentences;

  private List<Span> exclusionSpans;
  
  public SentenceDetectorJob() {
    super("Sentence Detector Job");
  }
  
  synchronized void setModelPath(String modelPath) {
    this.modelPath = modelPath;
  }

  synchronized void setText(String text) {
    this.text = text;
  }
  
  synchronized void setSentenceType(String sentenceType) {
    this.sentenceType = sentenceType;
  }
  
  public void setParagraphs(List<Span> paragraphs) {
    this.paragraphs =  paragraphs;
  }
  
  public void setExclusionSpans(List<Span> exclusionSpans) {
    
    this.exclusionSpans = new ArrayList<Span>();
    this.exclusionSpans.addAll(exclusionSpans);
    Collections.sort(this.exclusionSpans);
  }
  
  @Override
  protected IStatus run(IProgressMonitor monitor) {
    
    // lazy load model
    if (sentenceDetector == null) {
      InputStream modelIn = null;
      try {
        modelIn = ModelUtil.openModelIn(modelPath);
        SentenceModel model = new SentenceModel(modelIn);
        sentenceDetector = new SentenceDetectorME(model);
      } catch (IOException e1) {
        return new Status(IStatus.CANCEL, OpenNLPPlugin.ID, "Failed to load sentence detector model!");
      }
      finally {
        if (modelIn != null) {
          try {
            modelIn.close();
          } catch (IOException e) {
          }
        }
      }
    }
    
    detectedSentences = new ArrayList<PotentialAnnotation>();
    for (Span para : paragraphs) {

      List<Span> textBlocks = new ArrayList<Span>();

      int textBlockBeginIndex = 0;
      
      for (Span exclusionSpan : exclusionSpans) {
        
        Span textBlockSpan = new Span(textBlockBeginIndex, exclusionSpan.getStart());
        
        // TODO: Filter out whitespace sentences ...
        
        if (textBlockSpan.length() > 0) {
          textBlocks.add(textBlockSpan);
        }
        
        textBlockBeginIndex = exclusionSpan.getEnd();
      }
      
      if (textBlockBeginIndex < para.getEnd() - para.getStart()) {
        textBlocks.add(new Span(textBlockBeginIndex, para.getEnd()));
      }
      
      for (Span textBlock : textBlocks) {
        Span sentenceSpans[] = sentenceDetector.sentPosDetect(
            textBlock.getCoveredText(text).toString());
        
        double confidence[] = sentenceDetector.getSentenceProbabilities();
        
        for (int i = 0; i < sentenceSpans.length; i++) {
          Span sentenceSpan = sentenceSpans[i];
          String sentenceText = text.substring(textBlock.getStart() + sentenceSpan.getStart(),
              textBlock.getStart() + sentenceSpan.getEnd());
          detectedSentences.add(new PotentialAnnotation(textBlock.getStart() + sentenceSpan.getStart(), 
              textBlock.getStart() + sentenceSpan.getEnd(), sentenceText,
              confidence[i], sentenceType));
        }
      }
    }
    
    return new Status(IStatus.OK, OpenNLPPlugin.ID, "OK");
  }

  PotentialAnnotation[] getDetectedSentences() {
    return detectedSentences.toArray(new PotentialAnnotation[detectedSentences.size()]);
  }
}
