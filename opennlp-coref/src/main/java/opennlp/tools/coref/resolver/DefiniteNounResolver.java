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
 * Resolves coreference between definite noun-phrases.
 *
 * @see MaxentResolver
 */
public class DefiniteNounResolver extends MaxentResolver {

  public DefiniteNounResolver(String modelDirectory, ResolverMode m) throws IOException {
    super(modelDirectory, "defmodel", m, 80);
    // preferFirstReferent = true;
  }

  public DefiniteNounResolver(String modelDirectory, ResolverMode m, NonReferentialResolver nrr)
      throws IOException {
    super(modelDirectory, "defmodel", m, 80,nrr);
    // preferFirstReferent = true;
  }

  @Override
  public boolean canResolve(MentionContext mention) {
    Object[] mtokens = mention.getTokens();

    String firstTok = mention.getFirstTokenText().toLowerCase();
    return mtokens.length > 1 && !mention.getHeadTokenTag().startsWith("NNP")
        && ResolverUtils.definiteArticle(firstTok, mention.getFirstTokenTag());
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<>(super.getFeatures(mention, entity));
    if (entity != null) {
      features.addAll(ResolverUtils.getContextFeatures(mention));
      features.addAll(ResolverUtils.getStringMatchFeatures(mention,entity));
      features.addAll(ResolverUtils.getDistanceFeatures(mention,entity));
    }
    return (features);
  }
}
