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
package opennlp.tools.nl2code;

import java.util.ArrayList;
import java.util.List;

public class ObjectPhraseListForSentence {
  List<ObjectPhrase> oPhrases;
  ObjectControlOp contrOp;

  public ObjectPhraseListForSentence(List<ObjectPhrase> oPhrases2,
      ObjectControlOp op) {
    this.oPhrases = oPhrases2;
    this.contrOp=op;
  }

  public String toString(){
    String ret= "";
    ret+= contrOp.getOperatorFor();
    ret+= contrOp.getOperatorIf();
    ret+=" "+oPhrases.toString();
    return ret;
  }

  public void cleanMethodNamesIsAre(){
    for(int i=0; i<oPhrases.size(); i++){
      ObjectPhrase o1 = oPhrases.get(i);
      if (o1.getMethod()!=null && (o1.getMethod().equals("is")|| o1.getMethod().equals("are") 
          || o1.getMethod().equals("have") || o1.getMethod().equals("has"))){
        ObjectPhrase o1new = new ObjectPhrase();
        o1new.setObjectName(o1.getObjectName());
        o1new.setMethod("");
        o1new.setParamValues(o1.getParamValues());
        oPhrases.set(i, o1new);
      }
    }
  }
  //[pixel.area([]), pixels.belongs([]), null.are([less, 128])
  public void substituteNullObjectIntoEmptyArg(){
    for(int i=0; i<oPhrases.size(); i++)
      for(int j=i+1; j<oPhrases.size(); j++){
        ObjectPhrase o1 = oPhrases.get(i), o2 = oPhrases.get(j);
        if (o2.getObjectName()==null && o1.getParamValues().size()<1){
          String newArgs = o2.getMethod()+"("+o2.getParamValues()+")";
          ObjectPhrase o1new = new ObjectPhrase();
          o1new.setObjectName(o1.getObjectName());
          o1new.setMethod(o1.getMethod());
          List<String> paramValues = new ArrayList<String>();
          paramValues.add(newArgs);
          o1new.setParamValues(paramValues);
          oPhrases.set(i, o1new);
          oPhrases.remove(j);
        }
      }
  }

  //area.size([]), threshold.([below])
  public void substituteIntoEmptyArgs() {
    for(int i=0; i<oPhrases.size(); i++)
      for(int j=i+1; j<oPhrases.size(); j++){
        ObjectPhrase o1 = oPhrases.get(i), o2 = oPhrases.get(j);
        if (o1.getParamValues().size()<1 && o2.getMethod().equals("")){
          String newArgs = o2.getMethod()+"("+o2.getParamValues()+")";
          ObjectPhrase o1new = new ObjectPhrase();
          o1new.setObjectName(o1.getObjectName());
          o1new.setMethod(o1.getMethod());
          List<String> paramValues = new ArrayList<String>();
          paramValues.add(newArgs);
          o1new.setParamValues(paramValues);
          oPhrases.set(i, o1new);
          oPhrases.remove(j);
        }
      }
  }

  ////[null.2([])]
  public void clearInvalidObject() {
    for(int i=0; i<oPhrases.size(); i++){
      ObjectPhrase o1 = oPhrases.get(i);
      if (o1.getObjectName()==null && o1.getParamValues().size()<1){
       
        oPhrases.remove(i);
      }
    }
  }  
  

}
