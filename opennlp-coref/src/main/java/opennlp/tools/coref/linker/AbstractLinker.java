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
import opennlp.tools.coref.DiscourseModel;
import opennlp.tools.coref.mention.HeadFinder;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.MentionFinder;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.coref.resolver.AbstractResolver;
import opennlp.tools.coref.sim.Gender;
import opennlp.tools.coref.sim.Number;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a default implementation of many of the methods in {@link Linker} that
 * most implementations of {@link Linker} will want to extend.
 */
public abstract class AbstractLinker implements Linker {

  private static final Logger logger = LoggerFactory.getLogger(AbstractLinker.class);

  /** The mention finder used to find mentions. */
  protected MentionFinder mentionFinder;

  /** Specifies whether debug print is generated. */
  protected boolean debug = true;

  /** The mode in which this linker is running. */
  protected final LinkerMode mode;

  /** Instance used for returning the same linker for subsequent getInstance requests. */
  protected static Linker linker;

  /** The resolvers used by this Linker. */
  protected AbstractResolver[] resolvers;
  /** The names of the resolvers used by this Linker. */
  protected String[] resolverNames;

  /** Array used to store the results of each call made to the linker. */
  protected DiscourseEntity[] entities;

  /** The index of resolver which is used for singular pronouns. */
  protected int SINGULAR_PRONOUN;

  /** The name of the project where the coreference models are stored. */
  protected final String corefProject;

  /** The head finder used in this linker. */
  protected HeadFinder headFinder;

  /** Specifies whether coreferent mentions should be combined into a single entity.
   * Set this to true to combine them, false otherwise.  */
  protected final boolean useDiscourseModel;

  /** Specifies whether mentions for which no resolver can be used should be added to the
   * discourse model.
   */
  protected final boolean removeUnresolvedMentions;
  
  /**
   * Creates a new linker using the models in the specified project directory, using the specified mode,
   * and combining coreferent entities based on the specified value.
   * @param project The location of the models or other data needed by this linker.
   * @param mode The mode the linker should be run in: testing, training, or evaluation.
   * @param useDiscourseModel Specifies whether coreferent mention should be combined or not.
   */
  public AbstractLinker(String project, LinkerMode mode, boolean useDiscourseModel) {
    this.corefProject = project;
    this.mode = mode;
    SINGULAR_PRONOUN = -1;
    this.useDiscourseModel = useDiscourseModel;
    removeUnresolvedMentions = true;
  }

  /**
   * Resolves the specified mention to an entity in the specified discourse model
   * or creates a new entity for the mention.
   *
   * @param mention The mention to resolve.
   * @param discourseModel The discourse model of existing entities.
   */
  protected void resolve(MentionContext mention, DiscourseModel discourseModel) {
    boolean validEntity = true; // true if we should add this entity to the dm
    boolean canResolve = false;

    for (int ri = 0; ri < resolvers.length; ri++) {
      if (resolvers[ri].canResolve(mention)) {
        if (mode == LinkerMode.TEST) {
          entities[ri] = resolvers[ri].resolve(mention, discourseModel);
          canResolve = true;
        }
        else if (mode == LinkerMode.TRAIN) {
          entities[ri] = resolvers[ri].retain(mention, discourseModel);
          if (ri + 1 != resolvers.length) {
            canResolve = true;
          }
        }
        else if (mode == LinkerMode.EVAL) {
          entities[ri] = resolvers[ri].retain(mention, discourseModel);
          //DiscourseEntity rde = resolvers[ri].resolve(mention, discourseModel);
          //eval.update(rde == entities[ri], ri, entities[ri], rde);
        }
        else {
          logger.warn("Invalid linker mode '{}' detected during resolve in AbstractLinker", mode);
        }
        if (ri == SINGULAR_PRONOUN && entities[ri] == null) {
          validEntity = false;
        }
      }
      else {
        entities[ri] = null;
      }
    }
    if (!canResolve && removeUnresolvedMentions) {
      // What is / was econtext here ?
      //logger.debug("No resolver for: "+econtext.toText()
      //    + " head="+econtext.headTokenText+" "+econtext.headTokenTag);
      validEntity = false;
    }
    DiscourseEntity de = checkForMerges(discourseModel, entities);
    if (validEntity) {
      updateExtent(discourseModel, mention, de,useDiscourseModel);
    }
  }

  @Override
  public HeadFinder getHeadFinder() {
    return headFinder;
  }

  /**
   * Updates the specified discourse model with the specified mention as coreferent with the specified entity.
   * @param dm The discourse model
   * @param m The mention to be added to the specified entity.
   * @param entity The entity which is mentioned by the specified mention.
   * @param useDiscourseModel Whether the mentions should be kept as an entity or simply co-indexed.
   */
  protected void updateExtent(DiscourseModel dm, MentionContext m, DiscourseEntity entity,
                              boolean useDiscourseModel) {
    if (useDiscourseModel) {
      if (entity != null) {
        logger.debug("Adding extent: {}", m.toText());
        if (entity.getGenderProbability() < m.getGenderProb()) {
          entity.setGender(m.getGender());
          entity.setGenderProbability(m.getGenderProb());
        }
        if (entity.getNumberProbability() < m.getNumberProb()) {
          entity.setNumber(m.getNumber());
          entity.setNumberProbability(m.getNumberProb());
        }
        entity.addMention(m);
        dm.mentionEntity(entity);
      } else {
        logger.debug("Creating Extent: {} {} {}", m.toText(), m.getGender(), m.getNumber());
        entity = new DiscourseEntity(m, m.getGender(), m.getGenderProb(), m.getNumber(), m.getNumberProb());
        dm.addEntity(entity);
      }
    } else {
      if (entity != null) {
        DiscourseEntity newEntity =
                new DiscourseEntity(m, m.getGender(), m.getGenderProb(), m.getNumber(), m.getNumberProb());
        dm.addEntity(newEntity);
        newEntity.setId(entity.getId());
      }
      else {
        DiscourseEntity newEntity =
                new DiscourseEntity(m, m.getGender(), m.getGenderProb(), m.getNumber(), m.getNumberProb());
        dm.addEntity(newEntity);
      }
    }
  }

  protected DiscourseEntity checkForMerges(DiscourseModel dm, DiscourseEntity[] des) {
    DiscourseEntity de1; // temporary variable
    DiscourseEntity de2; // temporary variable
    de1 = des[0];
    for (int di = 1; di < des.length; di++) {
      de2 = des[di];
      if (de2 != null) {
        if (de1 != null && de1 != de2) {
          dm.mergeEntities(de1, de2, 1);
        }
        else {
          de1 = de2;
        }
      }
    }
    return (de1);
  }

  @Override
  public DiscourseEntity[] getEntities(Mention[] mentions) {
    MentionContext[] extentContexts = this.constructMentionContexts(mentions);
    DiscourseModel dm = new DiscourseModel();
    for (MentionContext extentContext : extentContexts) {
      logger.debug("{}", extentContext.toText());
      resolve(extentContext, dm);
    }
    return (dm.getEntities());
  }

  @Override
  public void setEntities(Mention[] mentions) {
    getEntities(mentions);
  }

  @Override
  public void train() throws IOException {
    for (AbstractResolver resolver : resolvers) {
      resolver.train();
    }
  }

  @Override
  public MentionFinder getMentionFinder() {
    return mentionFinder;
  }

  @Override
  public MentionContext[] constructMentionContexts(Mention[] mentions) {
    int mentionInSentenceIndex = -1;
    int numMentionsInSentence = -1;
    int prevSentenceIndex = -1;
    MentionContext[] contexts = new MentionContext[mentions.length];
    for (int mi = 0,mn = mentions.length;mi < mn; mi++) {
      Parse mentionParse = mentions[mi].getParse();
      if (mentionParse == null) {
        logger.warn("No parse for {}", mentions[mi]);
      } else {
        logger.debug("Constructing MentionContexts: mentionParse = {}", mentionParse);
        int sentenceIndex = mentionParse.getSentenceNumber();
        if (sentenceIndex != prevSentenceIndex) {
          mentionInSentenceIndex = 0;
          prevSentenceIndex = sentenceIndex;
          numMentionsInSentence = 0;
          for (int msi = mi; msi < mentions.length; msi++) {
            Parse p = mentions[msi].getParse();
            if (p != null && sentenceIndex != p.getSentenceNumber()) {
              break;
            }
            numMentionsInSentence++;
          }
        }
        contexts[mi] = new MentionContext(mentions[mi], mentionInSentenceIndex,
                numMentionsInSentence, mi, sentenceIndex, getHeadFinder());
        logger.debug("Constructing MentionContexts:: mi={} sn={} extent={} parse={} mc={}",
                mi, mentionParse.getSentenceNumber(), mentions[mi], mentionParse.getSpan(), contexts[mi].toText());
        contexts[mi].setId(mentions[mi].getId());
        mentionInSentenceIndex++;
        if (mode != LinkerMode.SIM) {
          Gender g  = computeGender(contexts[mi]);
          contexts[mi].setGender(g.getType(),g.getConfidence());
          Number n = computeNumber(contexts[mi]);
          contexts[mi].setNumber(n.getType(),n.getConfidence());
        }
      }
    }
    return (contexts);
  }

  protected abstract Gender computeGender(MentionContext mention);
  protected abstract Number computeNumber(MentionContext mention);
}
