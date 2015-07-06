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
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import opennlp.tools.disambiguator.ims.WTDIMS;
import opennlp.tools.disambiguator.lesk.Lesk;
import opennlp.tools.disambiguator.lesk.LeskParameters;

import org.junit.Test;

public class LeskEvaluatorTest {

  static DataExtractor dExtractor = new DataExtractor();

  @Test
  public static void main(String[] args) {
    Constants.print("Evaluation Started");

    String testDataLoc = "src\\test\\resources\\data\\";
    String helpersLoc = "src\\test\\resources\\helpers\\";

    File[] listOfFiles;
    File testFolder = new File(testDataLoc);

    // these are needed for mapping the sense IDs from the current data
    String dict = helpersLoc + "EnglishLS.dictionary.xml";
    String map = helpersLoc + "EnglishLS.sensemap";

    Lesk lesk = new Lesk();
    LeskParameters leskParams = new LeskParameters();
    leskParams.setLeskType(LeskParameters.LESK_TYPE.LESK_BASIC);
    lesk.setParams(leskParams);

    if (testFolder.isDirectory()) {
      listOfFiles = testFolder.listFiles();
      for (File file : listOfFiles) {
        WSDEvaluator evaluator = new WSDEvaluator(lesk);
        if (file.isFile()) {
          // don't take verbs because they are not from WordNet
          if (!file.getName().split("\\.")[1].equals("v")) {
            HashMap<String, ArrayList<DictionaryInstance>> senses = dExtractor
                .extractWordSenses(dict, map, file.getName());
            ArrayList<WTDIMS> instances = getTestData(file.getAbsolutePath(),
                senses);

            if (instances != null) {
              Constants.print("------------------" + file.getName()
                  + "------------------");
              Constants.print("there are " + instances.size() + " instances");
              for (WordToDisambiguate instance : instances) {
                // Constants.print("sense IDs : " + instance.senseIDs);
                evaluator.evaluateSample(instance);
              }
              Constants.print("the accuracy " + evaluator.getAccuracy() * 100
                  + "%");
            } else {
              Constants.print("null instances");
            }
          }
        }
      }
    }
  }

  protected static ArrayList<WTDIMS> getTestData(String testFile,
      HashMap<String, ArrayList<DictionaryInstance>> senses) {
    /**
     * word tag has to be in the format "word.POS" (e.g., "activate.v",
     * "smart.a", etc.)
     */
    ArrayList<WTDIMS> trainingData = dExtractor.extractWSDInstances(testFile);

    // HashMap<Integer, WTDIMS> trainingData =
    // dExtractor.extractWSDInstances(wordTrainingxmlFile);
    for (WTDIMS data : trainingData) {
      for (String senseId : data.getSenseIDs()) {
        for (String dictKey : senses.keySet()) {
          for (DictionaryInstance instance : senses.get(dictKey)) {
            if (senseId.equals(instance.getId())) {
              data.setSense(Integer.parseInt(dictKey.split("_")[1]));
              break;
            }
          }
        }
      }
    }

    return trainingData;
  }

}
