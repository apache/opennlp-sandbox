/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.disambiguator.mfs;

import opennlp.tools.disambiguator.WSDParameters;

public class MFSParameters extends WSDParameters {

  public MFSParameters(){
    this.isCoarseSense = false;
    this.source = Source.WORDNET;
  }
  
  public static enum Source {
    WORDNET(1, "wordnet");

    public int code;
    public String src;

    private Source(int code, String src) {
      this.code = code;
      this.src = src;
    }
  }

  protected Source source;

  public Source getSource() {
    return source;
  }

  public void setSource(Source source) {
    this.source = source;
  }

  @Override
  public boolean isValid() {
    if (this.source.code == 1) {
      return true;
    }
    return false;
  }

}
