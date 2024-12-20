/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.tools.disambiguator;

import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Disambiguator} implementation of the <b>Most Frequent Sense</b> (MFS) approach.
 * <p>
 * This approach returns the senses in order of frequency in WordNet.
 * The first sense is the most frequent.
 *
 * @see Disambiguator
 * @see WSDParameters
 */
public class MFS extends AbstractWSDisambiguator {

  private static final Logger LOG = LoggerFactory.getLogger(MFS.class);

  public static final String NONESENSE = "nonesense";

  /**
   * Extracts the most frequent sense for a specified {@link WSDSample}.
   *
   * @param sample The {@link WSDSample sample} to extract the sense for.
   *               Must not be {@code null}.
   * @return The most frequent senses from wordnet
   */
  public static String getMostFrequentSense(WSDSample sample) {
    List<Synset> synsets = sample.getSynsets();
    if (!synsets.isEmpty()) {
      String sampleLemma = sample.getLemmas()[sample.getTargetPosition()];
      for (Word wd : synsets.get(0).getWords()) {
        if (wd.getLemma().equalsIgnoreCase(sampleLemma)) {
          try {
            return WSDParameters.SenseSource.WORDNET.name() + " " + wd.getSenseKey();
          } catch (JWNLException e) {
            LOG.error(e.getLocalizedMessage(), e);
          }
        }
      }
    }
    return NONESENSE;
  }

  /**
   * Extracts the most frequent senses for a specified {@link WSDSample}.
   *
   * @param sample The {@link WSDSample sample} to extract the sense for.
   *               Must not be {@code null}.
   * @return The most frequent senses from wordnet
   */
  public static String[] getMostFrequentSenses(WSDSample sample) {
    List<Synset> synsets = sample.getSynsets();
    String[] senseKeys = new String[synsets.size()];

    String sampleLemma = sample.getLemmas()[sample.getTargetPosition()];
    for (int i = 0; i < synsets.size(); i++) {
      for (Word wd : synsets.get(i).getWords()) {
        if (wd.getLemma().equalsIgnoreCase(sampleLemma)) {
          try {
            senseKeys[i] = WSDParameters.SenseSource.WORDNET.name() + " " + wd.getSenseKey();
            break;
          } catch (JWNLException e) {
            LOG.error(e.getLocalizedMessage(), e);
          }
          break;

        }
      }
    }
    return senseKeys;

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String disambiguate(WSDSample sample) {
    String targetTag = sample.getTargetTag();
    if (WSDHelper.isRelevantPOSTag(targetTag)) {
      return disambiguate(sample.getTargetWordTag());
    } else {
      if (WSDHelper.getNonRelevWordsDef(targetTag) != null) {
        return WSDParameters.SenseSource.WSDHELPER.name() + " " + targetTag;
      } else {
        return null;
      }
    }
  }
  
}
