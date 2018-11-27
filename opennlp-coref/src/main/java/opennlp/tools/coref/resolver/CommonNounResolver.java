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
 * Resolves coreference between common nouns.
 */
public class CommonNounResolver extends MaxentResolver {

  public CommonNounResolver(String projectName, ResolverMode m) throws IOException {
    super(projectName,"cmodel", m, 80, true);
    showExclusions = false;
    preferFirstReferent = true;
  }

  public CommonNounResolver(String projectName, ResolverMode m, NonReferentialResolver nrr)
      throws IOException {
    super(projectName,"cmodel", m, 80, true,nrr);
    showExclusions = false;
    preferFirstReferent = true;
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    features.addAll(super.getFeatures(mention, entity));
    if (entity != null) {
      features.addAll(ResolverUtils.getContextFeatures(mention));
      features.addAll(ResolverUtils.getStringMatchFeatures(mention,entity));
    }
    return features;
  }

  public boolean canResolve(MentionContext mention) {
    String firstTok = mention.getFirstTokenText().toLowerCase();
    String firstTokTag = mention.getFirstToken().getSyntacticType();
    boolean rv = mention.getHeadTokenTag().equals("NN")
        && !ResolverUtils.definiteArticle(firstTok, firstTokTag);
    return rv;
  }

  @Override
  protected boolean excluded(MentionContext ec, DiscourseEntity de) {
    if (super.excluded(ec, de)) {
      return true;
    }
    else {
      MentionContext cec = de.getLastExtent();
      return !canResolve(cec) || super.excluded(ec, de);
    }
  }
}
