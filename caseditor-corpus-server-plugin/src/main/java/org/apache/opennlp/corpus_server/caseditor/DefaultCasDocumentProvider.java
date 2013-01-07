/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.opennlp.corpus_server.caseditor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.uima.ResourceSpecifierFactory;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.caseditor.editor.DocumentFormat;
import org.apache.uima.caseditor.editor.DocumentUimaImpl;
import org.apache.uima.caseditor.editor.ICasDocument;
import org.apache.uima.caseditor.editor.ICasEditor;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.FsIndexDescription;
import org.apache.uima.resource.metadata.TypePriorities;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.resource.metadata.impl.FsIndexDescription_impl;
import org.apache.uima.util.CasCreationUtils;
import org.apache.uima.util.InvalidXMLException;
import org.apache.uima.util.XMLInputSource;
import org.apache.uima.util.XMLParser;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.swt.widgets.Composite;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class DefaultCasDocumentProvider extends
        org.apache.uima.caseditor.editor.CasDocumentProvider {

  private static final int READ_TIMEOUT = 30000;
  
  private Map<Object, PreferenceStore> tsPreferenceStores =
      new HashMap<Object, PreferenceStore>();
  
  private Map<String, IPreferenceStore> sessionPreferenceStores = new HashMap<String, IPreferenceStore>();
  
  private static TypeSystemDescription createTypeSystemDescription(InputStream in) throws IOException {

    // Note:
    // Type System location is not set correctly,
    // resolving a referenced type system will fail

    XMLInputSource xmlTypeSystemSource = new XMLInputSource(in, new File(""));

    XMLParser xmlParser = UIMAFramework.getXMLParser();

    TypeSystemDescription typeSystemDesciptor;

    try {
      typeSystemDesciptor = (TypeSystemDescription) xmlParser
          .parse(xmlTypeSystemSource);

      typeSystemDesciptor.resolveImports();
    } catch (InvalidXMLException e) {
      throw new IOException(e);
    }

    return typeSystemDesciptor;
  }
  
  private static CAS createEmptyCAS(TypeSystemDescription typeSystem) {
    ResourceSpecifierFactory resourceSpecifierFactory = UIMAFramework
        .getResourceSpecifierFactory();
    TypePriorities typePriorities = resourceSpecifierFactory
        .createTypePriorities();

    FsIndexDescription indexDesciptor = new FsIndexDescription_impl();
    indexDesciptor.setLabel("TOPIndex");
    indexDesciptor.setTypeName("uima.cas.TOP");
    indexDesciptor.setKind(FsIndexDescription.KIND_SORTED);

    CAS cas;
    try {
      cas = CasCreationUtils.createCas(typeSystem, typePriorities,
          new FsIndexDescription[] { indexDesciptor });
    } catch (ResourceInitializationException e) {
      e.printStackTrace();
      cas = null;
    }

    return cas;
  }
  
  @Override
  protected ICasDocument createDocument(Object element) throws CoreException {
    
    if (element instanceof CorpusServerCasEditorInput) {
      
      // Note: We need to do some error handling here, how to report an error to
      //       the user if downloading the CAS fails?
      
      CorpusServerCasEditorInput casInput = (CorpusServerCasEditorInput) element;
      
      Client client = Client.create();
      client.setReadTimeout(READ_TIMEOUT);
      WebResource webResource = client.resource(casInput.getServerUrl());
      
      // Note: The type system could be cached to avoid downloading it
      //       for every opened CAS, a time stamp can be used to detect
      //       if it has been changed or not.
      
      ClientResponse tsResponse = webResource
              .path("_typesystem")
              .accept(MediaType.TEXT_XML)
              // TODO: How to fix this? Shouldn't accept do it?
              .header("Content-Type", MediaType.TEXT_XML)
              .get(ClientResponse.class);
      
      InputStream tsIn = tsResponse.getEntityInputStream();
      TypeSystemDescription tsDesc = null;
      
      try {
        tsDesc = createTypeSystemDescription(tsIn);
      }
      catch (IOException e) {
        // Failed to load ts
        e.printStackTrace();
        
        // TODO: Stop here, and display some kind of
        // error message to the user
      }
      finally {
        try {
          tsIn.close();
        } catch (IOException e) {
        }
      }
      // create an empty cas ..
      CAS cas = createEmptyCAS(tsDesc);
      
      ClientResponse casResponse;
      try {
        casResponse = webResource
            .path(URLEncoder.encode(casInput.getName(), "UTF-8"))
            .accept(MediaType.TEXT_XML)
            // TODO: How to fix this? Shouldn't accept do it?
            .header("Content-Type", MediaType.TEXT_XML)
            .get(ClientResponse.class);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("Should never fail, UTF-8 encoding is available on every JRE!", e);
      }
      
      InputStream casIn = casResponse.getEntityInputStream();
      
      org.apache.uima.caseditor.editor.ICasDocument doc = null;
      
      try {
        doc = new DocumentUimaImpl(cas, casIn, DocumentFormat.XMI);
      }
      // TODO: Catch exception here, and display error message?!
      finally {
        try {
          casIn.close();
        } catch (IOException e) {
        }
      }
      
      return doc;
    }

    return null;
  }

  @Override
  protected void doSaveDocument(IProgressMonitor monitor, Object element, ICasDocument document,
          boolean overwrite) throws CoreException {
    
    if (element instanceof CorpusServerCasEditorInput) {
      
      CorpusServerCasEditorInput casInput = (CorpusServerCasEditorInput) element;

      // TODO: What to do if there is already a newer version?
      //       A dialog could ask if it should be overwritten, or not.
      
      if (document instanceof DocumentUimaImpl) {
        
        DocumentUimaImpl documentImpl = (DocumentUimaImpl) document;
        
        ByteArrayOutputStream outStream = new ByteArrayOutputStream(40000); 
        documentImpl.serialize(outStream);
        
        Client client = Client.create();
        client.setReadTimeout(READ_TIMEOUT);
        WebResource webResource = client.resource(casInput.getServerUrl());
        
        byte xmiBytes[] = outStream.toByteArray();
        
        String encodedCasId;
        try {
          encodedCasId = URLEncoder.encode(casInput.getName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
          throw new CoreException(new Status(Status.ERROR, CorpusServerPlugin.PLUGIN_ID,
              "Severe error, should never happen, UTF-8 encoding is not supported!"));
        }
        
        ClientResponse response = webResource
                .path(encodedCasId)
                .accept(MediaType.TEXT_XML)
                // TODO: How to fix this? Shouldn't accept do it?
                .header("Content-Type", MediaType.TEXT_XML)
                .put(ClientResponse.class, xmiBytes);
        
        if (response.getStatus() != 204) {
          throw new CoreException(new Status(Status.ERROR, CorpusServerPlugin.PLUGIN_ID,
              "Failed to save document, http error code: " + response.getStatus()));
        }
      }
    }

    // tell everyone that the element changed and is not dirty any longer
    fireElementDirtyStateChanged(element, false);
  }

  private String getTypeSystemId(CorpusServerCasEditorInput input) {
    return input.getServerUrl();
  }
  
  
  @Override
  public IPreferenceStore getSessionPreferenceStore(Object element) {
      
    // lookup one, and if it does not exist create a new one, and put it!
    IPreferenceStore store = sessionPreferenceStores.get(getTypeSystemId((CorpusServerCasEditorInput) element));
      
    if (store == null) {
      store = new PreferenceStore();
      sessionPreferenceStores.put(getTypeSystemId((CorpusServerCasEditorInput) element), store);
    }

    return store;
  }

  @Override
  protected void disposeElementInfo(Object element, ElementInfo info) {
  }

  @Override
  public Composite createTypeSystemSelectorForm(ICasEditor editor,
      Composite arg1, IStatus arg2) {
    
    // Should not be needed, we can always provide a type system, and
    // if not, we can only show an error message!
    
    return null;
  }

  @Override
  public IPreferenceStore getTypeSystemPreferenceStore(Object element) {
    
    PreferenceStore tsStore = tsPreferenceStores.get(element);
    
    if (tsStore == null) {
      
      IPreferenceStore store = CorpusServerPlugin.getDefault().getPreferenceStore();
      
      String tsStoreString = store.getString(getTypeSystemId((CorpusServerCasEditorInput) element));
      
      tsStore = new PreferenceStore();
      
      if (tsStoreString.length() != 0) { 
        InputStream tsStoreIn = new ByteArrayInputStream(tsStoreString.getBytes(Charset.forName("UTF-8")));
        
        try {
          tsStore.load(tsStoreIn);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      
      tsPreferenceStores.put(element, tsStore);
    }
    
    
    return tsStore;
  }

  @Override
  public void saveTypeSystemPreferenceStore(Object element) {
    
    PreferenceStore tsStore = tsPreferenceStores.get(element);
    
    if (tsStore != null) {
      ByteArrayOutputStream tsStoreBytes = new ByteArrayOutputStream();
      try {
        tsStore.save(tsStoreBytes, "");
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      
      IPreferenceStore store = CorpusServerPlugin.getDefault().getPreferenceStore();
      store.putValue(getTypeSystemId((CorpusServerCasEditorInput) element), 
          new String(tsStoreBytes.toByteArray(), Charset.forName("UTF-8")));
    }
    
  }
}
