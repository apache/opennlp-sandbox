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

package opennlp.tools.coref.resolver;

import java.io.IOException;

import opennlp.tools.coref.mention.MentionContext;

/**
 * Implementation of non-referential classifier which uses a fixed-value threshold.
 *
 * @see NonReferentialResolver
 */
public class FixedNonReferentialResolver implements NonReferentialResolver {

  private final double nonReferentialProbability;

  public FixedNonReferentialResolver(double nonReferentialProbability) {
    this.nonReferentialProbability = nonReferentialProbability;
  }

  @Override
  public double getNonReferentialProbability(MentionContext mention) {
    return this.nonReferentialProbability;
  }

  @Override
  public void addEvent(MentionContext mention) {}

  @Override
  public void train() throws IOException {}
}
