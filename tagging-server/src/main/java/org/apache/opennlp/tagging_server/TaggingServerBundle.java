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

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;

import org.glassfish.jersey.servlet.ServletContainer;

public class TaggingServerBundle implements BundleActivator {

  private BundleContext context;
  
  private ServiceTracker tracker;
  
  @Override
  public void start(BundleContext context) throws Exception {
    this.context = context;
    
    // Register the Jersey servlet
    this.tracker = new ServiceTracker(context, HttpService.class.getName(), null) {

      private HttpService httpService;

      @Override
      public Object addingService(ServiceReference serviceRef) {
        httpService = (HttpService)super.addingService(serviceRef);
         
        Dictionary<String, String> jerseyServletParams = new Hashtable<>();
        jerseyServletParams.put("javax.ws.rs.Application", TaggingServerApplication.class.getName());
        
        try {
          httpService.registerServlet("/rest", new ServletContainer(), jerseyServletParams, null);
          httpService.registerResources("/","/htmls",null);
        } catch (ServletException | NamespaceException e) {
          throw new RuntimeException(e);
        }
        
        return httpService;
      }

      @Override
      public void removedService(ServiceReference ref, Object service) {
        if (httpService == service) {

          httpService.unregister("/rest");
          httpService = null;
        }
        
        super.removedService(ref, service);
      }
    };

    this.tracker.open();
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    this.context = null;
    tracker.close();
  }
}
