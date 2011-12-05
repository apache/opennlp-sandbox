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

import org.apache.uima.cas.TypeSystem;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * A UIMA Type System type input dialog.
 */
public class TypeInputDialog extends InputDialog {
  
  // TODO: Dialog should integrate nicely with preference page ...
  // TODO: Dialog should show some kind of list with existing types ...
  
  public TypeInputDialog(Shell parent, final TypeSystem ts) {
    super(parent,"Add a type", "Type name:", "", new IInputValidator() {
      
      @Override
      public String isValid(String value) {
        
        String result = null;
        
        if (ts.getType(value) == null) {
          return "Type does not exist in type sysetm!";
        }
        
        return result;
      }
    });
  }
}