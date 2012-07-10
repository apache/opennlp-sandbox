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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

public class ModelUtil {

  // TODO: Loading from a ULR (file, http) should be possible
  //       if the model is already loaded a time stamp should be
  //       used to detect an updated model, if model is updated,
  //       load it and replace the old one!
  
  public static InputStream openModelIn(String modelPath) throws IOException {
    InputStream modelIn = null;
    
    if (modelPath.startsWith("http://") || modelPath.startsWith("file://")) {
      URL modelURL = new URL(modelPath);
      
      modelIn = modelURL.openStream();
    }
    else {
      IResource modelResource = ResourcesPlugin.getWorkspace().
          getRoot().findMember(modelPath);
      
      if (modelResource instanceof IFile) {
        IFile modelFile = (IFile) modelResource;
        try {
          modelIn = modelFile.getContents();
        } catch (CoreException e) {
          throw new IOException(e.getMessage());
        }
      }
    }
    
    if (modelIn == null) {
      throw new IOException("Model path is incorrect!");
    }
    
    return modelIn;
  }
}
