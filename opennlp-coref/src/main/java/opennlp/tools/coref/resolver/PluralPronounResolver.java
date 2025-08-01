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
import java.util.List;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

/**
 * Resolves coreference between plural pronouns and their referents.
 *
 * @see MaxentResolver
 * @see Resolver
 */
public class PluralPronounResolver extends MaxentResolver {

  private static final String MODEL_NAME = "tmodel";
  private static final int NUM_SENTS_BACK_PRONOUNS = 2;

  public PluralPronounResolver(String modelDirectory, ResolverMode m) throws IOException {
    super(modelDirectory, MODEL_NAME, m, 30);
  }

  public PluralPronounResolver(String modelDirectory, ResolverMode m, NonReferentialResolver nrr)
      throws IOException {
    super(modelDirectory, MODEL_NAME, m, 30, nrr);
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<>(super.getFeatures(mention, entity));
    //features.add("eid="+pc.id);
    if (entity != null) { //generate pronoun w/ referent features
      features.addAll(ResolverUtils.getPronounMatchFeatures(mention,entity));
      MentionContext cec = entity.getLastExtent();
      features.addAll(ResolverUtils.getDistanceFeatures(mention,entity));
      features.addAll(ResolverUtils.getContextFeatures(cec));
      features.add(ResolverUtils.getMentionCountFeature(entity));
      /*
      //lexical features
      Set featureSet = new HashSet();
      for (Iterator ei = entity.getExtents(); ei.hasNext();) {
        MentionContext ec = (MentionContext) ei.next();
        int headIndex = PTBHeadFinder.getInstance().getHeadIndex(ec.tokens);
        Parse tok = (Parse) ec.tokens.get(headIndex);
        featureSet.add("hw=" + tok.toString().toLowerCase());
        if (ec.parse.isCoordinatedNounPhrase()) {
          featureSet.add("ht=CC");
        }
        else {
          featureSet.add("ht=" + tok.getSyntacticType());
        }
        if (ec.neType != null){
          featureSet.add("ne="+ec.neType);
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
  protected boolean outOfRange(MentionContext mention, DiscourseEntity entity) {
    MentionContext cec = entity.getLastExtent();
    return mention.getSentenceNumber() - cec.getSentenceNumber() > NUM_SENTS_BACK_PRONOUNS;
  }

  @Override
  public boolean canResolve(MentionContext mention) {
    String tag = mention.getHeadTokenTag();
    return tag != null && tag.startsWith(PRP)
        && ResolverUtils.PLURAL_THIRD_PERSON_PRONOUN_PATTERN.matcher(mention.getHeadTokenText()).matches();
  }
}
