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

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

/**
 * A task queue view to retrieve the next annotation task subject from the corpus server.
 */
public class TaskQueueView extends ViewPart {

  private Composite explorerComposite;
  private Text serverUrl;
  
  private TableViewer historyViewer;
  
  @Override
  public void createPartControl(Composite parent) {

    explorerComposite = new Composite(parent, SWT.NONE);
    GridLayout explorerLayout = new GridLayout();
    explorerLayout.numColumns = 2;
    explorerComposite.setLayout(explorerLayout);

    // URL field to connect to corpus server and corpus
    Label serverLabel = new Label(explorerComposite, SWT.NONE);
    serverLabel.setText("Server:");

    serverUrl = new Text(explorerComposite, SWT.BORDER);
    GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false)
        .applyTo(serverUrl);
    
    // Button for next cas (gets nexts and closes current one,
    // if not saved user is asked for it)
    Button nextDocument = new Button(explorerComposite, SWT.BORDER);
    nextDocument.setText("Next");
    GridDataFactory.swtDefaults().span(2, 1).align(SWT.FILL, SWT.CENTER)
        .grab(true, false).applyTo(nextDocument);
    
    nextDocument.addSelectionListener(new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent event) {

        // get next cas id ...
        String casId = "President_of_China_lunches_with_Brazilian_President.xmi";
        
        IWorkbenchPage page = TaskQueueView.this.getSite().getPage();
        
        IEditorInput input = new CorpusServerCasEditorInput(
            serverUrl.getText(), casId);

        try {
          page.openEditor(input, "org.apache.uima.caseditor.editor");
        } catch (PartInitException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent event) {
      }
    });
    
    // History view ... shows n last opened CASes ...
    historyViewer = new TableViewer(explorerComposite);

    GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true)
        .span(2, 1).applyTo(historyViewer.getTable());
    
    // User should be able to open cas from list ...
  }

  @Override
  public void setFocus() {
  }
}
