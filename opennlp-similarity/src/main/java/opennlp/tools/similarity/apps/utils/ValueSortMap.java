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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class is used to show how you can sort a java.uti.Map for values. This
 * also takes care of null and duplicate values present in the map.
 */
public class ValueSortMap {

  private ValueSortMap() {
  }

  /**
   * This method returns the new LinkedHashMap sorted with values for passed
   * Comparater. If null values exist they will be put in the last of the
   * returned LinkedHashMap. If there are duplicate values they will come
   * together at the values ordering order but ordering between same multiple
   * values is ramdom. Passed Map will be intect.
   * 
   * @param inMap
   *          Map to be sorted
   * @param comparator
   *          Values will be sorted as per passed Comparater
   * @return LinkedHashMap Sorted new LinkedHashMap
   */
  public static <K, V> LinkedHashMap<K, V> sortMapByValue(Map<K, V> inMap,
      Comparator<V> comparator) {
    return sortMapByValue(inMap, comparator, null);
  }

  /**
   * This method returns the new LinkedHashMap sorted with values for passed
   * ascendingOrder. If null values exist they will be put in the last for true
   * value of ascendingOrder or will be put on top of the returned LinkedHashMap
   * for false value of ascendingOrder. If there are duplicate values they will
   * come together at the values ordering order but ordering between same
   * multiple values is ramdom. Passed Map will be intect.
   * 
   * @param inMap
   *          Map to be sorted
   * @param ascendingOrder
   *          Values will be sorted as per value of ascendingOrder
   * @return LinkedHashMap Sorted new LinkedHashMap
   */
  public static <K, V> LinkedHashMap<K, V> sortMapByValue(Map<K, V> inMap,
      boolean ascendingOrder) {
    return sortMapByValue(inMap, null, ascendingOrder);
  }

  /**
   * This method returns the new LinkedHashMap sorted with values in ascending
   * order. If null values exist they will be put in the last of the returned
   * LinkedHashMap. If there are duplicate values they will come together at the
   * values ordering order but ordering between same multiple values is ramdom.
   * Passed Map will be intect.
   * 
   * @param inMap
   *          Map to be sorted
   * @return LinkedHashMap Sorted new LinkedHashMap
   */
  public static <K, V> LinkedHashMap<K, V> sortMapByValue(Map<K, V> inMap) {
    return sortMapByValue(inMap, null, null);
  }

  /**
   * This method returns the new LinkedHashMap sorted with values. Values will
   * be sorted as value of passed comparator if ascendingOrder is null or in
   * order of passed ascendingOrder if it is not null. If null values exist they
   * will be put in the last for true value of ascendingOrder or will be put on
   * top of the returned LinkedHashMap for false value of ascendingOrder. If
   * there are duplicate values they will come together at the values ordering
   * order but ordering between same multiple values is ramdom. Passed Map will
   * be intect.
   * 
   * @param inMap
   *          Map to be sorted
   * @param comparator
   *          Values will be sorted as per passed Comparater
   * @param ascendingOrder
   *          Values will be sorted as per value of ascendingOrder
   * @return LinkedHashMap Sorted new LinkedHashMap
   */
  private static <K, V> LinkedHashMap<K, V> sortMapByValue(Map<K, V> inMap,
      Comparator<V> comparator, Boolean ascendingOrder) {
    int iSize = inMap.size();

    // Create new LinkedHashMap that need to be returned
    LinkedHashMap sortedMap = new LinkedHashMap(iSize);

    Collection values = inMap.values();
    ArrayList valueList = new ArrayList(values); // To get List of all values in
                                                 // passed Map
    HashSet distinctValues = new HashSet(values); // To know the distinct values
                                                  // in passed Map

    // Do handing for null values. remove them from the list that will be used
    // for sorting
    int iNullValueCount = 0; // Total number of null values present in passed
                             // Map
    if (distinctValues.contains(null)) {
      distinctValues.remove(null);
      for (int i = 0; i < valueList.size(); i++) {
        if (valueList.get(i) == null) {
          valueList.remove(i);
          iNullValueCount++;
          i--;
          continue;
        }
      }
    }

    // Sort the values of the passed Map
    if (ascendingOrder == null) {
      // If Boolean ascendingOrder is null, use passed comparator for order of
      // sorting values
      Collections.sort(valueList, comparator);
    } else if (ascendingOrder.booleanValue()) {
      // If Boolean ascendingOrder is not null and is true, sort values in
      // ascending order
      Collections.sort(valueList);
    } else {
      // If Boolean ascendingOrder is not null and is false, sort values in
      // descending order
      Collections.sort(valueList);
      Collections.reverse(valueList);
    }

    // Check if there are multiple same values exist in passed Map (not
    // considering null values)
    boolean bAllDistinct = true;
    if (iSize != (distinctValues.size() + iNullValueCount))
      bAllDistinct = false;

    Object key = null, value = null, sortedValue;
    Set keySet = null;
    Iterator itKeyList = null;
    HashMap hmTmpMap = new HashMap(iSize);
    HashMap hmNullValueMap = new HashMap();

    if (bAllDistinct) {
      // There are no multiple same values in the passed map (without consedring
      // null)
      keySet = inMap.keySet();
      itKeyList = keySet.iterator();
      while (itKeyList.hasNext()) {
        key = itKeyList.next();
        value = inMap.get(key);

        if (value != null)
          hmTmpMap.put(value, key); // Prepare new temp HashMap with value=key
                                    // combination
        else
          hmNullValueMap.put(key, value); // Keep all null values in a new temp
                                          // Map
      }

      if (ascendingOrder != null && !ascendingOrder.booleanValue()) {
        // As it is descending order, Add Null Values in first place of the
        // LinkedHasMap
        sortedMap.putAll(hmNullValueMap);
      }

      // Put all not null values in returning LinkedHashMap
      for (int i = 0; i < valueList.size(); i++) {
        value = valueList.get(i);
        key = hmTmpMap.get(value);

        sortedMap.put(key, value);
      }

      if (ascendingOrder == null || ascendingOrder.booleanValue()) {
        // Add Null Values in the last of the LinkedHasMap
        sortedMap.putAll(hmNullValueMap);
      }
    } else {
      // There are some multiple values (with out considering null)
      keySet = inMap.keySet();
      itKeyList = keySet.iterator();
      while (itKeyList.hasNext()) {
        key = itKeyList.next();
        value = inMap.get(key);

        if (value != null)
          hmTmpMap.put(key, value); // Prepare new temp HashMap with key=value
                                    // combination
        else
          hmNullValueMap.put(key, value); // Keep all null values in a new temp
                                          // Map
      }

      if (ascendingOrder != null && !ascendingOrder.booleanValue()) {
        // As it is descending order, Add Null Values in first place of the
        // LinkedHasMap
        sortedMap.putAll(hmNullValueMap);
      }

      // Put all not null values in returning LinkedHashMap
      for (int i = 0; i < valueList.size(); i++) {
        sortedValue = valueList.get(i);

        // Search this value in temp HashMap and if found remove it
        keySet = hmTmpMap.keySet();
        itKeyList = keySet.iterator();
        while (itKeyList.hasNext()) {
          key = itKeyList.next();
          value = hmTmpMap.get(key);
          if (value.equals(sortedValue)) {
            sortedMap.put(key, value);
            hmTmpMap.remove(key);
            break;
          }
        }
      }

      if (ascendingOrder == null || ascendingOrder.booleanValue()) {
        // Add Null Values in the last of the LinkedHasMap
        sortedMap.putAll(hmNullValueMap);
      }
    }

    return sortedMap;
  }

  public static void main(String[] args) {
    HashMap hmValue = new HashMap();

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
    hmValue.put("CLE", "Carole Lee");
    hmValue.put("APE", "Annin Peck");
    hmValue.put("HUA", null);

    System.out.println("============ Before Sorting ===============");
    System.out.println(hmValue);

    // Call method to sort the hmValue Map for it's Values
    Map sortedMap = sortMapByValue(hmValue, false);

    System.out.println("============ After Sorting ===============");
    System.out.println(sortedMap);
  }
}
