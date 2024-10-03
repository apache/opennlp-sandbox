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

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.HeadFinder;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.MentionFinder;

/** 
 * A linker provides an interface for finding mentions, {@link #getMentionFinder getMentionFinder},
 * and creating entities out of those mentions, {@link #getEntities getEntities}.  This interface also allows
 * for the training of a resolver with the method {@link #setEntities setEntitites} which is used to give the
 * resolver mentions whose entityId fields indicate which mentions refer to the same entity and the
 * {@link #train train} method which compiles all the information provided via calls to
 * {@link #setEntities setEntities} into a model.
 */
public interface Linker {


  /** 
   * String constant used to label a mention which is a description.
   */
  String DESCRIPTOR = "desc";
  
  /** 
   * String constant used to label a mention in an appositive relationship.
   */
  String ISA = "isa";
  
  /** 
   * String constant used to label a mention which consists of two or more noun phrases.
   */
  String COMBINED_NPS = "cmbnd";
  
  /** 
   * String constant used to label a mention which consists of a single noun phrase.
   */
  String NP = "np";
  
  /** 
   * String constant used to label a mention which is a proper noun modifying another noun.
   */
  String PROPER_NOUN_MODIFIER = "pnmod";
  
  /** 
   * String constant used to label a mention which is a pronoun.
   */
  String PRONOUN_MODIFIER = "np";

 
  /**
   * Indicated that the specified mentions can be used to train this linker.
   * This requires that the coreference relationship between the mentions have been labeled
   * in the mention's id field.
   * 
   * @param mentions The mentions to be used to train the linker.
   */
  void setEntities(Mention[] mentions);

  /**
   * Returns a list of entities which group the mentions into entity classes.
   * @param mentions An array of mentions.
   * 
   * @return An array of discourse entities.
   */
  DiscourseEntity[] getEntities(Mention[] mentions);

  /**
   * Creates mention contexts for the specified mention exents.
   * These are used to compute coreference features over.
   *
   * @param mentions The mention of a document.
   * 
   * @return mention contexts for the specified mention exents.
   */
  MentionContext[] constructMentionContexts(Mention[] mentions);

  /** 
   * Trains the linker based on the data specified via calls to {@link #setEntities}.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  void train() throws IOException;

  /**
   * Returns the mention finder for this linker.
   * This can be used to get the mentions of a Parse.
   * 
   * @return The object which finds mentions for this linker.
   */
  MentionFinder getMentionFinder();

  /**
   * Returns the head finder associated with this linker.
   * 
   * @return The head finder associated with this linker.
   */
  HeadFinder getHeadFinder();
}
