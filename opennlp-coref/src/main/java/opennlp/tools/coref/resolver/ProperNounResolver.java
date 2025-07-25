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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves coreference between proper nouns.
 *
 * @see MaxentResolver
 * @see Resolver
 */
public class ProperNounResolver extends MaxentResolver {

  private static final Logger logger = LoggerFactory.getLogger(ProperNounResolver.class);
  private static final String MODEL_NAME = "pnmodel";

  private static Map<String, Set<String>> acroMap;
  private static boolean acroMapLoaded = false;

  public ProperNounResolver(String modelDirectory, ResolverMode m) throws IOException {
    super(modelDirectory, MODEL_NAME, m, 500);
    if (!acroMapLoaded) {
      initAcronyms(modelDirectory + "/acronyms");
      acroMapLoaded = true;
    }
    showExclusions = false;
  }

  public ProperNounResolver(String modelDirectory, ResolverMode m,NonReferentialResolver nonRefResolver)
      throws IOException {
    super(modelDirectory, MODEL_NAME, m, 500,nonRefResolver);
    if (!acroMapLoaded) {
      initAcronyms(modelDirectory + "/acronyms");
      acroMapLoaded = true;
    }
    showExclusions = false;
  }

  @Override
  public boolean canResolve(MentionContext mention) {
    return (mention.getHeadTokenTag().startsWith(NNP) || mention.getHeadTokenTag().startsWith("CD"));
  }

  private void initAcronyms(String name) {
    acroMap = new HashMap<>(15000);
    try (BufferedReader str = new BufferedReader(new FileReader(name))) {
      logger.debug("Reading acronyms database: {}", name);
      String line;
      while (null != (line = str.readLine())) {
        StringTokenizer st = new StringTokenizer(line, "\t");
        String acro = st.nextToken();
        String full = st.nextToken();
        Set<String> exSet = acroMap.get(acro);
        if (exSet == null) {
          exSet = new HashSet<>();
          acroMap.put(acro, exSet);
        }
        exSet.add(full);
        exSet = acroMap.get(full);
        if (exSet == null) {
          exSet = new HashSet<>();
          acroMap.put(full, exSet);
        }
        exSet.add(acro);
      }
    } catch (IOException e) {
      logger.error("Acronym Database not found: {}", e.getMessage(), e);
    }
  }

  private boolean isAcronym(String ecStrip, String xecStrip) {
    Set<String> exSet = acroMap.get(ecStrip);
    return exSet != null && exSet.contains(xecStrip);
  }

  protected List<String> getAcronymFeatures(MentionContext mention, DiscourseEntity entity) {
    MentionContext xec = ResolverUtils.getProperNounExtent(entity);
    String ecStrip = ResolverUtils.stripNp(mention);
    String xecStrip = ResolverUtils.stripNp(xec);
    if (ecStrip != null && xecStrip != null) {
      if (isAcronym(ecStrip, xecStrip)) {
        List<String> features = new ArrayList<>(1);
        features.add("knownAcronym");
        return features;
      }
    }
    return Collections.emptyList();
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    logger.debug("Getting features: mention = {} -> entity = {}", mention.toText(), entity);
    List<String> features = new ArrayList<>(super.getFeatures(mention, entity));
    if (entity != null) {
      features.addAll(ResolverUtils.getStringMatchFeatures(mention, entity));
      features.addAll(getAcronymFeatures(mention, entity));
    }
    return features;
  }

  @Override
  public boolean excluded(MentionContext mention, DiscourseEntity entity) {
    if (super.excluded(mention, entity)) {
      return true;
    }

    for (Iterator<MentionContext> ei = entity.getMentions(); ei.hasNext();) {
      MentionContext xec = ei.next();
      if (xec.getHeadTokenTag().startsWith(NNP)) {
        // || initialCaps.matcher(xec.headToken.toString()).find()) {
        logger.debug("Kept {} with {}", xec.toText(), xec.getHeadTokenTag());
        return false;
      }
    }

    return true;
  }
}
