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

package opennlp.tools.disambiguator;

import opennlp.tools.disambiguator.mfs.MFS;

/**
 * This is a typical example of how to call the disambiguation function in the
 * MFS class.
 */
public class MFSTester {

  public static void main(String[] args) {

    MFS mfs = new MFS();

    String test1 = "Please write to me soon.";
    String[] sentence1 = Loader.getTokenizer().tokenize(test1);
    Constants.print(mfs.disambiguate(sentence1, 1));

    String test2 = "it was a strong argument that his hypothesis was true";
    String[] sentence2 = Loader.getTokenizer().tokenize(test2);
    Constants.print(mfs.disambiguate(sentence2, 3));

    String test3 = "the component was highly radioactive to the point that it has been activated the second it touched water";
    String[] sentence3 = Loader.getTokenizer().tokenize(test3);
    Constants.print(mfs.disambiguate(sentence3, 12));

  }

}
