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

import java.util.List;

import opennlp.tools.util.Span;

/** 
 * Interface for syntactic and named-entity information to be used in coreference
 * annotation.
 */
public interface Parse extends Comparable<Parse> {

  /**
   * Returns the index of the sentence which contains this parse.
   * 
   * @return The index of the sentence which contains this parse.
   */
  int getSentenceNumber();

  /** 
   * Returns a list of the all noun phrases
   * contained by this parse.  The noun phrases in this list should
   * also implement the {@link Parse} interface.
   * 
   * @return a list of all the noun phrases contained by this parse.
   */
  List<Parse> getNounPhrases();

  /** 
   * Returns a list of all the named entities
   * contained by this parse.  The named entities in this list should
   * also implement the {@link Parse} interface.
   * 
   * @return a list of all the named entities contained by this parse. */
  List<Parse> getNamedEntities();

  /** 
   * Returns a list of the children to this object. The
   * children should also implement the {@link Parse} interface
   * .
   * @return a list of the children to this object.
   * */
  List<Parse> getChildren();

  /**
   * Returns a list of the children to this object which are constituents or tokens.  The
   * children should also implement the {@link Parse} interface. This allows
   * implementations which contain addition nodes for things such as semantic categories to
   * hide those nodes from the components which only care about syntactic nodes.
   * 
   * @return a list of the children to this object which are constituents or tokens.
   */
  List<Parse> getSyntacticChildren();

  /** 
   * Returns a list of the tokens contained by this object.  The tokens in this list should also
   * implement the {@link Parse} interface.
   *
   * @return the tokens
   */
  List<Parse> getTokens();

  /** 
   * Returns the syntactic type of this node. Typically, this is the part-of-speech or
   * constituent labeling.
   * 
   * @return the syntactic type.
   */
  String getSyntacticType();

  /** 
   * Returns the named-entity type of this node.
   * 
   * @return the named-entity type. 
   */
  String getEntityType();

  /** 
   * Determines whether this has an ancestor of type NAC.
   * 
   * @return {@code True} if this has an ancestor of type NAC, {@code false} otherwise.
   */
  boolean isParentNAC();

  /**
   * Returns the parent parse of this parse node.
   * 
   * @return the parent parse of this parse node.
   */
  Parse getParent();

  /**
   * Specifies whether this parse is a named-entity.
   * 
   * @return {@code true} if this parse is a named-entity; {@code false} otherwise.
   */
  boolean isNamedEntity();

  /**
   * Specifies whether this parse is a noun phrase.
   * 
   * @return {@code true} if this parse is a noun phrase; {@code false} otherwise.
   */
  boolean isNounPhrase();

  /**
   * Specifies whether this parse is a sentence.
   * 
   * @return {@code true} if this parse is a sentence; {@code false} otherwise.
   */
  boolean isSentence();

  /**
   * Specifies whether this parse is a coordinated noun phrase.
   * 
   * @return {@code true} if this parse is a coordinated noun phrase; {@code false} otherwise.
   */
  boolean isCoordinatedNounPhrase();

  /** 
   * Specifies whether this parse is a token.
   * 
   * @return {@code True} if this parse is a token; {@code false} otherwise.
   */
  boolean isToken();

  String toString();

  /** 
   * Returns an entity id associated with this parse and co-referent parses. This is only used for training on
   * already annotated coreference annotation.
   * 
   * @return an entity id associated with this parse and co-referent parses.
   */
  int getEntityId();

  /**
   * Returns the character offsets of this parse node.
   * 
   * @return The span representing the character offsets of this parse node.
   */
  Span getSpan();

  /**
   * Returns the first token which is not a child of this parse. If the first token of a sentence is
   * a child of this parse then null is returned.
   * 
   * @return the first token which is not a child of this parse or null if no such token exists.
   */
  Parse getPreviousToken();

  /**
   * Returns the next token which is not a child of this parse. If the last token of a sentence is
   * a child of this parse then null is returned.
   * 
   * @return the next token which is not a child of this parse or null if no such token exists.
   */
  Parse getNextToken();
}
