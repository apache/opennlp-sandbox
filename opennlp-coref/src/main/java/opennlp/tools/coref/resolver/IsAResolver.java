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
import java.util.regex.Pattern;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves coreference between appositives.
 *
 * @see MaxentResolver
 */
public class IsAResolver extends MaxentResolver {

  private static final Logger logger = LoggerFactory.getLogger(IsAResolver.class);

  private final Pattern predicativePattern;

  public IsAResolver(String modelDirectory, ResolverMode m) throws IOException {
    super(modelDirectory, "/imodel", m, 20);
    showExclusions = false;
    //predicativePattern = Pattern.compile("^(,|am|are|is|was|were|--)$");
    predicativePattern = Pattern.compile("^(,|--)$");
  }

  public IsAResolver(String modelDirectory, ResolverMode m, NonReferentialResolver nrr) throws IOException {
    super(modelDirectory, "/imodel", m, 20, nrr);
    showExclusions = false;
    //predicativePattern = Pattern.compile("^(,|am|are|is|was|were|--)$");
    predicativePattern = Pattern.compile("^(,|--)$");
  }

  @Override
  public boolean canResolve(MentionContext ec) {
    if (ec.getHeadTokenTag().startsWith(NN)) {
      return ec.getPreviousToken() != null
          && predicativePattern.matcher(ec.getPreviousToken().toString()).matches();
    }
    return false;
  }

  @Override
  protected boolean excluded(MentionContext ec, DiscourseEntity de) {
    MentionContext cec = de.getLastExtent();
    logger.debug("Excluded: ec.span={} cec.span={} cec={} lastToken={}",
            ec.getSpan(), cec.getSpan(), cec.toText(), ec.getNextToken());
    
    if (ec.getSentenceNumber() != cec.getSentenceNumber()) {
      logger.debug("Excluded: (true) not same sentence");
      return true;
    }
    // shallow parse appositives
    logger.debug("Excluded: ec={} {} cec={} {}", ec.toText(), ec.getSpan(), cec.toText(), cec.getSpan());
    if (cec.getIndexSpan().getEnd() == ec.getIndexSpan().getStart() - 2) {
      return false;
    }
    // full parse w/o trailing comma
    if (cec.getIndexSpan().getEnd() == ec.getIndexSpan().getEnd()) {
      logger.debug("Excluded: (false) spans share end");
      return false;
    }
    // full parse w/ trailing comma or period
    if (cec.getIndexSpan().getEnd() <= ec.getIndexSpan().getEnd() + 2 && (ec.getNextToken() != null
        && (ec.getNextToken().toString().equals(",") || ec.getNextToken().toString().equals(".")))) {
      logger.debug("Excluded: (false) spans end + punct");
      return false;
    }
    logger.debug("Excluded: (true) default");
    return true;
  }

  @Override
  protected boolean outOfRange(MentionContext ec, DiscourseEntity de) {
    MentionContext cec = de.getLastExtent();
    return (cec.getSentenceNumber() != ec.getSentenceNumber());
  }

  @Override
  protected boolean defaultReferent(DiscourseEntity de) {
    return true;
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<>(super.getFeatures(mention, entity));
    if (entity != null) {
      MentionContext ant = entity.getLastExtent();
      List<String> leftContexts = ResolverUtils.getContextFeatures(ant);
      for (String leftContext : leftContexts) {
        features.add("l" + leftContext);
      }
      List<String> rightContexts = ResolverUtils.getContextFeatures(mention);
      for (String rightContext : rightContexts) {
        features.add("r" + rightContext);
      }
      features.add("hts" + ant.getHeadTokenTag() + "," + mention.getHeadTokenTag());
    }

    logger.debug("GetFeatures: {}", features);
    return features;
  }
}
