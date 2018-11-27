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

package org.apache.opennlp.tagging_server;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

public class ModelUtil {

  private ModelUtil() {
  }
  
  public static ServiceReference getService(Class<?> serviceClazz) {
    Bundle bundle = FrameworkUtil.getBundle(ModelUtil.class);
    BundleContext context = bundle.getBundleContext();

    return context.getServiceReference(serviceClazz.getName());
  }
  
  @SuppressWarnings("unchecked")
  public static <T> T getModel(ServiceReference modelService, Class<T> modelClazz) {
    
    T model;
    if (modelService != null) {
      BundleContext context = modelService.getBundle().getBundleContext();
      model = (T) context.getService(modelService);
    }
    else {
      throw new RuntimeException("Model does not exist!");
    }
    
    return model;
  }

  public static void releaseService(ServiceReference service) {
    if (service != null) {
      BundleContext context = service.getBundle().getBundleContext();
      context.ungetService(service);
    }
  }
}
