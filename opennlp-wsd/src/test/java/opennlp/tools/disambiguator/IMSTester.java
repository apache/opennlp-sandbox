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

import opennlp.tools.disambiguator.ims.IMS;

public class IMSTester {

  public static void main(String[] args) {

    IMS ims = new IMS();

    String test = "You have to write an essay without using a dictionary!";
    String[] sentence = Loader.getTokenizer().tokenize(test);
    Constants.print(ims.disambiguate(sentence, 3));

    String test2 = "Please write to me soon.";
    String[] sentence2 = Loader.getTokenizer().tokenize(test2);
    Constants.print(ims.disambiguate(sentence2, 1));

    String test3 = "the argument over foreign aid goes on and on";
    String[] sentence3 = Loader.getTokenizer().tokenize(test3);
    Constants.print(ims.disambiguate(sentence3, 1));

    String test4 = "it was a strong argument that his hypothesis was true";
    String[] sentence4 = Loader.getTokenizer().tokenize(test4);
    Constants.print(ims.disambiguate(sentence4, 3));

  }

}
