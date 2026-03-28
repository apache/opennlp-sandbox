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

public class ObjectControlOp {
  String operatorFor;
  String operatorIf;
  String linkUp;
  String linkDown;
  
  
  public ObjectControlOp() {
    operatorFor="";
    operatorIf="";
  }
  public String getOperatorFor() {
    return operatorFor;
  }
  public void setOperatorFor(String operatorFor) {
    this.operatorFor = operatorFor;
  }
  public String getOperatorIf() {
    return operatorIf;
  }
  public void setOperatorIf(String operatorIf) {
    this.operatorIf = operatorIf;
  }
  public String getLinkUp() {
    return linkUp;
  }
  public void setLinkUp(String linkUp) {
    this.linkUp = linkUp;
  }
  public String getLinkDown() {
    return linkDown;
  }
  public void setLinkDown(String linkDown) {
    this.linkDown = linkDown;
  }
  
  public String toString(){
    return operatorFor+ "(" + operatorIf;
  }
  
}
