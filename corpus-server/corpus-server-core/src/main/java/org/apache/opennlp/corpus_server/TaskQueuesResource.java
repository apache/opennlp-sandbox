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

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.opennlp.corpus_server.taskqueue.TaskQueueService;

@Path("/queues")
public class TaskQueuesResource {

  @POST
  @Consumes(MediaType.TEXT_XML)
  @Path("_createTaskQueue")
  public void createTaskQueue(@QueryParam("queueId") String queueId, @QueryParam("corpusId")String corpusId, @QueryParam("q") String q) {
    TaskQueueService taskQueueService = CorpusServerBundle.getInstance().getCorpusServer().getTaskQueueService();

    taskQueueService.createTaskQueue(queueId, corpusId, q);
  }
  
  @Path("{queue}")
  public TaskQueueResource getTaskQueue(@PathParam("queue") String queueId) {
    TaskQueueService taskQueueService = CorpusServerBundle.getInstance().getCorpusServer().getTaskQueueService();
    
    return new TaskQueueResource(taskQueueService.getTaskQueue(queueId));
  }
}
