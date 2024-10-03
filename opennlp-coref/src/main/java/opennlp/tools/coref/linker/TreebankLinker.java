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

package opennlp.tools.coref.linker;

import java.io.IOException;

import opennlp.tools.coref.mention.PTBMentionFinder;

/**
 * This class perform coreference for treebank style parses.
 * <p>
 * It will only perform coreference over constituents defined in the trees and
 * will not generate new constituents for pre-nominal entities or sub-entities in
 * simple coordinated noun phrases.
 * <p>
 * This linker requires that named-entity information also be provided.
 *
 * @see Linker
 * @see DefaultLinker
 */
public class TreebankLinker extends DefaultLinker {

  /**
   * Instantiates a {@link TreebankLinker} with the specified model directory,
   * running in the specified {@link LinkerMode mode} which uses a discourse model
   * based on the specified parameter.
   *
   * @param modelDir The directory in which the coref model files are located.
   * @param mode The {@link LinkerMode mode} that this linker is running in.
   * @throws IOException when the models can not be read or written to based on the mode.
   */
  public TreebankLinker(String modelDir, LinkerMode mode) throws IOException {
    this(modelDir, mode, true);
  }

  /**
   * Instantiates a {@link TreebankLinker} with the specified model directory,
   * running in the specified {@link LinkerMode mode} which uses a discourse model
   * based on the specified parameter.
   *
   * @param modelDir The directory in which the coref model files are located.
   * @param mode The {@link LinkerMode mode} that this linker is running in.
   * @param useDiscourseModel Whether the model should use a discourse model or not.
   * @throws IOException Thrown if the models can not be read or written to based on the mode.
   */
  public TreebankLinker(String modelDir, LinkerMode mode, boolean useDiscourseModel) throws IOException {
    this(modelDir, mode, useDiscourseModel, -1);
  }

  /**
   * Instantiates a {@link TreebankLinker} with the specified model directory,
   * running in the specified {@link LinkerMode mode} which uses a discourse model
   * based on the specified parameter.
   *
   * @param modelDir The directory in which the coref model files are located.
   * @param mode The {@link LinkerMode mode} that this linker is running in.
   * @param useDiscourseModel Whether the model should use a discourse model or not.
   * @param fixedNonReferentialProbability The probability which resolvers are
   *                                       required to exceed a positive coreference relationship.
   * @throws IOException Thrown if the models can not be read or written to based on the mode.
   */
  public TreebankLinker(String modelDir, LinkerMode mode, boolean useDiscourseModel,
                        double fixedNonReferentialProbability) throws IOException {
    super(modelDir, mode, useDiscourseModel,fixedNonReferentialProbability);
  }

  @Override
  protected void initMentionFinder() {
    mentionFinder = PTBMentionFinder.getInstance(headFinder);
  }
}
