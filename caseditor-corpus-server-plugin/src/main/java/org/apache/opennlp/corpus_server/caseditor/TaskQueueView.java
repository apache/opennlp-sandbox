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

package org.apache.opennlp.corpus_server.caseditor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.ViewPart;

/**
 * A task queue view to retrieve the next annotation task subject from the corpus server.
 */
public class TaskQueueView extends ViewPart {

  @Override
  public void createPartControl(Composite parent) {

    Label testView = new Label(parent, SWT.NONE);
    testView.setText("Test!");
    
    // User can select queue
    // Button for next cas (gets nexts and closes current one, if not saved user is asked for it)
    // Save will send CAS to server ?!

    // History view ... shows n last opened CASes ...
    // User can quickly re-open a CAS, in case he wants to change something ...
  }

  @Override
  public void setFocus() {
  }
}
