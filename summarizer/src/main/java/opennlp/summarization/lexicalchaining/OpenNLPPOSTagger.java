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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.processing.Processor;

import opennlp.summarization.DocProcessor;
import opennlp.summarization.preprocess.DefaultDocProcessor;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;

public class OpenNLPPOSTagger implements POSTagger{
	private POSTaggerME tagger;
	private Hashtable<Integer, String[]> tagMap;
	private DocProcessor dp;
	private Logger log;
	
	public OpenNLPPOSTagger(DocProcessor dp, String posModelFileName) throws Exception{
		log = Logger.getLogger("OpenNLPPOSTagger");
		InputStream modelIn = null;
		this.dp = dp;
		initTagMap();
		try {
		  modelIn = new FileInputStream(posModelFileName);
		  POSModel model = new POSModel(modelIn);
		  tagger = new POSTaggerME(model);
		}
		catch (IOException e) {
		   // Model loading failed, handle the error
			e.printStackTrace();
			throw e;
		}
		finally {
		  if (modelIn != null) {
		    try {
		      modelIn.close();
		    }
		    catch (IOException e) {
		    }
		  }
		}			
	}

	private String[] nounTags = {"NN", "NNS","NNP","NNPS"};
	private void initTagMap()
	{
	    tagMap = new Hashtable<Integer, String[]>();		  
		tagMap.put(POSTagger.NOUN, nounTags);
	}

    //Returns true if the typestring belongs to one of the tags for the type..
    public boolean isType(String typeStr, int type)
    {
            boolean ret = false;
            String[] tags = tagMap.get(type);
            for(String tag: tags)
                    if(typeStr.equalsIgnoreCase(tag)) ret = true;

            return ret;
    }
    
	@Override
	public String getTaggedString(String article) {
		return tagger.tag(article);
	}

	@Override
    public List<String> getWordsOfType(String sent, int type)
    {
            List<String> ret = new ArrayList<String>();
            String[] tokens = dp.getWords(sent);
            for(String t:tokens)
            {
                    String[] wordPlusType = t.split("/");
                    if(wordPlusType.length ==2)
                    {
                        if(isType(wordPlusType[1], type))
                            ret.add(wordPlusType[0]);
                    }
            }
            log.info(ret.toString());
            return ret;
    }
}
