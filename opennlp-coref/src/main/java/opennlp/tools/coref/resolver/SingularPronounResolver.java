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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class resolver singular pronouns such as "he", "she", "it" and their various forms.
 *
 * @see MaxentResolver
 * @see Resolver
 */
public class SingularPronounResolver extends MaxentResolver {

  private static final Logger logger = LoggerFactory.getLogger(SingularPronounResolver.class);
  private static final String MODEL_NAME = "pmodel";

  public SingularPronounResolver(String modelDirectory, ResolverMode m) throws IOException {
    super(modelDirectory, MODEL_NAME, m, 30);
    this.numSentencesBack = 2;
  }

  public SingularPronounResolver(String modelDirectory, ResolverMode m, NonReferentialResolver nrr)
      throws IOException {
    super(modelDirectory, MODEL_NAME, m, 30, nrr);
    this.numSentencesBack = 2;
  }

  @Override
  public boolean canResolve(MentionContext mention) {
    logger.debug("CanResolve: ec=({}) {}", mention.getId(), mention.toText());
    String tag = mention.getHeadTokenTag();
    return tag != null && tag.startsWith(PRP)
        && ResolverUtils.SINGULAR_THIRD_PERSON_PRONOUN_PATTERN.matcher(mention.getHeadTokenText()).matches();
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<>(super.getFeatures(mention, entity));
    if (entity != null) { //generate pronoun w/ referent features
      MentionContext cec = entity.getLastExtent();
      //String gen = getPronounGender(pronoun);
      features.addAll(ResolverUtils.getPronounMatchFeatures(mention,entity));
      features.addAll(ResolverUtils.getContextFeatures(cec));
      features.addAll(ResolverUtils.getDistanceFeatures(mention,entity));
      features.add(ResolverUtils.getMentionCountFeature(entity));
      /*
      //lexical features
      Set featureSet = new HashSet();
      for (Iterator ei = entity.getExtents(); ei.hasNext();) {
        MentionContext ec = (MentionContext) ei.next();
        List toks = ec.tokens;
        Parse tok;
        int headIndex = PTBHeadFinder.getInstance().getHeadIndex(toks);
        for (int ti = 0; ti < headIndex; ti++) {
          tok = (Parse) toks.get(ti);
          featureSet.add(gen + "mw=" + tok.toString().toLowerCase());
          featureSet.add(gen + "mt=" + tok.getSyntacticType());
        }
        tok = (Parse) toks.get(headIndex);
        featureSet.add(gen + "hw=" + tok.toString().toLowerCase());
        featureSet.add(gen + "ht=" + tok.getSyntacticType());
        //semantic features
        if (ec.neType != null) {
          featureSet.add(gen + "," + ec.neType);
        }
        else {
          for (Iterator si = ec.synsets.iterator(); si.hasNext();) {
            Integer synset = (Integer) si.next();
            featureSet.add(gen + "," + synset);
          }
        }
      }
      Iterator fset = featureSet.iterator();
      while (fset.hasNext()) {
        String f = (String) fset.next();
        features.add(f);
      }
      */
    }
    return features;
  }

  @Override
  public boolean excluded(MentionContext mention, DiscourseEntity entity) {
    if (super.excluded(mention, entity)) {
      return true;
    }
    String mentionGender = null;

    for (Iterator<MentionContext> ei = entity.getMentions(); ei.hasNext();) {
      MentionContext entityMention = ei.next();
      String tag = entityMention.getHeadTokenTag();
      if (tag != null && tag.startsWith(PRP)
          && ResolverUtils.SINGULAR_THIRD_PERSON_PRONOUN_PATTERN.matcher(mention.getHeadTokenText()).matches()) {
        if (mentionGender == null) { //lazy initialization
          mentionGender = ResolverUtils.getPronounGender(mention.getHeadTokenText());
        }
        String entityGender = ResolverUtils.getPronounGender(entityMention.getHeadTokenText());
        if (!entityGender.equals("u") && !mentionGender.equals(entityGender)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  protected boolean outOfRange(MentionContext mention, DiscourseEntity entity) {
    MentionContext cec = entity.getLastExtent();
    return mention.getSentenceNumber() - cec.getSentenceNumber() > numSentencesBack;
  }
}
