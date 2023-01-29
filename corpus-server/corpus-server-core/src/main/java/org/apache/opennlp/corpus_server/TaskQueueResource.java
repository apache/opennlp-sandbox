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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.opennlp.corpus_server.taskqueue.TaskQueue;

public class TaskQueueResource {

  private TaskQueue queue;
  
  TaskQueueResource(TaskQueue queue) {
    
    if (queue == null) {
      throw new IllegalArgumentException("queue parameter must not be null!");
    }
    
    this.queue = queue;
  }
  
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("_nextTask")
  public String getNextTask() {
    return queue.nextTask();
  }
}
