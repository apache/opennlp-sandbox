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

public class LevensteinDistanceFinder {

  public static double matchLevensteinDistance(String str1, String str2) {
    if (str1.length() <= str2.length()) {
      if (str2.indexOf(str1) == 0) {
        return 0;
      }
    }
    if (str2.length() < str1.length()) {
      if (str1.indexOf(str2) == 0) {
        return 0;
      }
    }

    return levensteinDistance(str1, str2, 1, 10, 1, 10)
        / (str1.length() + 1 + str2.length());
  }

  /**
   * Computes Levenstain distance (unit distance) between two strings. Use
   * dynamic programming algorithm to calculate matrix with distances between
   * substrings. Time complexity - O(length1 * length2), memory - O(length1 +
   * length2)
   * 
   * @return distance between strings.
   */
  public static double levensteinDistance(String str1, String str2,
      int letterInsDelCost, int digitInsDelCost, int letterReplaceCost,
      int digitReplaceCost) {
    int length1 = str1.length() + 1;
    int length2 = str2.length() + 1;
    int[] upper = new int[length2];
    int[] left = new int[length1];
    upper[0] = 0;
    left[0] = 0;
    for (int i = 1; i < length1; i++) {
      int cost = letterInsDelCost; // 1 is a cost for deleting a character
      if (Character.isDigit(str1.charAt(i - 1))) {
        cost = digitInsDelCost;
      }
      left[i] = left[i - 1] + cost;
    }
    for (int j = 1; j < length2; j++) {
      int cost = letterInsDelCost; // 1 is a cost for inserting a character
      if (Character.isDigit(str2.charAt(j - 1))) {
        cost = digitInsDelCost;
      }
      upper[j] = upper[j - 1] + cost;
      int min = 0;
      for (int i = 1; i < length1; i++) {
        cost = letterInsDelCost; // 1 is a cost for inserting a character
        if (Character.isDigit(str1.charAt(i - 1))) {
          cost = digitInsDelCost;
        }
        int fromLeft = left[i] + cost;
        cost = letterInsDelCost; // 1 is a cost for deleting a character
        if (Character.isDigit(str2.charAt(j - 1))) {
          cost = digitInsDelCost;
        }
        int fromUp = upper[j] + cost;
        int delta = 0;
        if (str1.charAt(i - 1) != str2.charAt(j - 1)) {
          // 1 is a cost for replacing a character
          delta = letterReplaceCost;
          if (Character.isDigit(str1.charAt(i - 1))
              || Character.isDigit(str2.charAt(j - 1))) {
            delta = digitReplaceCost;
          }
        }
        int cross = left[i - 1] + delta;
        if (fromLeft < fromUp) {
          if (fromLeft < cross) {
            min = fromLeft;
          } else {
            min = cross;
          }
        } else {
          if (fromUp < cross) {
            min = fromUp;
          } else {
            min = cross;
          }
        }
        left[i - 1] = upper[j];
        upper[j] = min;
      }
    }
    return upper[length2 - 1];
  }

  public static double distanceBetweenStringArraysAsSpaceSepar(String line1,
      String line2) {
    String[] strings1 = line1.split(" ");
    String[] strings2 = line2.split(" ");
    if (strings1.length == 0 || strings2.length == 0) {
      return -1;
    }
    boolean[] selected2 = new boolean[strings2.length];
    boolean[] selected1 = new boolean[strings1.length];
    int intersectNum = 0;
    for (int i = 0; i < strings1.length; i++) {
      for (int j = 0; j < strings2.length; j++) {
        if (selected1[i]) {
          continue;
        }
        if (selected2[j]) {
          continue;
        }
        if (levensteinDistance(strings1[i], strings2[j], 1, 1, 1, 1)
            / (strings1.length + strings2.length) < 0.2) {
          intersectNum++;
          selected2[j] = true;
          selected1[i] = true;
        }
      }
    }
    if (strings1.length == intersectNum || strings2.length == intersectNum) {
      return ((double) (strings1.length + strings2.length - 2 * intersectNum))
          / (strings1.length + strings2.length) / 10; // bg - 20
    } else {
      return ((double) (strings1.length + strings2.length - 2 * intersectNum))
          / (strings1.length + strings2.length) / 4; // bg - 1.5
    }
  }

}
