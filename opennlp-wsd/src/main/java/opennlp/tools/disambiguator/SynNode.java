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
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.data.list.PointerTargetNode;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import opennlp.tools.tokenize.Tokenizer;

/**
 * Convenience class to access some features.
 */
public class SynNode {

  public Synset parent;
  public final Synset synset;

  private List<WordPOS> senseRelevantWords;

  public final ArrayList<Synset> hypernyms = new ArrayList<>();
  public final ArrayList<Synset> hyponyms = new ArrayList<>();
  public final ArrayList<Synset> meronyms = new ArrayList<>();
  public final ArrayList<Synset> holonyms = new ArrayList<>();
  public final ArrayList<Synset> entailments = new ArrayList<>();
  public final ArrayList<Synset> coordinateTerms = new ArrayList<>();
  public final ArrayList<Synset> causes = new ArrayList<>();
  public final ArrayList<Synset> attributes = new ArrayList<>();
  public final ArrayList<Synset> pertainyms = new ArrayList<>();

  public final ArrayList<WordPOS> synonyms = new ArrayList<>();

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

  public List<WordPOS> getSenseRelevantWords() {
    return senseRelevantWords;
  }

  public void setSenseRelevantWords(List<WordPOS> senseRelevantWords) {
    this.senseRelevantWords = senseRelevantWords;
  }

  public void setHypernyms() {
    PointerTargetNodeList phypernyms = new PointerTargetNodeList();
    try {
      phypernyms = PointerUtils.getDirectHypernyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the hypernyms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : phypernyms) {
      this.hypernyms.add(ptn.getSynset());
    }

  }

  public void setMeronyms() {
    PointerTargetNodeList pmeronyms = new PointerTargetNodeList();
    try {
      pmeronyms = PointerUtils.getMeronyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  meronyms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : pmeronyms) {
      this.meronyms.add(ptn.getSynset());
    }
  }

  public void setHolonyms() {
    PointerTargetNodeList pholonyms = new PointerTargetNodeList();
    try {
      pholonyms = PointerUtils.getHolonyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  holonyms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : pholonyms) {
      this.holonyms.add(ptn.getSynset());
    }

  }

  public void setHyponyms() {
    PointerTargetNodeList phyponyms = new PointerTargetNodeList();
    try {
      phyponyms = PointerUtils.getDirectHyponyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  hyponyms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : phyponyms) {
      this.hyponyms.add(ptn.getSynset());
    }
  }

  public void setEntailements() {
    PointerTargetNodeList pentailments = new PointerTargetNodeList();
    try {
      pentailments = PointerUtils.getEntailments(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  hypernyms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : pentailments) {
      this.entailments.add(ptn.getSynset());
    }

  }

  public void setCoordinateTerms() {
    PointerTargetNodeList pcoordinateTerms = new PointerTargetNodeList();
    try {
      pcoordinateTerms = PointerUtils.getCoordinateTerms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the  coordinate terms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : pcoordinateTerms) {
      this.coordinateTerms.add(ptn.getSynset());
    }

  }

  public void setCauses() {
    PointerTargetNodeList pcauses = new PointerTargetNodeList();
    try {
      pcauses = PointerUtils.getCauses(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the cause terms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : pcauses) {
      this.causes.add(ptn.getSynset());
    }

  }

  public void setAttributes() {
    PointerTargetNodeList pattributes = new PointerTargetNodeList();
    try {
      pattributes = PointerUtils.getAttributes(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the attributes");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : pattributes) {
      this.attributes.add(ptn.getSynset());
    }

  }

  public void setPertainyms() {
    PointerTargetNodeList ppertainyms = new PointerTargetNodeList();
    try {
      ppertainyms = PointerUtils.getPertainyms(this.synset);
    } catch (JWNLException e) {
      e.printStackTrace();
    } catch (NullPointerException e) {
      System.err.println("Error finding the pertainyms");
      e.printStackTrace();
    }

    for (PointerTargetNode ptn : ppertainyms) {
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
    return this.synset.getGloss();
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
    ArrayList<WordSense> scoredSenses = new ArrayList<>();

    final Tokenizer tokenizer = WSDHelper.getTokenizer();
    for (int i = 0; i < nodes.size(); i++) {
      List<WordPOS> sensesComponents = WSDHelper.getAllRelevantWords(tokenizer.tokenize(nodes.get(i).getGloss()));
      WordSense wordSense = new WordSense();
      nodes.get(i).setSenseRelevantWords(sensesComponents);
      wordSense.setNode(nodes.get(i));
      wordSense.setId(i);
      scoredSenses.add(wordSense);
    }
    return scoredSenses;

  }
}
