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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.uima.ResourceSpecifierFactory;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.SerialFormat;
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
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.swt.widgets.Composite;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientResponse;

public class DefaultCasDocumentProvider extends
        org.apache.uima.caseditor.editor.CasDocumentProvider {

  private static final int READ_TIMEOUT = 30000;
  
  private final Map<Object, PreferenceStore> tsPreferenceStores = new HashMap<>();
  
  private final Map<String, IPreferenceStore> sessionPreferenceStores = new HashMap<>();
  
  private static TypeSystemDescription createTypeSystemDescription(InputStream in) throws IOException {

    // Note:
    // Type System location is not set correctly,
    // resolving a referenced type system will fail

    XMLInputSource xmlTypeSystemSource = new XMLInputSource(in, new File(""));
    XMLParser xmlParser = UIMAFramework.getXMLParser();

    TypeSystemDescription typeSystemDescriptor;

    try {
      typeSystemDescriptor = (TypeSystemDescription) xmlParser.parse(xmlTypeSystemSource);
      typeSystemDescriptor.resolveImports();
    } catch (InvalidXMLException e) {
      throw new IOException(e);
    }

    return typeSystemDescriptor;
  }
  
  private static CAS createEmptyCAS(TypeSystemDescription typeSystem) {
    ResourceSpecifierFactory resourceSpecifierFactory = UIMAFramework
        .getResourceSpecifierFactory();
    TypePriorities typePriorities = resourceSpecifierFactory
        .createTypePriorities();

    FsIndexDescription indexDescriptor = new FsIndexDescription_impl();
    indexDescriptor.setLabel("TOPIndex");
    indexDescriptor.setTypeName("uima.cas.TOP");
    indexDescriptor.setKind(FsIndexDescription.KIND_SORTED);

    CAS cas;
    try {
      cas = CasCreationUtils.createCas(typeSystem, typePriorities,
          new FsIndexDescription[] { indexDescriptor });
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

      Client c = ClientBuilder.newClient();
      c.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
      WebTarget webResource = c.target(casInput.getServerUrl());

      // Note: The type system could be cached to avoid downloading it
      //       for every opened CAS, a time stamp can be used to detect
      //       if it has been changed or not.
      
      ClientResponse tsResponse = webResource.path("_typesystem")
              .request(MediaType.TEXT_XML)
              .header("Content-Type", MediaType.TEXT_XML)
              .get(ClientResponse.class);
      
      TypeSystemDescription tsDesc = null;
      try (InputStream tsIn = tsResponse.getEntityStream()) {
        tsDesc = createTypeSystemDescription(tsIn);
      }
      catch (IOException e) {
        // Failed to load ts
        e.printStackTrace();
        
        // TODO: Stop here, and display some kind of
        // error message to the user
      }

      // create an empty cas...
      CAS cas = createEmptyCAS(tsDesc);
      
      ClientResponse casResponse;
      casResponse = webResource.path(URLEncoder.encode(casInput.getName(), StandardCharsets.UTF_8))
          .request(MediaType.TEXT_XML)
          .header("Content-Type", MediaType.TEXT_XML)
          .get(ClientResponse.class);

      org.apache.uima.caseditor.editor.ICasDocument doc;
      try (InputStream casIn = casResponse.getEntityStream()) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IPath pluginStatePath = CorpusServerPlugin.getDefault().getStateLocation();
        IPath tempPath = pluginStatePath.append(casInput.getName()).addFileExtension(MediaType.TEXT_XML);
        IFile casFile = root.getFile(tempPath);
        casFile.setContents(casIn, IResource.FORCE, new NullProgressMonitor());
        doc = new DocumentUimaImpl(cas, casFile, SerialFormat.XMI.name());
      } catch (IOException e) {
        throw new RuntimeException(e.getLocalizedMessage(), e);
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
        
        Client c = ClientBuilder.newClient();
        c.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
        WebTarget webResource = c.target(casInput.getServerUrl());
        
        byte[] xmiBytes = outStream.toByteArray();
        
        String encodedCasId = URLEncoder.encode(casInput.getName(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename=\"" + encodedCasId + "\"";

        Response response = webResource.path(encodedCasId)
                .request(MediaType.TEXT_XML)
                .header("Content-Type", MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .header("Content-Disposition", contentDisposition)
                .header("Content-Length", xmiBytes.length)
                .put(Entity.entity(xmiBytes, MediaType.APPLICATION_OCTET_STREAM_TYPE));
        
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
        try (InputStream tsStoreIn = new ByteArrayInputStream(tsStoreString.getBytes(StandardCharsets.UTF_8))) {
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
      try (ByteArrayOutputStream tsStoreBytes = new ByteArrayOutputStream()) {
        tsStore.save(tsStoreBytes, "");
        IPreferenceStore store = CorpusServerPlugin.getDefault().getPreferenceStore();
        store.putValue(getTypeSystemId((CorpusServerCasEditorInput) element),
                tsStoreBytes.toString(StandardCharsets.UTF_8));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
