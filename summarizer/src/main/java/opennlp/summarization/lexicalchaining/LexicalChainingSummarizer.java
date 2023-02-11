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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import opennlp.summarization.DocProcessor;
import opennlp.summarization.Sentence;
import opennlp.summarization.Summarizer;

/**
 * Implements the algorithm outlined in - "Summarization Using Lexical Chains" by R. Berzilay et al.
 * <p>
 * The algorithm is based on extracting so-called lexical chains - a set of sentences in the article
 * that share a word that are very closely related. Thus, the longest chain represents the most important
 * topic and so forth. A summary can then be formed by identifying the most important lexical chains
 * and "pulling" out sentences from them.
 */
public class LexicalChainingSummarizer implements Summarizer{

  private POSTagger tagger;
  private DocProcessor dp;
  private final WordRelationshipDetermination wordRel;

  public LexicalChainingSummarizer(DocProcessor dp, InputStream posModelFile) throws Exception {
    wordRel = new WordRelationshipDetermination();
    tagger = new OpenNLPPOSTagger(dp, posModelFile);
  }

  //Build Lexical chains..
  public List<LexicalChain> buildLexicalChains(String article, List<Sentence> sent) {
    // POS tag article
    Hashtable<String, List<LexicalChain>> chains = new Hashtable<>();
    List<LexicalChain> lc = new ArrayList<>();
    // Build lexical chains
    // For each sentence
    for(Sentence currSent : sent) {
      String taggedSent = tagger.getTaggedString(currSent.getStringVal());
      List<String> nouns = tagger.getWordsOfType(taggedSent, POSTagger.NOUN);
      // 	For each noun
      for(String noun : nouns) {
        int chainsAddCnt = 0;
        //  Loop through each LC
        for(LexicalChain l: lc) {
          try {
            WordRelation rel = wordRel.getRelation(l, noun, (currSent.getSentId() - l.start)>7);
            //  Is the noun an exact match to one of the current LCs (Strong relation)
            //  Add sentence to chain
            if(rel.relation == WordRelation.STRONG_RELATION) {
              addToChain(rel.dest, l, chains, currSent);
              if(currSent.getSentId() - l.last > 10)
              {
                l.occurrences++; l.start = currSent.getSentId();
              }
              chainsAddCnt++;
            }
            else if(rel.relation == WordRelation.MED_RELATION) {
              //  Add sentence to chain if it is 7 sentences away from start of chain
              addToChain(rel.dest, l, chains, currSent);
              chainsAddCnt++;
              //If greater than 7 we will add it but call it a new occurrence of the lexical chain...
              if(currSent.getSentId() - l.start > 7)
              {
                l.occurrences++;
                l.start = currSent.getSentId();
              }
            }
            else if(rel.relation == WordRelation.WEAK_RELATION) {
              if(currSent.getSentId() - l.start <= 3) {
                addToChain(rel.dest, l, chains, currSent);
                chainsAddCnt++;
              }
            }
          } catch(Exception ex){}
          // add sentence and update last occurrence..
          //chaincnt++
          //  else 1 hop-relation in Wordnet (weak relation)
          //  Add sentence to chain if it is 3 sentences away from start of chain
          //chaincnt++
          // End loop LC
        }
        //Could not add the word to any existing list. Start a new lexical chain with the word.
        if(chainsAddCnt==0) {
          List<Word> senses = wordRel.getWordSenses(noun);
          for(Word w : senses) {
            LexicalChain newLc = new LexicalChain();
            newLc.start = currSent.getSentId();
            addToChain(w, newLc, chains, currSent);
            lc.add(newLc);
          }
        }
        if(lc.size()> 20)
          purge(lc, currSent.getSentId(), sent.size());
      }
      //End sentence
    }

//			disambiguateAndCleanChains(lc, chains);
    // Calculate score
    //	Length of chain * homogeneity
    //sort LC by strength.
    return lc;
  }

  /*
   * A way to manage the number of lexical chains generated. Expire very small lexical chains.
   * Takes care to only remove small chains that were added "long back"
   */
  private void purge(List<LexicalChain> lc, int sentId, int totSents) {
    //Do nothing for the first 50 sentences.
    if(lc.size()<20 ) return;

    Collections.sort(lc);
    double min = lc.get(0).score();
    double max = lc.get(lc.size()-1).score();

    int cutOff = Math.max(3, (int)min);
    Hashtable<String, Boolean> words = new Hashtable<>();
    List<LexicalChain> toRem = new ArrayList<>();
    for(int i=lc.size()-1; i>=0;i--)
    {
      LexicalChain l = lc.get(i);
      if(l.score() < cutOff && (sentId - l.last) > totSents/3)//	 && containsAllWords(words, l.word))
        toRem.add(l);
        //A different sense and added long back.
      else if(words.containsKey(l.getWord().get(0).getLexicon()) && (sentId - l.start) > totSents/10)
        toRem.add(l);
      else
      {
        //Check if this is from a word with different sense..
        for(Word w: l.word)
          words.put(w.getLexicon(), Boolean.TRUE);
      }
    }

    for(LexicalChain l: toRem)
      lc.remove(l);
  }

  private boolean containsAllWords(Hashtable<Word, Boolean> words,
                                   List<Word> word) {
    boolean ret = true;
    for(Word w: word)
      if(!words.containsKey(w)) return false;

    return ret;
  }

  private void addToChain(Word noun, LexicalChain l,
                          Hashtable<String, List<LexicalChain>> chains, Sentence sent) {

    l.addWord(noun);
    l.addSentence(sent);
    l.last = sent.getSentId();
    if(!chains.contains(noun))
      chains.put(noun.getLexicon(), new ArrayList<>());
    chains.get(noun.getLexicon()).add(l);
  }

  POSTagger getTagger() {
    return tagger;
  }

  void setTagger(POSTagger tagger) {
    this.tagger = tagger;
  }

  @Override
  public String summarize(String article, DocProcessor dp, int maxWords) {
    List<Sentence> sent = dp.getSentencesFromStr(article);
    List<LexicalChain> lc = buildLexicalChains(article, sent);
    Collections.sort(lc);
    int summSize=0;
    List<Sentence>summ = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    for(int i=0;i<lc.size();i++) {
      for(int j=0;j<lc.size();j++) {
        Sentence candidate = lc.get(i).sentences.get(j);
        if(!summ.contains(candidate)) {
          summ.add(candidate);
          sb.append(candidate.getStringVal());
          summSize += candidate.getWordCnt();
          break;
        }
      }
      if(summSize>=maxWords) break;
    }
    return sb.toString();
  }

}
