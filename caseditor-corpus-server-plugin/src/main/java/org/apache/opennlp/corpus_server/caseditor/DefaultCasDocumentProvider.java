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

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import javax.ws.rs.core.MediaType;

import org.apache.uima.ResourceSpecifierFactory;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.caseditor.editor.AnnotationDocument;
import org.apache.uima.caseditor.editor.AnnotationStyle;
import org.apache.uima.caseditor.editor.AnnotationStyle.Style;
import org.apache.uima.caseditor.editor.DocumentFormat;
import org.apache.uima.caseditor.editor.DocumentUimaImpl;
import org.apache.uima.caseditor.editor.EditorAnnotationStatus;
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
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Composite;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class DefaultCasDocumentProvider extends
        org.apache.uima.caseditor.editor.CasDocumentProvider {

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
  protected IDocument createDocument(Object element) throws CoreException {
    
    if (element instanceof CorpusServerCasEditorInput) {
      
      // Note: We need to do some error handling here, how to report an error to
      //       the user if downloading the CAS fails?
      
      CorpusServerCasEditorInput casInput = (CorpusServerCasEditorInput) element;
      
      Client client = Client.create();
      
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
      
      ClientResponse casResponse = webResource
          .path(casInput.getName())
          .accept(MediaType.TEXT_XML)
          // TODO: How to fix this? Shouldn't accept do it?
          .header("Content-Type", MediaType.TEXT_XML)
          .get(ClientResponse.class);
      
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
      
      AnnotationDocument document = new AnnotationDocument();
      document.setDocument(doc);
      
      return document;
    }

    return null;
  }

  @Override
  protected void doSaveDocument(IProgressMonitor monitor, Object element, IDocument document,
          boolean overwrite) throws CoreException {
    
    // TODO: Add support to save changes back to the server, what to do, if there is already a newer version?
    //       A dialog could ask if it should be overwritten, or not.
    
  }

  @Override
  public AnnotationStyle getAnnotationStyle(Object element, Type type) {
    return new AnnotationStyle(type.getName(), Style.SQUIGGLES, Color.BLUE, 1);
  }

  @Override
  public void setAnnotationStyle(Object element, AnnotationStyle style) {
  }

  @Override
  protected Collection<String> getShownTypes(Object element) {
    return new ArrayList<String>();
  }
  
  @Override
  protected void addShownType(Object element, Type type) {
    // Note: Should be local ...
  }
  
  @Override
  protected void removeShownType(Object element, Type type) {
    // Note: Should be local ...
  }
  
  @Override
  protected EditorAnnotationStatus getEditorAnnotationStatus(Object element) {
    return new EditorAnnotationStatus(CAS.TYPE_NAME_ANNOTATION, null, CAS.NAME_DEFAULT_SOFA);
  }

  @Override
  protected void disposeElementInfo(Object element, ElementInfo info) {
  }

  @Override
  public Composite createTypeSystemSelectorForm(ICasEditor arg0,
      Composite arg1, IStatus arg2) {
    
    // should not be needed, we can always provide a type sytem, and
    // if not, we can only show an error message!
    
    return null;
  }

  @Override
  protected void setEditorAnnotationStatus(Object arg0,
      EditorAnnotationStatus arg1) {
    
  }
}
