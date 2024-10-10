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

package opennlp.tools.coref.mention;

/**
 * Finds mentions from shallow np-chunking based parses.
 */
public class ShallowParseMentionFinder extends AbstractMentionFinder {

  private static ShallowParseMentionFinder instance;

  private ShallowParseMentionFinder(HeadFinder hf) {
    headFinder = hf;
    collectPrenominalNamedEntities = true;
    collectCoordinatedNounPhrases = true;
  }

  /**
   * @param hf A valid {@link HeadFinder} to assign if no instance exists.
   * @return Retrieves the one and only existing instance.
   */
  public static ShallowParseMentionFinder getInstance(HeadFinder hf) {
    if (instance == null) {
      instance = new ShallowParseMentionFinder(hf);
    } else if (instance.headFinder != hf) {
      instance = new ShallowParseMentionFinder(hf);
    }
    return instance;
  }

}
