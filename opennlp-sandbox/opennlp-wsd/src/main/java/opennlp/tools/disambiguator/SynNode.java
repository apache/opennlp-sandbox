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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;

import opennlp.tools.tokenize.Tokenizer;

/**
 * Convenience class to access some features via {@link Synset synsets}.
 * A {@link Synset} represents a concept, and contains a set of words, each of which
 * has a sense for that concept. Each element is thus synonymous with the other
 * words contained in the Synset.
 *
 * @see Synset
 */
public class SynNode {

  protected Synset parent;
  protected final Synset synset;

  private List<WordPOS> senseRelevantWords;

  private final List<Synset> hypernyms = new ArrayList<>();
  private final List<Synset> hyponyms = new ArrayList<>();
  private final List<Synset> meronyms = new ArrayList<>();
  private final List<Synset> holonyms = new ArrayList<>();
  private final List<Synset> entailments = new ArrayList<>();
  private final List<Synset> coordinateTerms = new ArrayList<>();
  private final List<Synset> causes = new ArrayList<>();
  private final List<Synset> attributes = new ArrayList<>();
  private final List<Synset> pertainyms = new ArrayList<>();
  private final List<WordPOS> synonyms = new ArrayList<>();

  /**
   * Initializes a {@link SynNode} via specified {@link Synset} and
   * {@code senseRelevantWords}.
   *
   * @param synSet The {@link Synset} to associate with.
   * @param senseRelevantWords The {@link WordPOS elements} to consider relevant.
   */
  public SynNode(Synset synSet, List<WordPOS> senseRelevantWords) {
    this.synset = synSet;
    this.senseRelevantWords = senseRelevantWords;
  }

  /**
   * Initializes a {@link SynNode} via specified {@link Synset} and
   * {@code senseRelevantWords}.
   *
   * @param parent The {@link Synset} to associate with as a parent element.
   * @param synSet The {@link Synset} to associate with.
   * @param senseRelevantWords The {@link WordPOS elements} to consider relevant.
   */
  public SynNode(Synset parent, Synset synSet, List<WordPOS> senseRelevantWords) {
    this(synSet, senseRelevantWords);
    this.parent = parent;
  }

  /**
   * @return Retrieves the associated {@link WordPOS elements}.
   */
  public List<WordPOS> getSenseRelevantWords() {
    return senseRelevantWords;
  }

  void addHypernyms() {
    PointerTargetNodeList phypernyms;
    try {
      phypernyms = PointerUtils.getDirectHypernyms(this.synset);
      for (PointerTargetNode ptn : phypernyms) {
        this.hypernyms.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addMeronyms() {
    PointerTargetNodeList pmeronyms;
    try {
      pmeronyms = PointerUtils.getMeronyms(this.synset);
      for (PointerTargetNode ptn : pmeronyms) {
        this.meronyms.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addHolonyms() {
    PointerTargetNodeList pholonyms;
    try {
      pholonyms = PointerUtils.getHolonyms(this.synset);
      for (PointerTargetNode ptn : pholonyms) {
        this.holonyms.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addHyponyms() {
    PointerTargetNodeList phyponyms;
    try {
      phyponyms = PointerUtils.getDirectHyponyms(this.synset);
      for (PointerTargetNode ptn : phyponyms) {
        this.hyponyms.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addEntailements() {
    PointerTargetNodeList pentailments;
    try {
      pentailments = PointerUtils.getEntailments(this.synset);
      for (PointerTargetNode ptn : pentailments) {
        this.entailments.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addCoordinateTerms() {
    PointerTargetNodeList pcoordinateTerms;
    try {
      pcoordinateTerms = PointerUtils.getCoordinateTerms(this.synset);
      for (PointerTargetNode ptn : pcoordinateTerms) {
        this.coordinateTerms.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addCauses() {
    PointerTargetNodeList pcauses;
    try {
      pcauses = PointerUtils.getCauses(this.synset);
      for (PointerTargetNode ptn : pcauses) {
        this.causes.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addAttributes() {
    PointerTargetNodeList pattributes;
    try {
      pattributes = PointerUtils.getAttributes(this.synset);
      for (PointerTargetNode ptn : pattributes) {
        this.attributes.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addPertainyms() {
    PointerTargetNodeList ppertainyms;
    try {
      ppertainyms = PointerUtils.getPertainyms(this.synset);
      for (PointerTargetNode ptn : ppertainyms) {
        this.pertainyms.add(ptn.getSynset());
      }
    } catch (JWNLException e) {
      throw new RuntimeException(e);
    }
  }

  void addSynonyms() {
    for (Word word : synset.getWords())
      synonyms.add(new WordPOS(word.toString(), word.getPOS()));
  }

  public List<Synset> getHypernyms() {
    return Collections.unmodifiableList(hypernyms);
  }

  public List<Synset> getHyponyms() {
    return Collections.unmodifiableList(hyponyms);
  }

  public List<Synset> getMeronyms() {
    return Collections.unmodifiableList(meronyms);
  }

  public List<Synset> getHolonyms() {
    return Collections.unmodifiableList(holonyms);
  }

  public List<Synset> getEntailments() {
    return Collections.unmodifiableList(entailments);
  }

  public List<Synset> getCoordinateTerms() {
    return Collections.unmodifiableList(coordinateTerms);
  }

  public List<Synset> getCauses() {
    return Collections.unmodifiableList(causes);
  }

  public List<Synset> getAttributes() {
    return Collections.unmodifiableList(attributes);
  }

  public List<Synset> getPertainyms() {
    return Collections.unmodifiableList(pertainyms);
  }

  public List<WordPOS> getSynonyms() {
    return Collections.unmodifiableList(synonyms);
  }

  public String getGloss() {
    return this.synset.getGloss();
  }

  public long getSynsetID() {
    return this.synset.getOffset();
  }

  /**
   * Retrieves associated {@link WordSense senses} for specified {@code nodes}.
   * 
   * @param nodes A list of {@link SynNode nodes} to score.
   * @return The {@link WordSense senses} obtained for the {@code nodes}.
   */
  public static List<WordSense> updateSenses(List<SynNode> nodes) {
    List<WordSense> scoredSenses = new ArrayList<>();

    final Tokenizer tokenizer = WSDHelper.getTokenizer();
    for (int i = 0; i < nodes.size(); i++) {
      SynNode sn = nodes.get(i);
      sn.senseRelevantWords = WSDHelper.getAllRelevantWords(tokenizer.tokenize(sn.getGloss()));
      WordSense wordSense = new WordSense(i, sn);
      scoredSenses.add(wordSense);
    }
    return scoredSenses;
  }
}
