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

package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.IGeneralizer;
import opennlp.tools.parse_thicket.ParseTreeNode;
import opennlp.tools.stemmer.PStemmer;
import opennlp.tools.textsimilarity.GeneralizationListReducer;
import opennlp.tools.textsimilarity.LemmaFormManager;
import opennlp.tools.textsimilarity.POSManager;
import opennlp.tools.textsimilarity.ParseTreeChunk;

public class PhraseGroupGeneralizer implements IGeneralizer<List<ParseTreeChunk>>{

  private GeneralizationListReducer generalizationListReducer = new GeneralizationListReducer();

  private LemmaFormManager lemmaFormManager = new LemmaFormManager();

  private POSManager posManager = new POSManager();

  private PhraseGeneralizer pGen = new PhraseGeneralizer();
  private NERPhraseGeneralizer pGenNER = new NERPhraseGeneralizer();

  /**
   * main function to generalize two expressions grouped by phrase types returns
   * a list of generalizations for each phrase type with filtered
   * sub-expressions
   * 
   * @param sent1
   * @param sent2
   * @return List<List<ParseTreeChunk>> list of list of POS-words pairs for each
   *         resultant matched / overlapped phrase
   */
  @Override
  public List<List<ParseTreeChunk>> generalize(Object o1, Object o2) {
	  
  
      List<List<ParseTreeChunk>> sent1 = (List<List<ParseTreeChunk>>)o1, 
    	 sent2 = (List<List<ParseTreeChunk>>) o2 ;
    List<List<ParseTreeChunk>> results = new ArrayList<List<ParseTreeChunk>>();
    // first iterate through component
    for (int comp = 0; comp < 2 && // just np & vp
        comp < sent1.size() && comp < sent2.size(); comp++) {
      List<ParseTreeChunk> resultComps = new ArrayList<ParseTreeChunk>();
      // then iterate through each phrase in each component
      // first try lemma-based alignment
      for (ParseTreeChunk ch1 : sent1.get(comp)) {
        for (ParseTreeChunk ch2 : sent2.get(comp)) { // simpler version
          List<ParseTreeChunk> chunkToAdd=null;
		try {
			chunkToAdd = pGen.generalize(ch1, ch2);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
          if (chunkToAdd == null){
        	     chunkToAdd = new ArrayList<ParseTreeChunk>();
          }       
          Boolean alreadyThere = false;
          for (ParseTreeChunk chunk : resultComps) {
            if (chunkToAdd.contains(chunk)) {
              alreadyThere = true;
              break;
            }
          }

          if (!alreadyThere && chunkToAdd != null && chunkToAdd.size() > 0) {
            resultComps.addAll(chunkToAdd);
          }

        }
      } // then try NER-based alignment
      if (comp==0 || resultComps.size()<1){
    	  for (ParseTreeChunk ch1 : sent1.get(comp)) {
    	        for (ParseTreeChunk ch2 : sent2.get(comp)) { // simpler version
    	          List<ParseTreeChunk> chunkToAdd = pGenNER.generalize(
    	              ch1, ch2);

    	          if (chunkToAdd == null){
    	        	   chunkToAdd = new ArrayList<ParseTreeChunk>();
    	          }
    	         
    	          Boolean alreadyThere = false;
    	          for (ParseTreeChunk chunk : resultComps) {
    	            if (chunkToAdd.contains(chunk)) {
    	              alreadyThere = true;
    	              break;
    	            }
    	          }

    	          if (!alreadyThere && chunkToAdd != null && chunkToAdd.size() > 0) {
    	            resultComps.addAll(chunkToAdd);
    	          }

    	        }
    	      }
      }
      
      List<ParseTreeChunk> resultCompsRed = generalizationListReducer.applyFilteringBySubsumption(resultComps);

      resultComps = resultCompsRed;
      results.add(resultComps);
    }

    return results;
  }

  

}
