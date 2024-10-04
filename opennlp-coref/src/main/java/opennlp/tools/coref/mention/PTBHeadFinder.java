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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds head information from Penn Treebank style parses.
 */
public final class PTBHeadFinder implements HeadFinder {

  private static final Logger logger = LoggerFactory.getLogger(PTBHeadFinder.class);

  private static PTBHeadFinder instance;
  private static final Set<String> SKIP_SET = new HashSet<>();

  static {
    SKIP_SET.add("POS");
    SKIP_SET.add(",");
    SKIP_SET.add(":");
    SKIP_SET.add(".");
    SKIP_SET.add("''");
    SKIP_SET.add("-RRB-");
    SKIP_SET.add("-RCB-");
  }

  private PTBHeadFinder() {}

  /**
   * @return Retrieves an instance of this head finder.
   */
  public static HeadFinder getInstance() {
    if (instance == null) {
      instance = new PTBHeadFinder();
    }
    return instance;
  }

  @Override
  public Parse getHead(Parse p) {
    if (p == null) {
      return null;
    }
    if (p.isNounPhrase()) {
      List<Parse> parts = p.getSyntacticChildren();
      // shallow parse POS
      if (parts.size() > 2) {
        Parse child0 = parts.get(0);
        Parse child1 = parts.get(1);
        Parse child2 = parts.get(2);
        if (child1.isToken() && child1.getSyntacticType().equals("POS")
            && child0.isNounPhrase() && child2.isNounPhrase()) {
          return child2;
        }
      }
      // full parse POS
      if (parts.size() > 1) {
        Parse child0 = parts.get(0);
        if (child0.isNounPhrase()) {
          List<Parse> ctoks = child0.getTokens();
          if (ctoks.isEmpty()) {
            logger.debug("NP {} with no tokens.", child0);
          }
          Parse tok = ctoks.get(ctoks.size() - 1);
          if (tok.getSyntacticType().equals("POS")) {
            return null;
          }
        }
      }
      // coordinated nps are their own entities
      if (parts.size() > 1) {
        for (int pi = 1; pi < parts.size() - 1; pi++) {
          Parse child = parts.get(pi);
          if (child.isToken() && child.getSyntacticType().equals("CC")) {
            return null;
          }
        }
      }
      // all other NPs
      for (Parse child : parts) {
        logger.debug("Getting head : {} {} - type {} child {}", p.getSyntacticType(), p, child.getSyntacticType(), child);
        if (child.isNounPhrase()) {
          return child;
        }
      }
      return null;
    }
    else {
      return null;
    }
  }

  @Override
  public int getHeadIndex(Parse p) {
    List<Parse> sChildren = p.getSyntacticChildren();
    boolean countTokens = false;
    int tokenCount = 0;
    // check for NP -> NN S type structures and return last token before S as head.
    for (int sci = 0, scn = sChildren.size(); sci < scn;sci++) {
      Parse sc = sChildren.get(sci);
      logger.debug("Getting head index: {} {} - sChild {} type {}", p, p.getSyntacticType(), sci, sc.getSyntacticType());
      if (sc.getSyntacticType().startsWith("S")) {
        if (sci != 0) {
          countTokens = true;
        }
        else {
          logger.debug("Getting head index: NP -> S production assuming right-most head");
        }
      }
      if (countTokens) {
        tokenCount += sc.getTokens().size();
      }
    }
    List<Parse> toks = p.getTokens();
    if (toks.isEmpty()) {
      logger.debug("Empty tok list for parse {}",  p);
    }
    for (int ti = toks.size() - tokenCount - 1; ti >= 0; ti--) {
      Parse tok = toks.get(ti);
      if (!SKIP_SET.contains(tok.getSyntacticType())) {
        return ti;
      }
    }
    return toks.size() - tokenCount - 1;
  }

  /**
   * Returns the bottom-most head of a {@link Parse}.
   * If no head is available which is a child of <code>p</code> then
   * <code>p</code> is returned.
   *
   * @param p The parse to check for a bottom-most head.
   */
  @Override
  public Parse getLastHead(Parse p) {
    Parse head;
    logger.debug("Getting last head: {}", p);

    while (null != (head = getHead(p))) {
      logger.debug(" -> {}", head);
      if (p.getEntityId() != -1 && head.getEntityId() != p.getEntityId()) {
        logger.debug("{} ({}) -> {} ({})", p, p.getEntityId(), head, head.getEntityId());
      }
      p = head;
    }
    return p;
  }

  @Override
  public Parse getHeadToken(Parse p) {
    List<Parse> toks = p.getTokens();
    return toks.get(getHeadIndex(p));
  }
}
