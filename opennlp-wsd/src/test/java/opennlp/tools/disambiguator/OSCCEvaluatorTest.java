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

import java.io.IOException;
import java.util.ArrayList;

import opennlp.tools.disambiguator.datareader.SensevalReader;
import opennlp.tools.disambiguator.oscc.OSCCFactory;
import opennlp.tools.disambiguator.oscc.OSCCME;
import opennlp.tools.disambiguator.oscc.OSCCModel;
import opennlp.tools.disambiguator.oscc.OSCCParameters;
import opennlp.tools.util.TrainingParameters;

import org.junit.Test;

public class OSCCEvaluatorTest {

  static SensevalReader seReader = new SensevalReader();

  @Test
  public static void main(String[] args) {
    
    
    WSDHelper.print("Evaluation Started");
    
    // TODO write unit test
    String modelsDir = "src\\test\\resources\\models\\";
    String trainingDataDirectory = "src\\test\\resources\\supervised\\models\\";
    WSDHelper.loadTokenizer(modelsDir + "en-token.bin");
    WSDHelper.loadLemmatizer(modelsDir + "en-lemmatizer.dict");
    WSDHelper.loadTagger(modelsDir + "en-pos-maxent.bin");

    OSCCParameters OSCCParams = new OSCCParameters("");
    OSCCParams.setTrainingDataDirectory(trainingDataDirectory);
    OSCCME oscc = new OSCCME(OSCCParams);
    OSCCModel model = null;
    ArrayList<String> words = seReader.getSensevalWords();

    for (String word : words) {
      // don't take verbs because they are not from WordNet
      if (!word.split("\\.")[1].equals("v")) {
      try {
        model = OSCCME.train("en", seReader.getSensevalDataStream(word), new TrainingParameters(), OSCCParams,
            new OSCCFactory());
        model.writeModel(OSCCParams.getTrainingDataDirectory() + word);
        oscc = new OSCCME(model, OSCCParams);
        
      } catch (IOException e) {
        e.printStackTrace();
        WSDHelper.print("skipped sample");
      }
      
      WSDEvaluator evaluator = new WSDEvaluator(oscc);
        ArrayList<WSDSample> instances = seReader.getSensevalData(word);
        if (instances != null) {
          WSDHelper.print("------------------" + word + "------------------");
          for (WSDSample instance : instances) {
            if (instance.getSenseIDs() != null
                && !instance.getSenseIDs()[0].equals("null")) {
              evaluator.evaluateSample(instance);
            }else{
              WSDHelper.print("skipped sample");
            }
          }
          WSDHelper.print(evaluator.toString());
        } else {
          WSDHelper.print("null instances");
        }
      }

    }

  }
}
