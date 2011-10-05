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

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
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

public class CorpusExplorerView extends ViewPart {

  private Composite explorerComposite;
  private Text serverUrl;
  private Text queryText;
  
  private TableViewer searchResultViewer;
  
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

    serverUrl.setText("http://localhost:8080/corpus-server/rest/corpora/wikinews");
    
    // Search field to view content of corpus, default initialized to hit
    // everything
    // create label
    Label queryLabel = new Label(explorerComposite, SWT.NONE);
    queryLabel.setText("Query");

    queryText = new Text(explorerComposite, SWT.BORDER);
    GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false)
        .applyTo(queryText);

    queryText.setText("*:*");
    
    // For testing:
    // Add a button for retrieval of cas ids ...
    Button queryServer = new Button(explorerComposite, SWT.BORDER);
    queryServer.setText("Query");
    GridDataFactory.swtDefaults().span(2, 1).align(SWT.FILL, SWT.CENTER)
        .grab(true, false).applyTo(queryServer);
    
    queryServer.addSelectionListener(new SelectionListener() {

      @Override
      public void widgetSelected(SelectionEvent event) {
        // get server url
        String serverPath = serverUrl.getText();
        
        // get query
        String query = queryText.getText();
        
        // execute query
        Client c = Client.create();
        
        WebResource r = c.resource(serverPath);
        
        ClientResponse response = r
                .path("_search")
                .queryParam("q", query)
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        
        JSONArray searchResult = response.getEntity(JSONArray.class);
        
        System.out.println("Status: " + response.getStatus());
        
        searchResultViewer.setItemCount(0);
        
        for (int i = 0; i < searchResult.length(); i++) {
            try {
//              System.out.println("Hit: " + searchResult.getString(i));
              
              searchResultViewer.add(searchResult.getString(i));
              // Write result into table ...
              
            } catch (JSONException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
        }
        
        // add result to list ...
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent event) {
        // TODO Auto-generated method stub

      }
    });

    
    // TODO: Transform this button into an action for the context menu ...
    
    Button openDocument = new Button(explorerComposite, SWT.BORDER);
    openDocument.setText("Open");
    GridDataFactory.swtDefaults().span(2, 1).align(SWT.FILL, SWT.CENTER)
        .grab(true, false).applyTo(queryServer);
    
    openDocument.addSelectionListener(new SelectionListener(){

      @Override
      public void widgetSelected(SelectionEvent event) {
    	  
    	  // open an editor
    	  // need an editor input for that
    	  // document provider can be choosen based on editor input? or how?
    	  
    	  IWorkbenchPage page = CorpusExplorerView.this.getSite().getPage();
    	  
    	  StructuredSelection selection = (StructuredSelection) searchResultViewer.getSelection();
    	  
    	  if (selection.isEmpty())
    	    return;
    	  
    	  String selectedCAS = (String) selection.getFirstElement();
    	  
    	  // Hard code it for now, lets work on retrieval code first ...
    	  IEditorInput input = new CorpusServerCasEditorInput(serverUrl.getText(), selectedCAS);
    	  
    	  try {
    	    page.openEditor(input, "org.apache.uima.caseditor.editor");
          } catch (PartInitException e) {
            e.printStackTrace();
          }
    	  
    	  // Need to register our content provider, should be possible to have multiple,
    	  // selection could be done based on editor input class ...
    	  
    	  // the editor has multiple content providers, and can choose one based
    	  // on the editor input ...
    	  // how to add all cas editors to an open with menu?
    	  
    	  // Document Provider ?!
    	  // url based input
    	  
      }
      
      @Override
      public void widgetDefaultSelected(SelectionEvent event) {
      }});
    
    // List with casIds in the corpus ... (might be later replaced with a title)
    // The table should later be virtual, and be able to scroll through very huge
    // lits of CASes ... might be connected to a repository with million of documents
    searchResultViewer = new TableViewer(explorerComposite);

    GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true)
        .span(2, 1).applyTo(searchResultViewer.getTable());

    searchResultViewer.setLabelProvider(new ITableLabelProvider() {

      @Override
      public void addListener(ILabelProviderListener arg0) {
        // TODO Auto-generated method stub
        
      }

      @Override
      public void dispose() {
        // TODO Auto-generated method stub
        
      }

      @Override
      public boolean isLabelProperty(Object arg0, String arg1) {
        // TODO Auto-generated method stub
        return false;
      }

      @Override
      public void removeListener(ILabelProviderListener arg0) {
        // TODO Auto-generated method stub
        
      }

      @Override
      public Image getColumnImage(Object arg0, int arg1) {
        // TODO Auto-generated method stub
        return null;
      }

      @Override
      public String getColumnText(Object arg0, int arg1) {
        return arg0.toString();
      }});
    
    // Need an action which can take a CAS id, as editor input ...
    // Write document provider, which can download as CAS, open it, and upload
    // it on save!

    // Context menu should have open action ...
  }

  @Override
  public void setFocus() {
    explorerComposite.setFocus();
  }
}
