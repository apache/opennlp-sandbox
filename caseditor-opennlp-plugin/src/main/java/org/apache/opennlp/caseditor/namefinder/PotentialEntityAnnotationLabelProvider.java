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

package org.apache.opennlp.caseditor.namefinder;

import java.text.DecimalFormat;

import org.apache.opennlp.caseditor.PotentialAnnotation;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

public class PotentialEntityAnnotationLabelProvider implements ITableLabelProvider {

  private DecimalFormat df = new DecimalFormat("#.#");
  
  public void addListener(ILabelProviderListener listener) {
  }

  public void removeListener(ILabelProviderListener listener) {
  }

  public boolean isLabelProperty(Object element, String property) {
    return false;
  }

  public Image getColumnImage(Object element, int columnIndex) {
    return null;
  }

  public String getColumnText(Object element, int columnIndex) {
    String result = null;
    
    PotentialAnnotation entity = (PotentialAnnotation) element;
    
    if (columnIndex == 0) {
      if (entity.getConfidence() != null)
        result = df.format(entity.getConfidence() * 100);
      else
        result = "";
    }
    else if (columnIndex == 1) {
      result = entity.getEntityText();
    }
    else if (columnIndex == 2) {
      // TODO: Improve this ...
      if (entity.getType() != null && entity.getType() != null) {
        String parts[] = entity.getType().split("\\.");
        result = parts[parts.length - 1];
      } else
        result = "";
    }
    
    return result;
  }

  public void dispose() {
  }
}
