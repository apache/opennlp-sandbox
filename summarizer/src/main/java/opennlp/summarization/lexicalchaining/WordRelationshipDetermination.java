/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.summarization.lexicalchaining;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import edu.mit.jwi.data.ILoadPolicy;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.RAMDictionary;

/**
 * Uses wordnet to determine the relation of two words.
 * Words have:
 * <ul>
 * <li>Strong relationship: same word</li>
 * <li>Med relationship: synonym, hyponym</li>
 * <li>Weak relationship: antonym, hypernym</li>
 * <li>No relationship: otherwise</li>
 * </ul>
 */
public class WordRelationshipDetermination {

  private static final String DICTIONARY_FILE = "/wordnet/dict";
  private static final IDictionary DICTIONARY;
  private final Pointer[] rels = {Pointer.ANTONYM, Pointer.HYPERNYM, Pointer.HYPONYM, Pointer.MERONYM_PART,
          Pointer.MERONYM_SUBSTANCE, Pointer.PARTICIPLE, Pointer.HYPERNYM_INSTANCE};
  private final Hashtable<ISynset, List<IWord>> synsetWordCache = new Hashtable<>();

  static {
    DICTIONARY = new RAMDictionary(WordRelationshipDetermination.class.getResource(DICTIONARY_FILE), ILoadPolicy.IMMEDIATE_LOAD);
    ((RAMDictionary) DICTIONARY).load();
    if (!DICTIONARY.isOpen())
      try {
        DICTIONARY.open();
      } catch (IOException e) {
        e.printStackTrace();
      }
  }

  private IWord isSynonym(String noun, Word w) {
    WordnetWord ww = (WordnetWord) w;
    IWord ret;
    IIndexWord idxNoun = DICTIONARY.getIndexWord(noun, POS.NOUN);

    /*
     * getWordIDs() returns all the WordID associated with an index
     *
     */
//		for(IWordID wordID : idxWord.getWordIDs())
    {
      //Construct an IWord object representing word associated with wordID
//			IWord word = dictionary.getWord(wordID);

      //Get the synset in which word is present.
      ISynset wordSynset;
      if (ww.synonyms != null)
        wordSynset = ww.synonyms;
      else {
        IWord word = DICTIONARY.getWord((IWordID) w.getID());
        wordSynset = word.getSynset();
        ww.synonyms = wordSynset;
      }

      IWord syn = inSynset(wordSynset, idxNoun);
      ret = syn;
//				break;
    }
    return ret;
  }

  /*
   * Returns true if the word represented by idxNoun is present in a synset.
   */
  private IWord inSynset(ISynset wordSynset, IIndexWord idxNoun) {
    if (idxNoun == null) {
      return null;
    }

    IWord ret = null;
    List<IWord> wrds;

    //		if(synsetWordCache.get(wordSynset)!=null)
//			wrds = synsetWordCache.get(wordSynset);
//		else{
    wrds = wordSynset.getWords();
//			synsetWordCache.put(wordSynset, wrds);
//		}

    //Returns all the words present in the synset wordSynset
    for (IWord synonym : wrds) {
      for (IWordID nounID : idxNoun.getWordIDs()) {
        if (synonym.equals(DICTIONARY.getWord(nounID))) {
          ret = synonym;
          break;
        }
      }
    }
    return ret;
  }

  //Returns a word if w has a medium strength relationship with noun. Returns null otherwise.
  private Word isMediumRel(String noun, Word w) {
    //	openDict();
    WordnetWord ret = null;
    WordnetWord ww = (WordnetWord) w;
    IWord syn;
    if ((syn = this.isSynonym(noun, w)) != null) {
      ret = new WordnetWord();
      ret.lexicon = noun;
      ret.id = syn.getID();
      ret.wordSense = syn.getSenseKey();
    }

    //Construct an IWord object representing word associated with wordID
    IWord word = DICTIONARY.getWord((IWordID) w.getID());

    IIndexWord idxNoun = DICTIONARY.getIndexWord(noun, POS.NOUN);
    //Get the synset in which word is present.
    ISynset wordSynset = word.getSynset();

    for (Pointer p : rels) {
      List<ISynsetID> rels;
      if (ww.rels.get(p) != null)
        rels = ww.rels.get(p);
      else {
        rels = wordSynset.getRelatedSynsets(p);
        ww.rels.put(p, rels);
      }

      for (ISynsetID id : rels) {
        ISynset s = this.DICTIONARY.getSynset(id);
        IWord mat = inSynset(s, idxNoun);
        if (mat != null) {
          ret = new WordnetWord();
          ret.lexicon = noun;
          ret.id = mat.getID();
          ret.wordSense = mat.getSenseKey();
          break;
        }
      }
      if (ret != null) break;
    }

    return ret;
  }

  /*
   * Returns the type of relation between a lexical chain and the noun. The return value is one of STRONG_RELATION, MEDIUM, WEAK, or NO
   * Strong relation means exact match. Medium relation means synonym or hyponym
   */
  public WordRelation getRelation(LexicalChain l, String noun, boolean checkMed) {
    WordRelation ret = new WordRelation(null, null, WordRelation.NO_RELATION);
    for (Word w : l.word) {
      //Exact match is a string relation.
      if (w.getLexicon().equalsIgnoreCase(noun)) {
        ret = new WordRelation(w, w, WordRelation.STRONG_RELATION);
        break;
      }
      //  else it is a Wordnet word and is it a synonym or hyponym of LCs (medium relation)
      else if (w.getID() != null && checkMed) {
        Word wrel = isMediumRel(noun, w);
        if (wrel != null) {
          ret = new WordRelation(w, wrel, WordRelation.MED_RELATION);
          break;
        }
      }
    }
    return ret;
  }

  public List<Word> getWordSenses(String noun) {
    List<Word> ret = new ArrayList<>();
    try {
      //		openDict();
      List<IWordID> wordIDs = this.DICTIONARY.getIndexWord(noun, POS.NOUN).getWordIDs();
      for (IWordID wid : wordIDs) {
        Word w = new WordnetWord();
        w.setLexicon(noun);
        w.setID(wid);
        ret.add(w);
      }
    } catch (Exception ex) {
      //Not in dictionary
      Word w = new WordnetWord();
      w.setLexicon(noun);
      ret.add(w);
    }
    return ret;
  }
}
