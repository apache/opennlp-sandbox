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
/**
 * Class which models the number of an entity and the confidence of that association.
 *
 * @see NumberEnum
 */
public class Number {
  private final NumberEnum type;
  private final double confidence;

  public Number(NumberEnum type,double confidence) {
    this.type = type;
    this.confidence = confidence;
  }

  public NumberEnum getType() {
    return type;
  }

  public double getConfidence() {
    return confidence;
  }
}
