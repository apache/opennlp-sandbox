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

package opennlp.tools.similarity.apps;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.similarity.apps.SpeechRecognitionResultsProcessor.SentenceMeaningfullnessScore;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechRecognitionResultsProcessorTest {

  @Test
  void testRestaurantEntityInSpeechRecognitionResults() {
    SpeechRecognitionResultsProcessor proc = new SpeechRecognitionResultsProcessor();
    List<SentenceMeaningfullnessScore> res = proc.runSearchAndScoreMeaningfulness(Arrays.asList(
        "remember to buy milk tomorrow for details",
        "remember to buy milk tomorrow from trader joes",
        "remember to buy milk tomorrow from 3 to jones",
        "remember to buy milk tomorrow for for details",
        "remember to buy milk tomorrow from third to joes",
        "remember to buy milk tomorrow from third to jones",
        "remember to buy milk tomorrow from for d jones"));

    assertTrue(res.get(1).getScore() > res.get(0).getScore()
        && res.get(1).getScore() > res.get(2).getScore()
        && res.get(1).getScore() > res.get(3).getScore()
        && res.get(1).getScore() > res.get(4).getScore()
        && res.get(1).getScore() > res.get(5).getScore()
        && res.get(1).getScore() > res.get(6).getScore());
    proc.close();

  }

}
