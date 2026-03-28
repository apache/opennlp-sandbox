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

package opennlp.tools.similarity.apps.utils;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValueSortMapTest {

  private Map<String, String> hmValue;

  @BeforeEach
  public void setup() {
    hmValue = new HashMap<>();

    hmValue.put("ZNU", "Zuki Ndulo");
    hmValue.put("YSH", "Yogesh Sharma");
    hmValue.put("HHU", "Hiram Hugesh");
    hmValue.put("MLE", "Marry Lee");
    hmValue.put("FST", "Faran Stott");
    hmValue.put("HET", null);
    hmValue.put("SID", null);
    hmValue.put("AFR", "Alice Fryer");
    hmValue.put("KIQ", null);
    hmValue.put("JBE", "Jim Bell");
    hmValue.put("MAU", null);
    hmValue.put("KAE", null);
    hmValue.put("JBA", "Jim Bader");
    hmValue.put("RAN", "Robert Anthony");
    hmValue.put("CLE", "Carole Lee");
    hmValue.put("JMD", "Jim Bader");
    hmValue.put("ALI", null);
    hmValue.put("GMI", "Gracia Millan");
    hmValue.put("MAL", "Marry Lee");
    hmValue.put("CLE", "Carole Lee"); // duplicate on purpose !
    hmValue.put("APE", "Annin Peck");
    hmValue.put("HUA", null);
  }

  @Test
  public void testSortMapByValueWithImplicitOrder() {
    // Implicit: ascending
    testSortMapByValue(null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void testSortMapByValueWithExplicitOrder(boolean ascending) {
    testSortMapByValue(ascending);
  }

  private void testSortMapByValue(Boolean ascending) {
    final Map<String, String> sortedMap;
    // Test
    if (ascending != null) {
      sortedMap = ValueSortMap.sortMapByValue(hmValue, ascending);
    } else {
      sortedMap = ValueSortMap.sortMapByValue(hmValue); // results in ascending order
      ascending = true;
    }
    // Check
    assertNotNull(sortedMap);
    assertFalse(sortedMap.isEmpty());

    int countNull = 0;
    boolean hasNullsAtBegin = false;

    String prevValue = null;
    String currValue;
    for(Map.Entry<String, String> entry : sortedMap.entrySet()) {
      currValue = entry.getValue();
      if (currValue == null) {
        if (countNull == 0) {
          hasNullsAtBegin = true;
        }
        countNull++;
      } else {
        if (prevValue != null) {
          int lexComparison = prevValue.compareTo(currValue);
          if (ascending) {
            assertTrue(lexComparison < 0 || lexComparison == 0);
          } else {
            assertTrue(lexComparison > 0 || lexComparison == 0);
          }
        }
        prevValue = currValue;
      }
    }
    // 7 positions are expected as 'null' values
    assertEquals(7, countNull);
    if(ascending) {
      assertTrue(hasNullsAtBegin);
    }
  }
}
