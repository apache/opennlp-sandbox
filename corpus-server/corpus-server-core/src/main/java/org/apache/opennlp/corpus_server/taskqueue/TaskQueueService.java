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

package org.apache.opennlp.corpus_server.taskqueue;


// Note: If there are multiple task queues, updates might fail,
// because CAS was modified concurrently
// by two annotators

public interface TaskQueueService {
  
  /**
   * Creates a new task queue with the given name for the given corpus
   * and document query.
   * 
   * @param corpusId
   * @param queueId
   * @param query
   */
  void createTaskQueue(String queueId, String corpusId, String query);

  /**
   * Retrieves the task queue for the given queue id.
   * 
   * @param queueId
   * 
   * @return the task queue or null if it does not exist
   */
  TaskQueue getTaskQeue(String queueId);
}
