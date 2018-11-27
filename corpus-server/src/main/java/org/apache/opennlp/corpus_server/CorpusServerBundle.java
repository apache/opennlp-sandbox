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

package org.apache.opennlp.corpus_server;

import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;

import com.sun.jersey.spi.container.servlet.ServletContainer;

public class CorpusServerBundle implements BundleActivator {

  private static CorpusServerBundle instance;
  
  private ServiceReference corpusServerServiceReference;
  private CorpusServer corpusServer;

  private ServiceTracker tracker;
  
  @Override
  public void start(final BundleContext context) throws Exception {
    instance = this;
    
    // Registers a service listener to listen for a corpus server implementation.
    // The first implementation which can be found will be used. All other implementations
    // which could be there as well will be ignored.
    
    context.getServiceReference(CorpusServer.class.getName());

    ServiceListener sl = new ServiceListener() {
      public void serviceChanged(ServiceEvent ev) {

        switch (ev.getType()) {
        case ServiceEvent.REGISTERED: {

          if (corpusServer == null) {
            System.out.println("Registered a Corpus Server implementation!");

            corpusServerServiceReference = ev.getServiceReference();
            corpusServer = (CorpusServer) context
                .getService(corpusServerServiceReference);
          }
        }
          break;
        case ServiceEvent.UNREGISTERING: {

          if (ev.getServiceReference().equals(corpusServerServiceReference)) {
            System.out.println("Unregistered Corpus Server implementation!");

            context.ungetService(corpusServerServiceReference);
            corpusServerServiceReference = null;
            corpusServer = null;
          }
        }
          break;
        }
      }
    };

    String filter = "(objectclass=" + CorpusServer.class.getName() + ")";
    
    try {
      context.addServiceListener(sl, filter);
      ServiceReference[] srl = context.getServiceReferences(null, filter);
      for (int i = 0; srl != null && i < srl.length; i++) {
        sl.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, srl[i]));
      }
    } catch (InvalidSyntaxException e) {
      e.printStackTrace();
      // TODO: Log error
    }
    
    // Register the Jersey servlet
    this.tracker = new ServiceTracker(context, HttpService.class.getName(), null) {

      private HttpService httpService;

      @Override
      public Object addingService(ServiceReference serviceRef) {
        httpService = (HttpService)super.addingService(serviceRef);
         
        Dictionary<String, String> jerseyServletParams = new Hashtable<String, String>();
        jerseyServletParams.put("javax.ws.rs.Application", CorpusServerApplication.class.getName());
        jerseyServletParams.put("com.sun.jersey.api.json.POJOMappingFeature", "true");
        
        try {
          httpService.registerServlet("/rest", new ServletContainer(), jerseyServletParams, null);
        } catch (ServletException e) {
          throw new RuntimeException(e);
        } catch (NamespaceException e) {
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

  public static CorpusServerBundle getInstance() {
    return instance;
  }

  public CorpusServer getCorpusServer() {
    return corpusServer;
  }
  
  @Override
  public void stop(BundleContext context) throws Exception {
    instance = null;
    
    if (corpusServerServiceReference != null) {
      context.ungetService(corpusServerServiceReference);
    }
    
    tracker.close();
  }
}
