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

package org.apache.opennlp.caseditor.util;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.ConstraintFactory;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.FSTypeConstraint;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;

public class UIMAUtil {

  public static String[] split(String parameter, char splitChar) {
    
    String parts[] = parameter.split(Character.toString(splitChar));
    
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    
    return parts;
  }
  
  // TODO: Should throw an exception
  public static Type[] splitTypes(String typeList, char splitChar, TypeSystem typeSystem) {
    String typeNames[] = split(typeList, splitChar);
    
    Type types[] = new Type[typeNames.length];
    
    for (int i = 0; i < typeNames.length; i++) {
      types[i] = typeSystem.getType(typeNames[i]);
      
      if (types[i] == null) {
        return null; // TODO: Throw an exception instead!
      }
    }
    
    return types;
  }
  
  public static FSIterator<AnnotationFS> createMultiTypeIterator(CAS cas, Type... types) {
    
    if (types.length == 0)
      throw new IllegalArgumentException("Need at least one type to create an iterator!");
    
    ConstraintFactory cf = ConstraintFactory.instance();
    FSIterator<AnnotationFS> iterator = cas.getAnnotationIndex().iterator();

    FSTypeConstraint typeConstraint = cf.createTypeConstraint();
    
    for (Type type : types) {
      typeConstraint.add(type);
    }

    // Create and use the filtered iterator
    FSIterator<AnnotationFS> filteredIterator = cas.createFilteredIterator(iterator, typeConstraint);
    
    return filteredIterator;
  }
}
