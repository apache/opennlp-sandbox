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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.coref.linker.Linker;
import opennlp.tools.coref.resolver.ResolverUtils;
import opennlp.tools.util.Span;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides default implementation of many of the methods in the {@link MentionFinder} interface.
 */
public abstract class AbstractMentionFinder implements MentionFinder {

  private static final Logger logger = LoggerFactory.getLogger(AbstractMentionFinder.class);

  protected HeadFinder headFinder;

  protected boolean collectPrenominalNamedEntities;
  protected boolean collectCoordinatedNounPhrases;

  private void gatherHeads(Parse p, Map<Parse, Parse> heads) {
    Parse head = headFinder.getHead(p);
    logger.debug("Gathering Heads: {} -> ({}) {}", head, p.hashCode(), p);
    if (head != null) {
      logger.debug("head.hashCode() = {}", head.hashCode());
      heads.put(head, p);
    }
  }

  /**
   * Assigns head relations between noun phrases and the child np
   * which is their head.
   * @param nps List of valid nps for this mention finder.
   * @return mapping from noun phrases and the child np which is their head
   */
  protected Map<Parse, Parse> constructHeadMap(List<Parse> nps) {
    Map<Parse, Parse> headMap = new HashMap<>();
    for (Parse np : nps) {
      gatherHeads(np, headMap);
    }
    return headMap;
  }

  @Override
  public boolean isPrenominalNamedEntityCollection() {
    return collectPrenominalNamedEntities;
  }

  @Override
  public void setPrenominalNamedEntityCollection(boolean b) {
    collectPrenominalNamedEntities = b;
  }

  protected boolean isBasalNounPhrase(Parse np) {
    return np.getNounPhrases().isEmpty();
  }

  protected boolean isPossessive(Parse np) {
    List<Parse> parts = np.getSyntacticChildren();
    if (parts.size() > 1) {
      Parse child0 = parts.get(0);
      if (child0.isNounPhrase()) {
        List<Parse> ctoks = child0.getTokens();
        Parse tok = ctoks.get(ctoks.size() - 1);
        if (tok.getSyntacticType().equals("POS")) {
          return true;
        }
      }
    }
    if (parts.size() > 2) {
      Parse child0 = parts.get(0);
      Parse child1 = parts.get(1);
      Parse child2 = parts.get(2);
      if (child1.isToken() && child1.getSyntacticType().equals("POS")
          && child0.isNounPhrase() && child2.isNounPhrase()) {
        return true;
      }
    }
    return false;
  }

  protected boolean isOfPrepPhrase(Parse np) {
    List<Parse> parts = np.getSyntacticChildren();
    if (parts.size() == 2) {
      Parse child0 = parts.get(0);
      if (child0.isNounPhrase()) {
        Parse child1 = parts.get(1);
        List<Parse> cparts = child1.getSyntacticChildren();
        if (cparts.size() == 2) {
          Parse child2 = cparts.get(0);
          if (child2.isToken() && child2.toString().equals("of")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected boolean isConjoinedBasal(Parse np) {
    List<Parse> parts = np.getSyntacticChildren();
    boolean allToken = true;
    boolean hasConjunction = false;
    for (Parse c : parts) {
      if (c.isToken()) {
        if (c.getSyntacticType().equals("CC")) {
          hasConjunction = true;
        }
      } else {
        allToken = false;
        break;
      }
    }
    return allToken && hasConjunction;
  }

  private void collectCoordinatedNounPhraseMentions(Parse np, List<Mention> entities) {
    logger.trace("collectCoordNp: {}", np);
    //exclude nps with UCPs inside.
    List<Parse> sc = np.getSyntacticChildren();
    for (Parse scp : sc) {
      if (scp.getSyntacticType().equals("UCP") || scp.getSyntacticType().equals("NX")) {
        return;
      }
    }
    List<Parse> npTokens = np.getTokens();
    boolean inCoordinatedNounPhrase = false;
    int lastNpTokenIndex = headFinder.getHeadIndex(np);
    for (int ti = lastNpTokenIndex - 1; ti >= 0; ti--) {
      Parse tok = npTokens.get(ti);
      String tokStr = tok.toString();
      if ((tokStr.equals("and") || tokStr.equals("or")) && !isPartOfName(tok)) {
        if (lastNpTokenIndex != ti) {
          if (ti - 1 >= 0 && (npTokens.get(ti - 1)).getSyntacticType().startsWith("NN")) {
            Span npSpan = new Span((npTokens.get(ti + 1)).getSpan().getStart(),
                npTokens.get(lastNpTokenIndex).getSpan().getEnd());
            Mention snpExtent = new Mention(npSpan, npSpan, tok.getEntityId(), tok, "CNP");
            entities.add(snpExtent);
            logger.debug("Adding extent for conjunction in: {} preceded by {}",
                    np, npTokens.get(ti-1).getSyntacticType());
            inCoordinatedNounPhrase = true;
          }
          else {
            break;
          }
        }
        lastNpTokenIndex = ti - 1;
      }
      else if (inCoordinatedNounPhrase && tokStr.equals(",")) {
        if (lastNpTokenIndex != ti) {
          Span npSpan = new Span((npTokens.get(ti + 1)).getSpan().getStart(),
              npTokens.get(lastNpTokenIndex).getSpan().getEnd());
          Mention snpExtent = new Mention(npSpan, npSpan, tok.getEntityId(), tok ,"CNP");
          entities.add(snpExtent);
          logger.debug("Adding extent for comma in: {}", np);
        }
        lastNpTokenIndex = ti - 1;
      }
      else if (inCoordinatedNounPhrase && ti == 0 && lastNpTokenIndex >= 0) {
        Span npSpan = new Span((npTokens.get(ti)).getSpan().getStart(),
            npTokens.get(lastNpTokenIndex).getSpan().getEnd());
        Mention snpExtent = new Mention(npSpan, npSpan, tok.getEntityId(), tok, "CNP");
        entities.add(snpExtent);
        logger.debug("Adding extent for start coord in: {}", np);
      }
    }
  }

  private boolean handledPronoun(String tok) {
    return ResolverUtils.SINGULAR_THIRD_PERSON_PRONOUN_PATTERN.matcher(tok).find() ||
                 ResolverUtils.PLURAL_THIRD_PERSON_PRONOUN_PATTERN.matcher(tok).find() ||
                 ResolverUtils.SPEECH_PRONOUN_PATTERN.matcher(tok).find();
  }

  private void collectPossessivePronouns(Parse np, List<Mention> entities) {
    //XXX: Look at how training is done and examine whether this is
    // needed or can be accommodated in a different way.
    /*
    List snps = np.getSubNounPhrases();
    if (snps.size() != 0) {
      logger.trace("Found existing snps");
      for (int si = 0, sl = snps.size(); si < sl; si++) {
        Parse snp = (Parse) snps.get(si);
        Extent ppExtent = new Extent(snp.getSpan(), snp.getSpan(), snp.getEntityId(),
            null,Linker.PRONOUN_MODIFIER);
        entities.add(ppExtent);
      }
    }
    else {
    */
      logger.debug("CollectPossessivePronouns: {}", np);
      List<Parse> npTokens = np.getTokens();
      Parse headToken = headFinder.getHeadToken(np);
      for (int ti = npTokens.size() - 2; ti >= 0; ti--) {
        Parse tok = npTokens.get(ti);
        if (tok == headToken) {
          continue;
        }
        if (tok.getSyntacticType().startsWith("PRP") && handledPronoun(tok.toString())) {
          Mention ppExtent = new Mention(tok.getSpan(), tok.getSpan(),
              tok.getEntityId(), tok, Linker.PRONOUN_MODIFIER);
          logger.debug("CollectPossessivePronouns: adding possessive pronoun: {} {}", tok, tok.getEntityId());
          entities.add(ppExtent);
          logger.debug("Adding pos-pro: {}", ppExtent);
          break;
        }
      }
    //}
  }

  private void removeDuplicates(List<Mention> extents) {
    Mention lastExtent = null;
    for (Iterator<Mention> ei = extents.iterator(); ei.hasNext();) {
      Mention e = ei.next();
      if (lastExtent != null && e.getSpan().equals(lastExtent.getSpan())) {
        ei.remove();
      }
      else {
        lastExtent = e;
      }
    }
  }

  private boolean isHeadOfExistingMention(Parse np, Map<Parse, Parse> headMap,
      Set<Parse> mentions) {
    Parse head = headMap.get(np);
    while (head != null) {
      if (mentions.contains(head)) {
        return true;
      }
      head = headMap.get(head);
    }
    return false;
  }


  private void clearMentions(Set<Parse> mentions, Parse np) {
    Span npSpan = np.getSpan();
    logger.debug("Clearing {} for {}", mentions, np);
    mentions.removeIf(mention -> !mention.getSpan().contains(npSpan));
  }

  private Mention[] collectMentions(List<Parse> nps, Map<Parse, Parse> headMap) {
    List<Mention> mentions = new ArrayList<>(nps.size());
    Set<Parse> recentMentions = new HashSet<>();
    logger.debug("CollectMentions: {}", headMap);
    for (Parse np : nps) {
      logger.debug("CollectMentions: {} head={}", np, headMap.get(np));
      if (!isHeadOfExistingMention(np, headMap, recentMentions)) {
        clearMentions(recentMentions, np);
        if (!isPartOfName(np)) {
          Parse head = headFinder.getLastHead(np);
          Mention extent = new Mention(np.getSpan(), head.getSpan(), head.getEntityId(), np, null);
          logger.debug("Adding {} with head {}", np, head);
          mentions.add(extent);
          recentMentions.add(np);
          // determine name-entity type
          String entityType = getEntityType(headFinder.getHeadToken(head));
          if (entityType != null) {
            extent.setNameType(entityType);
          }
        } else {
          logger.debug("CollectMentions excluding np as part of name. np={}", np);
        }
      } else {
        logger.debug("CollectMentions excluding np as head of previous mention. np={}", np);
      }
      if (isBasalNounPhrase(np)) {
        if (collectPrenominalNamedEntities) {
          collectPrenominalNamedEntities(np, mentions);
        }
        if (collectCoordinatedNounPhrases) {
          collectCoordinatedNounPhraseMentions(np, mentions);
        }
        collectPossessivePronouns(np, mentions);
      } else {
        // Could use to get NP -> tokens CON structures for basal nps including NP -> NAC tokens
        //collectComplexNounPhrases(np,mentions);
      }
    }
    Collections.sort(mentions);
    removeDuplicates(mentions);
    return mentions.toArray(new Mention[0]);
  }

  private void collectPrenominalNamedEntities(Parse np, List<Mention> extents) {
    Parse htoken = headFinder.getHeadToken(np);
    List<Parse> nes = np.getNamedEntities();
    Span headTokenSpan = htoken.getSpan();
    for (Parse ne : nes) {
      if (!ne.getSpan().contains(headTokenSpan)) {
        logger.debug("Adding extent for prenominal ne: {}", ne);
        Mention extent = new Mention(ne.getSpan(), ne.getSpan(), ne.getEntityId(), null, "NAME");
        extent.setNameType(ne.getEntityType());
        extents.add(extent);
      }
    }
  }

  private String getEntityType(Parse headToken) {
    String entityType;
    for (Parse parent = headToken.getParent(); parent != null; parent = parent.getParent()) {
      entityType = parent.getEntityType();
      if (entityType != null) {
        return entityType;
      }
      if (parent.isSentence()) {
        break;
      }
    }
    List<Parse> tc = headToken.getChildren();
    int tcs = tc.size();
    if (tcs > 0) {
      Parse tchild = tc.get(tcs - 1);
      entityType = tchild.getEntityType();
      if (entityType != null) {
        return entityType;
      }
    }
    return null;
  }

  private boolean isPartOfName(Parse np) {
    String entityType;
    for (Parse parent = np.getParent(); parent != null; parent = parent.getParent()) {
      entityType = parent.getEntityType();
      logger.debug("IsPartOfName: entityType={}", entityType);
      if (entityType != null) {
        logger.debug("npSpan={} parentSpan={}", np.getSpan(), parent.getSpan());
        if (!np.getSpan().contains(parent.getSpan())) {
          return true;
        }
      }
      if (parent.isSentence()) {
        break;
      }
    }
    return false;
  }

  public List<Parse> getNamedEntities(Parse p) {
    return p.getNamedEntities();
  }

  @Override
  public Mention[] getMentions(Parse p) {
    List<Parse> nps = p.getNounPhrases();
    Collections.sort(nps);
    Map<Parse, Parse> headMap = constructHeadMap(nps);
    logger.debug("GetMentions: got {} named entities", nps.size());
    return collectMentions(nps, headMap);
  }

  @Override
  public boolean isCoordinatedNounPhraseCollection() {
    return collectCoordinatedNounPhrases;
  }

  @Override
  public void setCoordinatedNounPhraseCollection(boolean b) {
    collectCoordinatedNounPhrases = b;
  }
}
