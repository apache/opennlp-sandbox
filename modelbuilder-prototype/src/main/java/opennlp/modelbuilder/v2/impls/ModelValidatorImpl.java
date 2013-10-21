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
package opennlp.modelbuilder.v2.impls;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import opennlp.modelbuilder.v2.ModelGenerationValidator;

/**
 *
 * @author Owner
 */
public class ModelValidatorImpl implements ModelGenerationValidator {

  private Set<String> badentities = new HashSet<String>();
  private final double MIN_SCORE_FOR_TRAINING = 0.95d;
  private Object validationData;
 private Map<String, String> params = new HashMap<String, String>();

  @Override
  public void setParameters(Map<String, String> params) {
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

    Pattern p = Pattern.compile("[0-9]", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    if (p.matcher(namedEntity).find()) {
      return false;
    }
    Boolean b = true;
    if (badentities.contains(namedEntity.toLowerCase())) {
      b = false;
    }
    return b;
  }

 
  @Override
  public Set<String> getBlackList() {
    badentities.add(".");
    badentities.add("-");
    badentities.add(",");
    badentities.add(";");
    badentities.add("the");
    badentities.add("that");
    badentities.add("several");
    badentities.add("model");
    badentities.add("our");
    badentities.add("are");
    badentities.add("in");
    badentities.add("are");
    badentities.add("at");
    badentities.add("is");
    badentities.add("for");
    badentities.add("the");
    badentities.add("during");
    badentities.add("south");
    badentities.add("from");
    badentities.add("recounts");
    badentities.add("wissenschaftliches");
    badentities.add("if");
    badentities.add("security");
    badentities.add("denouncing");
    badentities.add("writes");
    badentities.add("but");
    badentities.add("operation");
    badentities.add("adds");
    badentities.add("Above");
    badentities.add("but");
    badentities.add("RIP");
    badentities.add("on");
    badentities.add("no");
    badentities.add("agrees");
    badentities.add("year");
    badentities.add("for");
    badentities.add("you");
    badentities.add("red");
    badentities.add("added");
    badentities.add("hello");
    badentities.add("around");
    badentities.add("has");
    badentities.add("turn");
    badentities.add("surrounding");
    badentities.add("\" No");
    badentities.add("aug.");
    badentities.add("or");
    badentities.add("quips");
    badentities.add("september");
    badentities.add("[mr");
    badentities.add("diseases");
    badentities.add("when");
    badentities.add("bbc");
    badentities.add(":\"");
    badentities.add("dr");
    badentities.add("baby");
    badentities.add("on");
    badentities.add("route");
    badentities.add("'");
    badentities.add("\"");
    badentities.add("a");
    badentities.add("her");
    badentities.add("'");
    badentities.add("\"");
    badentities.add("two");
    badentities.add("that");
    badentities.add(":");
    badentities.add("one");
    badentities.add("Party");
    badentities.add("Championship");

    badentities.add("Ltd");

    return badentities;
  }
}
