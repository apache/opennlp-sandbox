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

package opennlp.tools.apps.contentgen.multithreaded;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;

public class BingWebQueryRunnerThread extends BingQueryRunner implements Runnable{
	
	private String query;
	private List<HitBase> results= new ArrayList<HitBase>();
	public BingWebQueryRunnerThread(String Query){
		super();
		this.query=Query;
	}
	public void run(){
		results=runSearch(query);
		fireMyEvent(new MyEvent(this));
	}
	public List<HitBase> getResults() {
		return results;
	}
	
	public String getQuery() {
		return query;
	}
	
	// Create the listener list
    protected javax.swing.event.EventListenerList listenerList = new javax.swing.event.EventListenerList();
    // This method allows classes to register for MyEvents
    public void addMyEventListener(MyEventListener listener) {
        listenerList.add(MyEventListener.class, listener);
    }

    // This method allows classes to unregister for MyEvents
    public void removeMyEventListener(MyEventListener listener) {
        listenerList.remove(MyEventListener.class, listener);
    }

    void fireMyEvent(MyEvent evt) {
        Object[] listeners = listenerList.getListenerList();
        // Each listener occupies two elements - the first is the listener class
        // and the second is the listener instance
        for (int i = 0; i < listeners.length; i += 2) {
            if (listeners[i] == MyEventListener.class) {
                ((MyEventListener) listeners[i + 1]).MyEvent(evt);
            }
        }
    }
	

}
