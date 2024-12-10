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

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import opennlp.tools.util.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base implementation of {@link Disambiguator}
 *
 * @implNote Examples on how to use each approach are provided in the test section.
 *
 * @see Disambiguator
 * @see WSDParameters
 */
abstract class AbstractWSDisambiguator implements Disambiguator {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractWSDisambiguator.class);

  private static final Pattern SPLIT = Pattern.compile("\\.");

  protected WSDParameters params;

  /**
   * @return Retrieves the parameters of the disambiguation algorithm.
   */
  public WSDParameters getParams() {
    return params;
  }

  /**
   * @param params Sets the disambiguation implementation specific parameters.
   *
   * @throws InvalidParameterException Thrown if specified parameters are invalid.
   */
  public void setParams(WSDParameters params) throws InvalidParameterException {
    this.params = params;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String disambiguate(String[] tokenizedContext, String[] tokenTags,
                             String[] lemmas, int ambiguousTokenIndex) {
    return disambiguate(new WSDSample(tokenizedContext, tokenTags, lemmas, ambiguousTokenIndex));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> disambiguate(String[] tokenizedContext, String[] tokenTags,
                                   String[] lemmas, Span ambiguousTokenIndexSpan) {
    List<String> senses = new ArrayList<>();

    int start = Math.max(0, ambiguousTokenIndexSpan.getStart());
    int end = Math.max(start, Math.min(tokenizedContext.length, ambiguousTokenIndexSpan.getEnd()));

    for (int i = start; i < end + 1; i++) {

      if (WSDHelper.isRelevantPOSTag(tokenTags[i])) {
        WSDSample sample = new WSDSample(tokenizedContext, tokenTags, lemmas, i);
        String sense = disambiguate(sample);
        senses.add(sense);
      } else {
        if (WSDHelper.getNonRelevWordsDef(tokenTags[i]) != null) {
          String sense = WSDParameters.SenseSource.WSDHELPER.name() + " "
              + WSDHelper.getNonRelevWordsDef(tokenTags[i]);
          senses.add(sense);
        } else {
          senses.add(null);
        }
      }
    }
    return senses;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> disambiguate(String[] tokenizedContext, String[] tokenTags,
                                   String[] lemmas) {

    List<String> senses = new ArrayList<>();
    for (int i = 0; i < tokenizedContext.length; i++) {

      if (WSDHelper.isRelevantPOSTag(tokenTags[i])) {
        WSDSample sample = new WSDSample(tokenizedContext, tokenTags, lemmas, i);
        senses.add(disambiguate(sample));
      } else {
        if (WSDHelper.getNonRelevWordsDef(tokenTags[i]) != null) {
          String sense = WSDParameters.SenseSource.WSDHELPER.name() + " "
              + WSDHelper.getNonRelevWordsDef(tokenTags[i]);
          senses.add(sense);
        } else {
          senses.add(null);
        }
      }
    }
    return senses;
  }

  /**
   * Conducts disambiguation via available {@link Synset synsets} for the specified
   * {@code wordTag}.
   *
   * @param wordTag A combination of word and POS tag, separated by a {@code .} character.
   * @return The disambiguated sense and key if disambiguation was successful,
   *         {@code null} otherwise.
   */
  protected String disambiguate(String wordTag) {

    String[] splitWordTag = SPLIT.split(wordTag);

    String word = splitWordTag[0];
    String tag = splitWordTag[1];

    POS pos;
    if (tag.equalsIgnoreCase("a")) {
      pos = POS.ADJECTIVE;
    } else if (tag.equalsIgnoreCase("r")) {
      pos = POS.ADVERB;
    } else if (tag.equalsIgnoreCase("n")) {
      pos = POS.NOUN;
    } else if (tag.equalsIgnoreCase("v")) {
      pos = POS.VERB;
    } else
      pos = null;

    if (pos != null) {

      WordPOS wordPOS = new WordPOS(word, pos);

      List<Synset> synsets = wordPOS.getSynsets();
      if (synsets != null) {
        String sense = WSDParameters.SenseSource.WORDNET.name();

        for (Word wd : synsets.get(0).getWords()) {
          if (wd.getLemma().equals(word)) {
            try {
              sense = sense + " " + wd.getSenseKey();
              break;
            } catch (JWNLException e) {
              LOG.error(e.getLocalizedMessage(), e);
            }
          }
        }
        return sense;
      } else {
        LOG.debug("{}    {}", word, pos);
        LOG.debug("The word has no definitions in WordNet !");
        return null;
      }

    } else {
      LOG.debug("{}    {}", word, pos);
      LOG.debug("The word has no definitions in WordNet !");
      return null;
    }
  }
}