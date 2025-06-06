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

import opennlp.addons.modelbuilder.SentenceProvider;

/**
 * Provides user sentences via a simple text file.
 *
 * @see SentenceProvider
 */
public class FileSentenceProvider implements SentenceProvider {

  private final Set<String> sentences = new HashSet<>();
  private BaseModelBuilderParams params ;

  public FileSentenceProvider(BaseModelBuilderParams params) {
    if (params == null) {
      throw new IllegalArgumentException("BaseModelBuilderParams cannot be null!");
    }
    this.params = params;
  }

  @Override
  public Set<String> getSentences() {
     if (sentences.isEmpty()) {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(
              new FileInputStream(params.getSentenceFile()), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
          sentences.add(line);
        }
      } catch (IOException ex) {
        Logger.getLogger(FileKnownEntityProvider.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return sentences;
  }

  @Override
  public void setParameters(BaseModelBuilderParams params) {
    this.params = params;
  }
}
