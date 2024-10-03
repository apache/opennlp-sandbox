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

package opennlp.tools.coref.dictionary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.Pointer;
import net.sf.extjwnl.data.PointerType;
import net.sf.extjwnl.data.Synset;

/**
 * An implementation of the Dictionary interface using the JWNL library.
 *
 * @see Dictionary
 */
public class JWNLDictionary implements Dictionary {

  private final net.sf.extjwnl.dictionary.Dictionary dict;
  private final net.sf.extjwnl.dictionary.MorphologicalProcessor morphy;
  private static final String[] EMPTY = new String[0];

  public JWNLDictionary() throws IOException, JWNLException {
    dict = net.sf.extjwnl.dictionary.Dictionary.getDefaultResourceInstance();
    morphy = dict.getMorphologicalProcessor();
  }

  @Override
  public String[] getLemmas(String word, String tag) {
    try {
      POS pos;
      if (tag.startsWith("N") || tag.startsWith("n")) {
        pos = POS.NOUN;
      }
      else if (tag.startsWith("V") || tag.startsWith("v")) {
        pos = POS.VERB;
      }
      else if (tag.startsWith("J") || tag.startsWith("a")) {
        pos = POS.ADJECTIVE;
      }
      else if (tag.startsWith("R") || tag.startsWith("r")) {
        pos = POS.ADVERB;
      }
      else {
        pos = POS.NOUN;
      }
      List<String> lemmas = morphy.lookupAllBaseForms(pos,word);
      return lemmas.toArray(new String[0]);
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public String getSenseKey(String lemma, String pos,int sense) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw == null) {
        return null;
      }
      return String.valueOf(iw.getSynsetOffsets()[sense]);
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public int getNumSenses(String lemma, String pos) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw == null) {
        return 0;
      }
      return iw.getSynsetOffsets().length;
    }
    catch (JWNLException e) {
      return 0;
    }
  }

  private void getParents(Synset synset, List<String> parents) throws JWNLException {
    List<Pointer> pointers = synset.getPointers();
    for (Pointer pointer : pointers) {
      if (pointer.getType() == PointerType.HYPERNYM) {
        Synset parent = pointer.getTargetSynset();
        parents.add(String.valueOf(parent.getOffset()));
        getParents(parent, parents);
      }
    }
  }

  @Override
  public String[] getParentSenseKeys(String lemma, String pos, int sense) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw != null) {
        Synset synset = iw.getSenses().get(sense);
        List<String> parents = new ArrayList<>();
        getParents(synset,parents);
        return parents.toArray(new String[0]);
      }
      else {
        return EMPTY;
      }
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }
}
