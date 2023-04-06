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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.addons.modelbuilder.KnownEntityProvider;

public class FileKnownEntityProvider implements KnownEntityProvider {
 
  final Set<String> knownEntities = new HashSet<>();
  BaseModelBuilderParams params;

  @Override
  public Set<String> getKnownEntities() {
    if (knownEntities.isEmpty()) {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(
              new FileInputStream(params.getKnownEntitiesFile()), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          knownEntities.add(line);
        }
      } catch (IOException ex) {
        Logger.getLogger(FileKnownEntityProvider.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return knownEntities;
  }

  @Override
  public void addKnownEntity(String unambiguousEntity) {
    knownEntities.add(unambiguousEntity);
  }

  @Override
  public String getKnownEntitiesType() {
    return params.getEntityType();
  }

  @Override
  public void setParameters(BaseModelBuilderParams params) {
    this.params = params;
  }
}
