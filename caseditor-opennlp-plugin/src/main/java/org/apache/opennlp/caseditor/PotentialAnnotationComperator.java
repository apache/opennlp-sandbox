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

package org.apache.opennlp.caseditor;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;

/**
 * Compares two entities. Entities which have a smaller begin index are ordered first.
 * If entities have an identical begin index the one with the higher confidence score
 * is ordered first.
 */
public class PotentialAnnotationComperator extends ViewerComparator {

  @Override
  public int compare(Viewer viewer, Object o1, Object o2) {
    
    PotentialAnnotation e1 = (PotentialAnnotation) o1;
    PotentialAnnotation e2 = (PotentialAnnotation) o2;
    
    int diff = e1.getBeginIndex() - e2.getBeginIndex();
    
    if (diff == 0) {
      if (e1.getConfidence() - e2.getConfidence() < 0) {
        return -1;
      }
      else {
        return 1;
      }
    }
    
    return diff;
  }
}
