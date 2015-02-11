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

import java.util.Hashtable;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;

import com.sun.istack.internal.logging.Logger;
	
/*
 * Class to load inverse document frequency for words. Resources like google n-gram can be used to populate this.
 * 
 */
public class IDFWordWeight implements WordWeight
{
	Hashtable<String, Double> idf;
	private static IDFWordWeight instance;
	
	public IDFWordWeight(String fileName)
	{
		idf = new Hashtable<String,Double>();
		load(fileName);
	}

	public static IDFWordWeight getInstance(String fileName)
	{
		if(instance==null) 
			instance = new IDFWordWeight(fileName);
		return instance;
	}
	
	public double getWordWeight(String s)
	{
		if(idf==null) return 1d;		
		
		Double d = idf.get(s);
		if(d == null)
		{
			   return 1;
		}
	    return d.doubleValue();
	}

	/*
	 * Loads the IDF for words from given file. The file is required to have a simple format -
	 * word, IDF. 
	 */
	public void load(String fileName)
	{
		try{
		    LineNumberReader lnr = new LineNumberReader(new FileReader(fileName));
		    String nextLine;
		    
		    while ((nextLine = lnr.readLine()) != null) 
		    {
		        String trimmedLine = nextLine.trim();
		        if (!trimmedLine.isEmpty()) 
		        {
				  String[] tokens = trimmedLine.split(",");
				  String word = tokens[0]; double idfVal = Double.parseDouble(tokens[1]);
				  idf.put(word, idfVal); 
		        }
		    }   	
		}catch(Exception ex){
			Logger.getLogger(opennlp.summarization.preprocess.IDFWordWeight.class).warning("Could not load the file with IDF");
		}
	}	
}
