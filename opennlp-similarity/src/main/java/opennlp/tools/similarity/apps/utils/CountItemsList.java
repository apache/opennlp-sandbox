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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CountItemsList<E> extends ArrayList<E> {

  /**
	 * 
	 */
  private static final long serialVersionUID = 1L;

  // This is private. It is not visible from outside.
  private Map<E, Integer> count = new HashMap<E, Integer>();

  // There are several entry points to this class
  // this is just to show one of them.
  public boolean add(E element) {
    if (!count.containsKey(element)) {
      count.put(element, 1);
    } else {
      count.put(element, count.get(element) + 1);
    }
    return super.add(element);
  }

  // This method belongs to CountItemList interface ( or class )
  // to used you have to cast.
  public int getCount(E element) {
    if (!count.containsKey(element)) {
      return 0;
    }
    return count.get(element);
  }

  public List<E> getFrequentTags() {
    Map<E, Integer> sortedMap = ValueSortMap.sortMapByValue(count, false);
    List<E> vals = new ArrayList<E>(sortedMap.keySet());
    if (vals.size() > 3) {
      vals = vals.subList(0, 3);
    }
    return vals;
  }

}