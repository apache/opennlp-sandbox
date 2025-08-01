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

package opennlp.tools.coref.sim;

import java.io.IOException;

/**
 * Interface for training a similarity, gender, or number model.
 */
public interface TrainModel<T>{

  String MODEL_EXTENSION = ".bin";

  T trainModel() throws IOException;
  
  /**
   * Creates similarity training pairs based on the specified extents.
   * Extents are considered compatible if they are in the same coreference chain,
   * have the same named-entity tag, or share a common head word.
   * <p>
   * Incompatible extents are chosen at random from the set of extents which don't meet these criteria.
   *
   * @param extents The {@link Context extents} to set and process.
   */
  void setExtents(Context[] extents);
}
