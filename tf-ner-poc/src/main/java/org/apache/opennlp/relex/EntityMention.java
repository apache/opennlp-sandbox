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

package org.apache.opennlp.relex;

import java.util.Objects;

import opennlp.tools.parser.Parse;

public class EntityMention {

  private final Parse parse;
  private final int entityId;

  public EntityMention(Parse parse, int entityId) {
    this.parse = Objects.requireNonNull(parse);
    this.entityId = entityId;
  }

  public Parse getParse() {
    return parse;
  }

  public int getEntityId() {
    return entityId;
  }
}
