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

import java.io.File;
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
import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.RAMDictionary;

/*
 * Uses wordnet to determine the relation of two words.
 * Words have -
 * 	strong relationship: same word
 *  Med relationship: synonym, hyponym
 *  weak relationship: antonym, hypernym..
 *  No relationship: otherwise
 */
public class WordRelationshipDetermination {

	IDictionary dictionary;
	String dictionaryFile="resources/wordnet/dict";
	int MAX_DIST_MED_REL = 1000;
	
	public WordRelationshipDetermination() throws Exception
	{
		dictionary = new RAMDictionary(new File(dictionaryFile), ILoadPolicy.IMMEDIATE_LOAD);
		((RAMDictionary)dictionary).load();
		openDict();
	}
	
	private IWord isSynonynm(String noun, Word w)
	{
		WordnetWord ww = (WordnetWord)w;
		IWord ret = null;
		IIndexWord idxNoun = dictionary.getIndexWord(noun, POS.NOUN);
		
		/*getWordIDs() returns all the WordID associated with a index 
		 * 
		 */		
//		for(IWordID wordID : idxWord.getWordIDs())
		{
			//Construct an IWord object representing word associated with wordID  
//			IWord word = dictionary.getWord(wordID);
			
			//Get the synset in which word is present.
			ISynset wordSynset = null;
			if(ww.synonyms!=null)
				wordSynset = ww.synonyms;
			else{
				IWord word = dictionary.getWord((IWordID)w.getID());
				wordSynset = word.getSynset();				
				ww.synonyms = wordSynset;
			}
			IWord syn = inSynset(wordSynset, idxNoun);
			if(w!=null){
				ret = syn;
//				break;
			}
		}
		return ret;
	}
	/*
	 * Returns true if the word represented by idxNoun is present in a synset.. 	
	 */
	Hashtable<ISynset, List<IWord>> synsetWordCache = new Hashtable<ISynset, List<IWord>>();
	private IWord inSynset(ISynset wordSynset, IIndexWord idxNoun)
	{
		IWord ret = null;
		List<IWord> wrds = null;

		//		if(synsetWordCache.get(wordSynset)!=null)
//			wrds = synsetWordCache.get(wordSynset);
//		else{
			wrds = wordSynset.getWords();
//			synsetWordCache.put(wordSynset, wrds);
//		}
			
		//Returns all the words present in the synset wordSynset
		for(IWord synonym : wrds)
		{
			for(IWordID nounID : idxNoun.getWordIDs())
			{
				if(synonym.equals(dictionary.getWord(nounID)))
				{
					ret = synonym;
					break;
				}
			}
		}
		return ret;
	}
	
	Pointer[] rels = {Pointer.ANTONYM, Pointer.HYPERNYM, Pointer.HYPONYM, Pointer.MERONYM_PART, 
			Pointer.MERONYM_SUBSTANCE, Pointer.PARTICIPLE, Pointer.HYPERNYM_INSTANCE};
	Hashtable<ISynsetID, ISynset> cache = new Hashtable<ISynsetID, ISynset>();
	//Returns a word if w has a medium strength relationship with noun. Returns null otherwise.
	private Word isMediumRel(String noun, Word w)
	{
	//	openDict();
		WordnetWord ret = null;
		WordnetWord ww = (WordnetWord) w;
		IWord syn = null;
		if((syn = this.isSynonynm(noun, w))!=null) {
			ret = new WordnetWord();
			ret.lexicon = noun;
			ret.id = syn.getID();
			ret.wordSense = syn	.getSenseKey();			
		}
		
		//Construct an IWord object representing word associated with wordID  
		IWord word = dictionary.getWord((IWordID)w.getID());

		IIndexWord idxNoun = dictionary.getIndexWord(noun, POS.NOUN);
		//Get the synset in which word is present.
		ISynset wordSynset = word.getSynset();
		
		for(Pointer p : rels)
		{
			
			List<ISynsetID> rels = null;
			if(ww.rels.get(p)!=null)
				rels = ww.rels.get(p);
			else{
				rels = wordSynset.getRelatedSynsets(p);
				ww.rels.put(p, rels);
			}
			
			for(ISynsetID id: rels)
			{				
				ISynset s = this.dictionary.getSynset(id);				
				IWord mat = inSynset(s, idxNoun);
				if(mat!=null)
				{
					ret = new WordnetWord();
					ret.lexicon = noun;
					ret.id = mat.getID();
					ret.wordSense = mat.getSenseKey();
					break;
				}
			}
			if(ret!=null) break;
		}

		return ret;
	}
	
	/*
	 * Returns the type of relation between a lexical chain and the noun. The return value is one of STRONG_RELATION, MEDIUM, WEAK, or NO
	 * Strong relation means exact match. Medium relation means synonym or hyponym
	 */
	public WordRelation getRelation(LexicalChain l, String noun, boolean checkMed) throws Exception{
		WordRelation ret = new WordRelation();
		ret.relation = ret.NO_RELATION;
		for(Word w : l.word)
		{
			//Exact match is a string relation..
			if(w.getLexicon().equalsIgnoreCase(noun)) 
			{
				ret.relation = WordRelation.STRONG_RELATION;
				ret.src = w;
				ret.dest = w;
				break;
			}
			 //  else it is a Wordnet word and is it a synonym or hyponym of LCs (medium relation)
			else if(w.getID()!=null && checkMed){
				Word wrel = isMediumRel(noun, w) ;
				if(wrel!=null)
				{
					ret.relation = WordRelation.MED_RELATION;
					ret.src = w;
					ret.dest = wrel;
					break;
				}						
			}					
		}
		return ret;
	}
	
	private void openDict()
	{
		if(!dictionary.isOpen())
			try {
				dictionary.open();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
	}
	public List<Word> getWordSenses(String noun)
	{
		List<Word> ret = new ArrayList<Word>();
		try{
	//		openDict();
			List<IWordID> wordIDs = this.dictionary.getIndexWord(noun, POS.NOUN).getWordIDs();
			for(IWordID wid: wordIDs)
			{
				Word w = new WordnetWord();
				w.setLexicon(noun);
				w.setID(wid);
				ret.add(w);
			}
		}catch(Exception ex){
			// ex.printStackTrace();
			//Not in dictionary
			Word w = new WordnetWord();
			w.setLexicon(noun);
			ret.add(w);
		}
		return ret;
	}
}
