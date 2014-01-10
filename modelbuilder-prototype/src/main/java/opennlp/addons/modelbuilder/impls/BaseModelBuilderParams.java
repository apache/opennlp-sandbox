/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.addons.modelbuilder.impls;

import java.io.File;
import java.util.Map;

/**
 *
 * Used to pass params through the processing
 */
public class BaseModelBuilderParams {

  private File modelFile;
  private File sentenceFile;
  private File knownEntitiesFile;
  private File knownEntityBlacklist;
  private File annotatedTrainingDataFile;
  private String entityType;
  private Map<String, String> additionalParams;

  public File getModelFile() {
    return modelFile;
  }

  public void setModelFile(File modelFile) {
    this.modelFile = modelFile;
  }

  public File getSentenceFile() {
    return sentenceFile;
  }

  public void setSentenceFile(File sentenceFile) {
    this.sentenceFile = sentenceFile;
  }

  public File getKnownEntitiesFile() {
    return knownEntitiesFile;
  }

  public void setKnownEntitiesFile(File knownEntitiesFile) {
    this.knownEntitiesFile = knownEntitiesFile;
  }

  public File getKnownEntityBlacklist() {
    return knownEntityBlacklist;
  }

  public void setKnownEntityBlacklist(File knownEntityBlacklist) {
    this.knownEntityBlacklist = knownEntityBlacklist;
  }

  public Map<String, String> getAdditionalParams() {
    return additionalParams;
  }

  public void setAdditionalParams(Map<String, String> additionalParams) {
    this.additionalParams = additionalParams;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public File getAnnotatedTrainingDataFile() {
    return annotatedTrainingDataFile;
  }

  public void setAnnotatedTrainingDataFile(File annotatedTrainingDataFile) {
    this.annotatedTrainingDataFile = annotatedTrainingDataFile;
  }
}