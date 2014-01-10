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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.addons.modelbuilder.ModelGenerationValidator;

/**
 *Validates NER results input before inclusion into the model
 */
public class FileModelValidatorImpl implements ModelGenerationValidator {

  private Set<String> badentities = new HashSet<String>();
  BaseModelBuilderParams params;

  @Override
  public void setParameters(BaseModelBuilderParams params) {
    this.params = params;
  }

  @Override
  public Boolean validSentence(String sentence) {
    //returning true by default, because the sentence provider will  return only "valid" sentences in this case
    return true;
  }

  @Override
  public Boolean validNamedEntity(String namedEntity) {

    if (badentities.isEmpty()) {
      getBlackList();
    }
//
//    Pattern p = Pattern.compile("[0-9]", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
//    if (p.matcher(namedEntity).find()) {
//      return false;
//    }
    Boolean b = true;
    if (badentities.contains(namedEntity.toLowerCase())) {
      b = false;
    }
    return b;
  }

  @Override
  public Collection<String> getBlackList() {
    if (params.getKnownEntityBlacklist() == null) {
      return badentities;
    }
    if (!badentities.isEmpty()) {
      try {
        InputStream fis;
        BufferedReader br;
        String line;

        fis = new FileInputStream(params.getKnownEntityBlacklist());
        br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
        while ((line = br.readLine()) != null) {
          badentities.add(line);
        }
        br.close();
        br = null;
        fis = null;
      } catch (FileNotFoundException ex) {
        Logger.getLogger(FileKnownEntityProvider.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
        Logger.getLogger(FileKnownEntityProvider.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return badentities;
  }
}
