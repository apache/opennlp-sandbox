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
package opennlp.tools.parse_thicket.opinion_processor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.utils.ValueSortMap;
import opennlp.tools.stemmer.PStemmer;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.TextProcessor;

public class LinguisticPhraseManager {
	private Map<String, Integer> freq = new ConcurrentHashMap<String, Integer>();
	
	// the purpose to init this static object is to show the path to resources
	private static StopList stop = StopList.getInstance(new File(".").getAbsolutePath().replace(".","")+ "src/test/resources/");

	// this list will be overwritten by the external synonyms.csv
	private static String[][] synonymPairs = new String[][]{};
	private PStemmer stemmer = new PStemmer();

	private List<ParseTreeChunk> lingPhrases = new ArrayList<ParseTreeChunk>();
	private List<String> standardizedTopics = new ArrayList<String>();
	// map which shows for each ling phrase the list of ling phrases with the same head noun it belongs
	private Map<ParseTreeChunk, List<ParseTreeChunk>> entry_group = new ConcurrentHashMap<ParseTreeChunk, List<ParseTreeChunk>>();

	//  map which shows for each string phrase the list of ling phrases with the same head noun it belongs
	private Map<String, List<ParseTreeChunk>> std_group = new ConcurrentHashMap<String, List<ParseTreeChunk>>();

	private BingQueryRunner runner = new BingQueryRunner();
	private static final int MIN_NUMBER_OF_PHRASES_TO_CONSIDER = 3;//2; 5
	private static final int MIN_LENGTH_OF_WORD_TO_CONSIDER = 3;
	// this function takes a log of a chain of the nodes of parse trees and builds their instances
	// the phrases should only be VP or NP, otherwise an exception should be thrown
	
	

	private String resourceDir;
	public LinguisticPhraseManager(){
		try {
			resourceDir  = new File( "." ).getCanonicalPath()+"/src/main/resources/";
			List<String[]> vocabs = ProfileReaderWriter.readProfiles(resourceDir+"/synonyms.csv");
			synonymPairs = new String[vocabs.size()][2];
			int count = 0;
			for(String[] line: vocabs){
				try {
					synonymPairs[count] = line;
					count++;
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}

	private ParseTreeChunk parseLingPhraseIntoParseTreeChunk(String phrStr){
		ParseTreeChunk ch = new ParseTreeChunk();
		List<String> POSs = new ArrayList<String>(), lemmas = new ArrayList<String>();

		String[] parts = phrStr.replace("]","").split(", <");

		ch.setMainPOS( StringUtils.substringBetween(phrStr, ">", "'"));
		try {
			for(String part: parts){
				String lemma = StringUtils.substringBetween(part, "P'", "':").toLowerCase();
				String pos = part.substring(part.indexOf(":")+1, part.length());

				if (pos==null || lemma ==null){
					continue;
				}
				POSs.add(pos.trim());
				lemmas.add(lemma.trim());
				ch.setPOSs(POSs); ch.setLemmas(lemmas);
			}
		} catch (Exception e) {
			// we expect exceptions if extracted phrases are NEITHER NP nor VP
			// empty chunk will be given which will not create a new topic
			e.printStackTrace();
		}

		return ch;
	}

	// this is a constructor with an array of extraction files
	// optimized for performance
	// only topics occurring more than MIN_NUMBER_OF_PHRASES_TO_CONSIDER times will be considered
	public LinguisticPhraseManager(String[] loadPaths){
		List<String[]> columns = new ArrayList<String[]>();
		for(String file: loadPaths){
			columns.addAll(ProfileReaderWriter.readProfiles( file));
		}

		for(String[] l: columns){
			if (l.length<3 || l[1]==null || l[2]==null)
				continue;
			String word = l[1].toLowerCase().trim();
			if (word.indexOf("=>")>-1)
				continue;

			word = isAcceptableStringPhrase(word);
			if (word==null)
				continue;

			if (!freq.containsKey(word)) {
				freq.put(word, 1);

			} else {
				freq.put(word, freq.get(word) + 1);
				// once we reached the count for a topic, create it
				if (freq.get(word)==MIN_NUMBER_OF_PHRASES_TO_CONSIDER){
					ParseTreeChunk ch = parseLingPhraseIntoParseTreeChunk(l[2]);
					ch = isAcceptableLingPhrase(ch);
					if (ch==null)
						continue;
					lingPhrases.add(ch);
				}
			}		  
		}
		// we dont need frequency data any more
		freq.clear();
	}

	// this is a default constructor with a single topic extraction file
	// not optimized for performance
	public LinguisticPhraseManager(String loadPath){
		List<String[]> columns = ProfileReaderWriter.readProfiles( loadPath);
		for(String[] l: columns){
			if (l.length<3 || l[1]==null || l[2]==null)
				continue;
			String word = l[1].toLowerCase().trim();
			if (word.indexOf("=>")>-1)
				continue;

			word = isAcceptableStringPhrase(word);
			if (word==null)
				continue;

			if (!freq.containsKey(word)) {

				ParseTreeChunk ch = parseLingPhraseIntoParseTreeChunk(l[2]);
				ch = isAcceptableLingPhrase(ch);
				if (ch==null)
					continue;
				freq.put(word, 1);
				lingPhrases.add(ch);
			} else {
				freq.put(word, freq.get(word) + 1);
			}		  


		}
		freq = ValueSortMap.sortMapByValue(freq, false);


	}
	// removing prepositions and articles in case it has not worked at phrase forming stage
	private String isAcceptableStringPhrase(String word) {
		if (word.startsWith("to "))
			return null;
		if (word.startsWith("a "))
			return word.substring(2, word.length());

		if (word.endsWith(" !") || word.endsWith(" ."))
			return word.substring(0, word.length()-2).trim();

		return word;
	}
	// we only accept NP 
	private ParseTreeChunk isAcceptableLingPhrase(ParseTreeChunk ch) {
		if (!ch.getMainPOS().equals("NP"))
			return null;


		return ch;
	}

	// groups are sets of phrases with the same head noun
	// put all phrases in a group. Have a map from each phrase to its group: the list of members
	public void doLingGrouping(){
		for(int i=0; i< lingPhrases.size(); i++){
			for(int j=i+1; j< lingPhrases.size(); j++){
				ParseTreeChunk chI = lingPhrases.get(i);
				ParseTreeChunk chJ = lingPhrases.get(j);
				if (chI.getLemmas().get(chI.getLemmas().size()-1).equals(chJ.getLemmas().get(chJ.getLemmas().size()-1))
						&& chI.getPOSs().get(chI.getLemmas().size()-1).startsWith("NN") ){
					List<ParseTreeChunk> values = null; 
					if( chI.getLemmas().size()<chJ.getLemmas().size()){		

						if (values == null)
							values = new ArrayList<ParseTreeChunk>();
						values.add(chI);
						entry_group.put(chJ, values);
					} else {
						values = entry_group.get(chI);
						if (values == null)
							values = new ArrayList<ParseTreeChunk>();
						values.add(chJ);
						entry_group.put(chI, values);
					}
				}
			}
		}


	}

	public List<String> formStandardizedTopic(){
		Set<ParseTreeChunk> keys = entry_group.keySet();
		for(ParseTreeChunk k: keys){
			List<ParseTreeChunk> lingPhrases = entry_group.get(k);		
			for(int i=0; i< lingPhrases.size(); i++)
				for(int j=i+1; j< lingPhrases.size(); j++){
					ParseTreeChunk chI = lingPhrases.get(i);
					ParseTreeChunk chJ = lingPhrases.get(j);
					List<String> lemmas = new ArrayList<String>(chI.getLemmas());
					lemmas.retainAll(chJ.getLemmas());
					if (lemmas.size()<2)
						continue;
					String buf = ""; List<String> candTopicLst = new ArrayList<String>();
					for(String w: lemmas){
						if (w.length()<MIN_LENGTH_OF_WORD_TO_CONSIDER)
							continue;
						if (!StringUtils.isAlpha(w))
							continue;
						// find POS of w
						boolean bAccept = false;
						for(int iw=0; iw<chI.getLemmas().size(); iw++){
							if (w.equals(chI.getLemmas().get(iw))){
								if (chI.getPOSs().get(iw).startsWith("NN") || chI.getPOSs().get(iw).startsWith("JJ")
										|| chI.getPOSs().get(iw).startsWith("VB"))
									bAccept=true;
							}
						}
						if (bAccept){
							//buf+=w+" ";
							String ws = substituteSynonym(w);
							candTopicLst.add(ws);
						}
					}
					// remove duplicates like 'new new house'
					//candTopicLst = new ArrayList<String>(new HashSet<String>(candTopicLst));
					for(String w: candTopicLst){
						buf+=w+" ";
					}

					buf = buf.trim();
					if (buf.indexOf(' ')<0)
						continue;

					if (!standardizedTopics.contains(buf)){
						standardizedTopics.add(buf);		
						std_group.put(buf, lingPhrases);
					}
				}
		}
		cleanUpStandardizedTopics();

		return standardizedTopics;
	}

	public void cleanUpStandardizedTopics(){
		List<String> toDelete = new ArrayList<String>();
		for(int i=0; i< standardizedTopics.size(); i++)
			for(int j=i+1; j< standardizedTopics.size(); j++){
				List<String> t1 = TextProcessor.fastTokenize(standardizedTopics.get(i), false);
				List<String> t2 = TextProcessor.fastTokenize(standardizedTopics.get(j), false);
				for(int k=0; k< t1.size(); k++){
					t1.set(k, stemmer.stem(t1.get(k)));
				}
				for(int k=0; k< t2.size(); k++){
					t2.set(k, stemmer.stem(t2.get(k)));
				} 
				// check if lists are equal
				if (t1.size()!=t2.size())
					continue;
				//if in two phrases once all keywords are tokenized, one phrase annihilates another, 
				t1.removeAll(t2);
				if (t1.isEmpty()){ 
					if (standardizedTopics.get(i).length()> standardizedTopics.get(j).length()){
						toDelete.add(standardizedTopics.get(i));
						// TODO update std_group entry
						System.out.println("Removing '" + standardizedTopics.get(i) + "' because of '" + standardizedTopics.get(j) );
						List<ParseTreeChunk> stJ = std_group.get(standardizedTopics.get(j));
						stJ.addAll(std_group.get(standardizedTopics.get(i)));
						stJ = new ArrayList<ParseTreeChunk>(new HashSet<ParseTreeChunk>(stJ));
						std_group.put(standardizedTopics.get(j), stJ);
					}
					else {
						toDelete.add(standardizedTopics.get(j));
						System.out.println("Removing '" + standardizedTopics.get(j) + "' because of '" + standardizedTopics.get(i) );
						List<ParseTreeChunk> stI = std_group.get(standardizedTopics.get(i));
						stI.addAll(std_group.get(standardizedTopics.get(j)));
						stI = new ArrayList<ParseTreeChunk>(new HashSet<ParseTreeChunk>(stI));
						std_group.put(standardizedTopics.get(i), stI);
					}

				}
			}
		for(String d: toDelete){
			//System.out.println("Removed '" + d + "'");
			standardizedTopics.remove(d);
		}
	}

	// substitute synonyms according to internal vocab
	private String substituteSynonym(String w) {
		try {
			for(String[] pair: synonymPairs){
				if (w.equals(pair[0]))
					return pair[1];
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return w;
	}

	public void generateGroupingReport(String reportName){
		List<String[]>  report = new ArrayList<String[]>();
		Set<ParseTreeChunk> chs = entry_group.keySet();
		report.add(new String[]{"string phrase" , "class", "linguistic phrase",  "list of ling phrases class representatives"});

		for(ParseTreeChunk ch: chs){
			String head = ch.getLemmas().get(ch.getLemmas().size()-1);
			List<ParseTreeChunk> values = entry_group.get(ch);
			if (values.size()<6)
				head = "";
			report.add(new String[]{ch.toWordOnlyString(), head,  ch.toString(),  values.toString()});
		}
		ProfileReaderWriter.writeReport(report, reportName);
	}

	//final merge floor-floors-flooring as head nound with phrase update
	public void applyLastRoundOfAggregation(){
		//merge <floor - floors - flooring>
		/*
			List<ParseTreeChunk> entries =  new ArrayList<ParseTreeChunk>(entry_group.keySet());
			for(int i=0; i< entries.size(); i++){
				for(int j=i+1; j< entries.size(); j++){
					ParseTreeChunk chI = entries.get(i);
					ParseTreeChunk chJ = entries.get(j);
					String headI = getLastElement(chI.getLemmas());
					String headJ = getLastElement(chJ.getLemmas());
					if (headI==null || headI.length()<MIN_LENGTH_OF_WORD_TO_CONSIDER  || 
							headJ==null || headJ.length()<MIN_LENGTH_OF_WORD_TO_CONSIDER )
						continue;

					if (headI.indexOf(headJ)>-1){
						//leave headJ
						List<ParseTreeChunk> valuesToAddTo = entry_group.get(chJ);
						List<ParseTreeChunk> valuesBeingAdded = entry_group.get(chI);
						if (valuesToAddTo==null || valuesBeingAdded == null)
							continue;
						valuesToAddTo.addAll(valuesBeingAdded);
						entry_group.put(chJ, valuesToAddTo);
						entry_group.remove(chI);
						System.out.println("Deleting entry '"+ headI +"' and moving group to entry '"+ headJ +"'");
					} else if (headJ.indexOf(headI)>-1){
						//leave headJ
						List<ParseTreeChunk> valuesToAddTo = entry_group.get(chI);
						List<ParseTreeChunk> valuesBeingAdded = entry_group.get(chJ);
						if (valuesToAddTo==null || valuesBeingAdded == null)
							continue;
						valuesToAddTo.addAll(valuesBeingAdded);
						entry_group.put(chI, valuesToAddTo);
						entry_group.remove(chJ);
						System.out.println("Deleting entry '"+ headJ +"' and moving group to entry '"+ headI +"'");
					}

				}
			}
		 */
		for(int i = 0; i<standardizedTopics.size(); i++ )
			for(int j = i+1; j<standardizedTopics.size(); j++ ){
				String headI = extractHeadNounFromPhrase(standardizedTopics.get(i));
				String headJ = extractHeadNounFromPhrase(standardizedTopics.get(j));
				// if the same word do nothing
				if (headI.equals(headJ))
					continue;

				//only if one is sub-word of another
				if (headI.indexOf(headJ)>-1){

					if (!properSubWordForm(headI, headJ))
						continue;
					//entry 'I' will be updated
					String newKey = standardizedTopics.get(i).replace(headI, headJ);

					List<ParseTreeChunk> stI = std_group.get(standardizedTopics.get(i));
					List<ParseTreeChunk> stInew = std_group.get(newKey);
					//if (stInew!=null && !stInew.isEmpty())
					//	stI.addAll(stInew);
					if(stI==null)
						continue;
					std_group.put(newKey, stI);
					std_group.remove(standardizedTopics.get(i));
					System.out.println("Deleted entry for key '"+ standardizedTopics.get(i) +"' and created  '"+ newKey +"'");
					standardizedTopics.set(i, newKey);

				} else if (headJ.indexOf(headI)>-1){
					if (!properSubWordForm(headJ, headI))
						continue;
					//entry 'J' will be updated
					String newKey = standardizedTopics.get(j).replace(headJ, headI);

					List<ParseTreeChunk> stJ = std_group.get(standardizedTopics.get(j));
					List<ParseTreeChunk> stJnew = std_group.get(newKey);
					//if (stJnew!=null && !stJnew.isEmpty())
					//	stJ.addAll(stJnew);
					if(stJ==null)
						continue;
					std_group.put(newKey, stJ);
					std_group.remove(standardizedTopics.get(j));
					System.out.println("Deleted entry for key '"+ standardizedTopics.get(j) +"' and created  '"+ newKey +"'");
					standardizedTopics.set(j, newKey);
				}
			}



	}

	private boolean properSubWordForm(String headI, String headJ) {
		String suffix = headI.replace(headJ, "");
		if (suffix.equals("s") || suffix.equals("ing") //|| suffix.equals("er") 
				|| suffix.equals("rooms") ||
				suffix.equals("") || suffix.equals("counter") ||
				suffix.equals("room") || suffix.equals("back"))
			return true;

		//System.out.println("Wrong word '"+ headI + "'reduction into '" + headJ +"'");
		return false;
	}

	//generates report 
	public void generateStdTopicReport(String reportName){
		List<String[]>  report = new ArrayList<String[]>();
		report.add(new String[]{"category", "topic", "sub-topics", "phrase instances" });

		for(String t: standardizedTopics){

			String bufCover = "";
			int count = 0;
			List<ParseTreeChunk> ptcList = std_group.get(t);
			if (ptcList == null)
				continue;
			for(ParseTreeChunk ch: ptcList){
				List<String> candidate = TextProcessor.fastTokenize(ch.toWordOnlyString(), false);
				List<String> tList = TextProcessor.fastTokenize(t, false);
				List<String> tListChk = new ArrayList<String>(tList);

				tListChk.removeAll(candidate);
				// fully covered by phrase instance
				if (!tListChk.isEmpty() || ch.toWordOnlyString().equals(t)){
					continue;
				}

				boolean bCovered = true;
				
				for(String ts: tList){
					boolean bCandWordsIsCovered = false;
					for(String s: candidate){
						if ((s.indexOf(ts)>-1) )//  && properSubWordForm(s, ts))
							bCandWordsIsCovered = true;
					}
					if (!bCandWordsIsCovered){
						bCovered = false;
						break;
					}
				}
				if (!bCovered)
					continue;
				bufCover+=ch.toWordOnlyString()+ " # ";
				count++;
				if (count > 40)
					break;

			}
			if (bufCover.endsWith(" # "))
				bufCover = bufCover.substring(0, bufCover.length()-3).trim();

			String buf = "";
			count = 0;
			// only up to 40 instances of phrases per 1-st level topic
			for(ParseTreeChunk ch: ptcList){
				buf+=ch.toWordOnlyString()+ "|";
				count++;
				if (count > 40)
					break;
			}
			
			//TODO uncomment
			//t = spell.getSpellCheckResult(t);
			report.add(new String[]{extractHeadNounFromPhrase(t), t, bufCover, buf //, std_group.get(t).toString()
			});
		}
		
		
		ProfileReaderWriter.writeReport(report, reportName);
	}
	// get a last word from a phrase (supposed to be a head noun)
	private String extractHeadNounFromPhrase(String topic){
		String[] tops = topic.split(" ");
		int len = tops.length;
		if (len>1){
			return tops[len-1];
		}
		else return topic;
	}

	// get last elem of a list
	private String getLastElement(List<String> arrayList ){
		if (arrayList != null && !arrayList.isEmpty()) {
			return arrayList.get(arrayList.size()-1);
		}
		return null;
	}
	/*
	 * Using Bing API to check if an extracted phrase can be found on the web, therefore is a meaningful phrase 
	 */
	public List<String> verifyTopic(){
		Set<String> phrases = freq.keySet();
		List<String> approvedPhrases = new ArrayList<String>();
		for(String p: phrases){
			List<HitBase> hits = runner.runSearch("\""+p+"\"");
			for(HitBase h: hits){
				String lookup = h.getTitle() + " " + h.getAbstractText();
				if (lookup.indexOf(p)>-1){
					approvedPhrases.add(p);
					break;
				}
			}
		}
		return approvedPhrases;
	}

	public Set<String> getPhraseLookup(){
		return freq.keySet();
	}

	// using phrase frequency to filter phrases
	public boolean isAcceptablePhrase(String phrase){
		Integer count = freq.get(phrase.toLowerCase().trim());
		if (count==null)
			return false;

		if (count>0 && count < 10000)
			return true;
		return false;
	}

	public static void main(String[] args){
		LinguisticPhraseManager man = new  LinguisticPhraseManager(
				"/Users/bgalitsky/Documents/workspace/move_com/phrasesOfInterest.csv");
		man.doLingGrouping();
		man.generateGroupingReport("topics_groups7_mergedHeads.csv");
		List<String> stdTopics = man.formStandardizedTopic();
		man.applyLastRoundOfAggregation();
		man.generateStdTopicReport("std_topics7_mergedHeads.csv");
		System.out.println(stdTopics);

	}
}
