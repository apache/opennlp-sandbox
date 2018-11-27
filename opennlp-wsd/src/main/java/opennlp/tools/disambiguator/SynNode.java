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

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;

/**
 * Convenience class to access some features.
 */
public class SynNode {

  public Synset parent;
  public Synset synset;

  protected ArrayList<WordPOS> senseRelevantWords;

  public ArrayList<Synset> hypernyms = new ArrayList<Synset>();
  public ArrayList<Synset> hyponyms = new ArrayList<Synset>();
  public ArrayList<Synset> meronyms = new ArrayList<Synset>();
  public ArrayList<Synset> holonyms = new ArrayList<Synset>();
  public ArrayList<Synset> entailments = new ArrayList<Synset>();
  public ArrayList<Synset> coordinateTerms = new ArrayList<Synset>();
  public ArrayList<Synset> causes = new ArrayList<Synset>();
  public ArrayList<Synset> attributes = new ArrayList<Synset>();
  public ArrayList<Synset> pertainyms = new ArrayList<Synset>();

  public ArrayList<WordPOS> synonyms = new ArrayList<WordPOS>();

  public SynNode(Synset parent, Synset synSet,
      ArrayList<WordPOS> senseRelevantWords) {
    this.parent = parent;
    this.synset = synSet;
    this.senseRelevantWords = senseRelevantWords;
  }

  public SynNode(Synset synSet, ArrayList<WordPOS> senseRelevantWords) {
    this.synset = synSet;
    this.senseRelevantWords = senseRelevantWords;
  }

  public ArrayList<WordPOS> getSenseRelevantWords() {
    return senseRelevantWords;
  }

  public void setSenseRelevantWords(ArrayList<WordPOS> senseRelevantWords) {
    this.senseRelevantWords = senseRelevantWords;
  }

  public void setHypernyms() {
    // PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList phypernyms = new PointerTargetNodeList();
    try {
      phypernyms = PointerUtils.getDirectHypernyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  hypernyms");
      e.printStackTrace();
    }

    for (int i = 0; i < phypernyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) phypernyms.get(i);
      this.hypernyms.add(ptn.getSynset());
    }

  }

  public void setMeronyms() {
    // PointerUtils pointerUtils = PointerUtils.getInstance();
    PointerTargetNodeList pmeronyms = new PointerTargetNodeList();
    try {
      pmeronyms = PointerUtils.getMeronyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  meronyms");
      e.printStackTrace();
    }

    for (int i = 0; i < pmeronyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pmeronyms.get(i);
      this.meronyms.add(ptn.getSynset());
    }
  }

  public void setHolonyms() {
    // PointerUtils pointerUtils = PointerUtils.getInstance();
    PointerTargetNodeList pholonyms = new PointerTargetNodeList();
    try {
      pholonyms = PointerUtils.getHolonyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  holonyms");
      e.printStackTrace();
    }

    for (int i = 0; i < pholonyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pholonyms.get(i);
      this.holonyms.add(ptn.getSynset());
    }

  }

  public void setHyponyms() {
    // PointerUtils pointerUtils = PointerUtils.getInstance();
    PointerTargetNodeList phyponyms = new PointerTargetNodeList();
    try {
      phyponyms = PointerUtils.getDirectHyponyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  hyponyms");
      e.printStackTrace();
    }

    for (int i = 0; i < phyponyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) phyponyms.get(i);
      this.hyponyms.add(ptn.getSynset());
    }
  }

  public void setEntailements() {
    // PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList pentailments = new PointerTargetNodeList();
    try {
      pentailments = PointerUtils.getEntailments(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  hypernyms");
      e.printStackTrace();
    }

    for (int i = 0; i < pentailments.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pentailments.get(i);
      this.entailments.add(ptn.getSynset());
    }

  }

  public void setCoordinateTerms() {
    // PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList pcoordinateTerms = new PointerTargetNodeList();
    try {
      pcoordinateTerms = PointerUtils.getCoordinateTerms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  coordinate terms");
      e.printStackTrace();
    }

    for (int i = 0; i < pcoordinateTerms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pcoordinateTerms.get(i);
      this.coordinateTerms.add(ptn.getSynset());
    }

  }

  public void setCauses() {
    // PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList pcauses = new PointerTargetNodeList();
    try {
      pcauses = PointerUtils.getCauses(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the cause terms");
      e.printStackTrace();
    }

    for (int i = 0; i < pcauses.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pcauses.get(i);
      this.causes.add(ptn.getSynset());
    }

  }

  public void setAttributes() {
    // PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList pattributes = new PointerTargetNodeList();
    try {
      pattributes = PointerUtils.getAttributes(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the attributes");
      e.printStackTrace();
    }

    for (int i = 0; i < pattributes.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) pattributes.get(i);
      this.attributes.add(ptn.getSynset());
    }

  }

  public void setPertainyms() {
    // PointerUtils pointerUtils = PointerUtils.get();
    PointerTargetNodeList ppertainyms = new PointerTargetNodeList();
    try {
      ppertainyms = PointerUtils.getPertainyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the pertainyms");
      e.printStackTrace();
    }

    for (int i = 0; i < ppertainyms.size(); i++) {
      PointerTargetNode ptn = (PointerTargetNode) ppertainyms.get(i);
      this.pertainyms.add(ptn.getSynset());
    }

  }

  public void setSynonyms() {
    for (Word word : synset.getWords())
      synonyms.add(new WordPOS(word.toString(), word.getPOS()));
  }

  public ArrayList<Synset> getHypernyms() {
    return hypernyms;
  }

  public ArrayList<Synset> getHyponyms() {
    return hyponyms;
  }

  public ArrayList<Synset> getMeronyms() {
    return meronyms;
  }

  public ArrayList<Synset> getHolonyms() {
    return holonyms;
  }

  public ArrayList<Synset> getEntailments() {
    return entailments;
  }

  public ArrayList<Synset> getCoordinateTerms() {
    return coordinateTerms;
  }

  public ArrayList<Synset> getCauses() {
    return causes;
  }

  public ArrayList<Synset> getAttributes() {
    return attributes;
  }

  public ArrayList<Synset> getPertainyms() {
    return pertainyms;
  }

  public ArrayList<WordPOS> getSynonyms() {
    return synonyms;
  }

  public String getGloss() {
    return this.synset.getGloss().toString();
  }

  public long getSynsetID() {
    return this.synset.getOffset();
  }

  /**
   * Gets the senses of the nodes
   * 
   * @param nodes
   * @return senses from the nodes
   */
  public static ArrayList<WordSense> updateSenses(ArrayList<SynNode> nodes) {
    ArrayList<WordSense> scoredSenses = new ArrayList<WordSense>();

    for (int i = 0; i < nodes.size(); i++) {
      ArrayList<WordPOS> sensesComponents = WSDHelper
          .getAllRelevantWords(WSDHelper.getTokenizer().tokenize(
              nodes.get(i).getGloss()));
      WordSense wordSense = new WordSense();
      nodes.get(i).setSenseRelevantWords(sensesComponents);
      wordSense.setNode(nodes.get(i));
      wordSense.setId(i);
      scoredSenses.add(wordSense);
    }
    return scoredSenses;

  }
}
