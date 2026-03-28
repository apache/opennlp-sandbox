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
 * Used to pass params through the processing.
 */
public record BaseModelBuilderParams (File sentenceFile, File knownEntitiesFile, File knownEntitiesBlacklist,
                                      File modelFile, File annotatedTrainingDataFile, String entityType,
                                      Map<String, String> additionalParams) {

  public File getModelFile() {
    return modelFile;
  }

  public File getSentenceFile() {
    return sentenceFile;
  }

  public File getKnownEntitiesFile() {
    return knownEntitiesFile;
  }

  public File getKnownEntityBlacklist() {
    return knownEntitiesBlacklist;
  }

  public Map<String, String> getAdditionalParams() {
    return additionalParams;
  }
  
  public String getEntityType() {
    return entityType;
  }

  public File getAnnotatedTrainingDataFile() {
    return annotatedTrainingDataFile;
  }
}