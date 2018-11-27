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

package opennlp.summarization.preprocess;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.BreakIterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.summarization.Sentence;
import opennlp.summarization.DocProcessor;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.Stemmer;


/*
 * Parse document to sentences..
 */
public class DefaultDocProcessor implements DocProcessor
{
	SentenceModel sentModel;
	Stemmer stemmer;
	StopWords sw;
	//Sentence fragmentation to use..
    static int OPEN_NLP = 1;
    static int SIMPLE = 2; 
    static int SENTENCE_FRAG= OPEN_NLP;

    public DefaultDocProcessor(String fragModelFile){
		try {
			InputStream modelIn = new FileInputStream(fragModelFile);
			sentModel = new SentenceModel(modelIn);        	
        }catch(Exception ex){
			Logger.getAnonymousLogger().info("Error while parsing.. Ignoring the line and marching on.. "+ ex.getMessage());
        }
	}
	
	//Str - Document or para
	//sentences - List containing returned sentences
	// iidx - if not null update with the words in the sentence + sent id
	// processedSent - Sentences after stemming and stopword removal..
    private void getSentences(String str, List<String> sentences, Hashtable<String, List<Integer>> iidx, List<String> processedSent)
    {    	
		int oldSentEndIdx = 0;
		int sentEndIdx = 0;
	    Stemmer stemmer = new PorterStemmer();
	    StopWords sw = StopWords.getInstance();      
	    BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
	    BreakIterator wrdItr = BreakIterator.getWordInstance(Locale.US);
	    iterator.setText(str);
	    int start = iterator.first();
	    int sentCnt = 0;
	
	    for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) 
	    {
			String sentence = str.substring(start,end);//str.substring(oldSentEndIdx, sentEndIdx).trim();

			//Add the sentence as-is; do any processing at the word level..
			//To lower case and trim all punctuations			
			sentences.add(sentence);
			wrdItr.setText(sentence);	
			StringBuffer procSent = new StringBuffer();
			int wrdStrt = 0;
			
			for(int wrdEnd = wrdItr.next(); wrdEnd != BreakIterator.DONE; 
					wrdStrt = wrdEnd, wrdEnd = wrdItr.next())
			{
				String word = sentence.substring(wrdStrt, wrdEnd);//words[i].trim();
				word.replaceAll("\"|'","");

				//Skip stop words and stem the word..
				if(sw.isStopWord(word)) continue;                        
	            
				String stemedWrd = stemmer.stem(word).toString();
				
				//update iidx by adding the current sentence to the list..
				if(iidx!=null)
				{
					if(stemedWrd.length()>1)
					{						
						List<Integer> sentList= iidx.get(stemedWrd);
						if(sentList==null)
						{
							sentList = new ArrayList<Integer>();
						}
				
						sentList.add(sentCnt);
						//Save it back
						iidx.put(stemedWrd, sentList);
					}
				}		
				procSent.append(stemedWrd+" ");                           
			}

			sentCnt++;
			if(processedSent!=null )
				processedSent.add(procSent.toString());
	    }
    }
    
    
    public String docToString(String fileName)
    {
    	LineNumberReader lnr = null;
        StringBuffer docBuffer = new StringBuffer();
        
        try {	 	
            lnr = new LineNumberReader(new FileReader(fileName));
            String nextLine;
            
            while ((nextLine = lnr.readLine()) != null) {
                String trimmedLine = nextLine.trim();
                if (!trimmedLine.isEmpty() ) {
                    docBuffer.append(trimmedLine.replaceAll("&#?[0-9 a-z A-Z][0-9 a-z A-Z][0-9 a-z A-Z]?;", "")+" ");
                }
            }	    
        } catch (Exception ex) {
            Logger.getLogger(DefaultDocProcessor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                lnr.close();
            } catch (IOException ex) {
                Logger.getLogger(DefaultDocProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        return docBuffer.toString();
    }
   
    //List of sentences form a document
    public List<Sentence> docToSentList(String fileName)
    {
    	List<Sentence> sentList = new ArrayList<Sentence>();
    	LineNumberReader lnr = null;
        StringBuffer docBuffer = new StringBuffer();
        
        try {	 	
            lnr = new LineNumberReader(new FileReader(fileName));
            String nextLine;
            int paraNo =0;
            int sentNo = 0;
            while ((nextLine = lnr.readLine()) != null) {
                String trimmedLine = nextLine.trim();
                if (!trimmedLine.isEmpty()) {
                	List<String> sents = new ArrayList<String>();
                	List<String> cleanedSents = new ArrayList<String>();
                	this.getSentences(trimmedLine, sents, null, cleanedSents);	
                	int paraPos = 1;
                	for(String sen:sents)
                	{
                		Sentence s = new Sentence();
                		s.setSentId(sentNo++);
                		s.setParagraph(paraNo);
                		s.setStringVal(sen);
                		s.setParaPos(paraPos++);
                		sentList.add(s);
                	}
                	paraNo++;
                }
            }
            
            String doc = docBuffer.toString(); 	
        } catch (Exception ex) {
            Logger.getLogger(DefaultDocProcessor.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        } finally {
            try {
                lnr.close();
            } catch (IOException ex) {
                Logger.getLogger(DefaultDocProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

	return sentList;
    }

    
	public List<Sentence> getSentencesFromStr(String text) {
		List<Sentence> ret = new ArrayList<Sentence>();

		List<String> sentStrs = new ArrayList<String>();
        List<String> cleanedSents = new ArrayList<String>();
        
        //Custom/simple method if specified or open nlp model was not found..
        if(sentModel==null || SENTENCE_FRAG==SIMPLE)
               getSentences(text, sentStrs, null, cleanedSents);
        else{
        	SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentModel);
        	String[] sentences = sentenceDetector.sentDetect(text);
        	for(String sentence : sentences)
        	{
        		Logger.getLogger("DocProcessor").info(sentence);
            	sentStrs.add(sentence);
        	}
        }
        int sentNo = 0;
        
        for(String sen:sentStrs)
    	{        	
        	Sentence s = new Sentence();
    		s.setSentId(sentNo);
    		s.setParagraph(1);
    		s.setStringVal(sen);
    		s.setParaPos(sentNo);
    		ret.add(s);
    		sentNo++;
    	}
		return ret;
	}    
	

	public String[] getWords(String sent)
	{
		return sent.split(" ");
	}

	@Override
	public Stemmer getStemmer() {
		// TODO Auto-generated method stub
		return stemmer;
	}

}