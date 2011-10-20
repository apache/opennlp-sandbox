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

package opennlp.tools.textsimilarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class GeneralizationListReducer {
  public List<ParseTreeChunk> applyFilteringBySubsumption_OLD(
      List<ParseTreeChunk> result) {
    List<ParseTreeChunk> resultDupl = new ArrayList<ParseTreeChunk>();
    resultDupl.addAll(new HashSet<ParseTreeChunk>(result));
    result = resultDupl;
    if (result.size() < 2)
      return result; // nothing to reduce
    List<ParseTreeChunk> resultReduced = new ArrayList<ParseTreeChunk>();
    int size = result.size();
    for (int i = 0; i < size; i++) {
      Boolean bSubChunk = false;
      for (int j = 0; j < size; j++) {
        if (i == j) {
          continue;
        }
        if (result.get(j).isASubChunk(result.get(i))) {
          bSubChunk = true;
        }
      }
      if (!bSubChunk)
        resultReduced.add(result.get(i));
    }

    if (resultReduced.size() < 1) {
      System.err.println("Wrong subsumption reduction");
    }

    if (resultReduced.size() > 1) {
      int z = 0;
      z++;
    }
    return resultReduced;

  }

  public List<ParseTreeChunk> applyFilteringBySubsumptionOLD(
      List<ParseTreeChunk> result) {
    List<ParseTreeChunk> resultDupl = null;
    if (result.size() < 2)
      return result; // nothing to reduce
    List<ParseTreeChunk> resultReduced = new ArrayList<ParseTreeChunk>();
    int size = result.size();
    resultDupl = new ArrayList<ParseTreeChunk>(result);
    for (int s = 0; s < size; s++) {
      for (int i = 0; i < resultDupl.size(); i++) {
        Boolean bStop = false;
        for (int j = 0; j < resultDupl.size(); j++) {
          if (i == j) {
            continue;
          }
          if (result.get(j).isASubChunk(result.get(i))
              && !result.get(i).isASubChunk(result.get(j))) {
            resultDupl.remove(i);
            bStop = true;
            break;
          }
        }
        if (bStop) {
          break;
        }
      }
    }
    resultReduced = resultDupl;
    if (resultReduced.size() < 1) {
      System.err.println("Wrong subsumption reduction");
    }

    if (resultReduced.size() > 1) {
      int z = 0;
      z++;
    }
    return resultReduced;

  }

  public List<ParseTreeChunk> applyFilteringBySubsumption(
      List<ParseTreeChunk> result) {
    List<Integer> resultDuplIndex = new ArrayList<Integer>();
    List<ParseTreeChunk> resultReduced = new ArrayList<ParseTreeChunk>();

    if (result.size() < 2) {
      return result; // nothing to reduce
    }
    // remove empty
    for (ParseTreeChunk ch : result) {
      if (ch.getLemmas().size() > 0) {
        resultReduced.add(ch);
      }
    }
    result = resultReduced;

    for (int i = 0; i < result.size(); i++) {
      for (int j = i + 1; j < result.size(); j++) {
        if (i == j) {
          continue;
        }
        if (result.get(j).isASubChunk(result.get(i))) {
          resultDuplIndex.add(i);
        } else if (result.get(i).isASubChunk(result.get(j))) {
          resultDuplIndex.add(j);
        }
      }

    }
    resultReduced = new ArrayList<ParseTreeChunk>();
    for (int i = 0; i < result.size(); i++) {
      if (!resultDuplIndex.contains(i)) {
        resultReduced.add(result.get(i));
      }
    }

    if (resultReduced.size() < 1) {
      System.err.println("Wrong subsumption reduction");
      resultReduced = result;
    }

    return resultReduced;

  }

  // testing sub-chunk functionality and
  // elimination more general according to subsumption relation

}
