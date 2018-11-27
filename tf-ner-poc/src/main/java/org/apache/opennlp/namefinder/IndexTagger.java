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

package org.apache.opennlp.namefinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IndexTagger {

  private Map<Integer, String> idx2Tag = new HashMap<>();

  public IndexTagger(InputStream vocabTags) throws IOException {
    try(BufferedReader in = new BufferedReader(
            new InputStreamReader(
                    vocabTags, "UTF8"))) {
      String tag;
      int idx = 0;
      while ((tag = in.readLine()) != null) {
        idx2Tag.put(idx, tag);
        idx += 1;
      }
    }

  }

  public String getTag(Integer idx) {
    return idx2Tag.get(idx);
  }

  public Map<Integer, String> getIdx2Tag() {
    return Collections.unmodifiableMap(idx2Tag);
  }

  public int getNumberOfTags() {
    return idx2Tag.size();
  }

}
