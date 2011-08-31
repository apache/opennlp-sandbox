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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * A task queue view to retrieve the next annotation task subject from the corpus server.
 */
public class TaskQueueView extends ViewPart {

  private Composite explorerComposite;
  private Text serverUrl;
  
  private TableViewer historyViewer;
  
  private List<IEditorInput> lastInputElements = new ArrayList<IEditorInput>();
      
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
    
    // TOOD: Should be stored in some way, or just done more sophisticated ..
    serverUrl.setText("http://localhost:8080/corpus-server/rest/queues/ObamaNerTask");
    
    // Button for next cas (gets nexts and closes current one,
    // if not saved user is asked for it)
    Button nextDocument = new Button(explorerComposite, SWT.BORDER);
    nextDocument.setText("Next");
    GridDataFactory.swtDefaults().span(2, 1).align(SWT.FILL, SWT.CENTER)
        .grab(true, false).applyTo(nextDocument);
    
    nextDocument.addSelectionListener(new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent event) {

        Client c = Client.create();
        
        WebResource queueWebResource = c.resource(serverUrl.getText());
        
        ClientResponse response2 = queueWebResource
            .path("_nextTask")
            .accept(MediaType.APPLICATION_JSON)
            .header("Content-Type", MediaType.TEXT_XML)
            .get(ClientResponse.class);
        
        String casId = response2.getEntity(String.class);
        
        // How to get the corpus uri for the item returned from the queue ???
        // Queue could always return full URI ...
        
        // we also need to corpus the cas id belongs too ...
        IWorkbenchPage page = TaskQueueView.this.getSite().getPage();
        
        // TODO: Thats a short cut, we need to make this work properly ...
        IEditorInput input = new CorpusServerCasEditorInput(
            "http://localhost:8080/corpus-server/rest/corpora/wikinews", casId);

        try {
          page.openEditor(input, "org.apache.uima.caseditor.editor");
        } catch (PartInitException e) {
          e.printStackTrace();
        }
        
        // Add casId to historyViewer ... should be inserted at the top, not bottom.
        
        lastInputElements.add(input);
        historyViewer.insert(input, 0);
        
        if (lastInputElements.size() > 10) {
          IEditorInput tooOldInput = lastInputElements.remove(0);
          historyViewer.remove(tooOldInput);
        }
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent event) {
      }
    });
    
    // History view ... shows n last opened CASes ...
    historyViewer = new TableViewer(explorerComposite);
    historyViewer.setLabelProvider(new ITableLabelProvider() {

      @Override
      public void addListener(ILabelProviderListener arg0) {
      }

      @Override
      public void dispose() {
      }

      @Override
      public boolean isLabelProperty(Object arg0, String arg1) {
        return false;
      }

      @Override
      public void removeListener(ILabelProviderListener arg0) {
      }

      @Override
      public Image getColumnImage(Object arg0, int arg1) {
        return null;
      }

      @Override
      public String getColumnText(Object arg0, int arg1) {
        return ((IEditorInput) arg0).getName();
      }});
    
      GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true)
        .span(2, 1).applyTo(historyViewer.getTable());
    
      historyViewer.addOpenListener(new IOpenListener() {
        
        @Override
        public void open(OpenEvent event) {
          
          StructuredSelection selection = (StructuredSelection) event.getSelection();
          
          if (!selection.isEmpty()) {
            IWorkbenchPage page = TaskQueueView.this.getSite().getPage();
            
            IEditorInput input = (IEditorInput) selection.getFirstElement();

            try {
              page.openEditor(input, "org.apache.uima.caseditor.editor");
            } catch (PartInitException e) {
              e.printStackTrace();
            }
          }
        }
      });
  }

  @Override
  public void setFocus() {
  }
}
