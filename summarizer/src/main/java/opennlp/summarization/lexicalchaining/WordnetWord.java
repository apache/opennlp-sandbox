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

import java.util.Hashtable;
import java.util.List;

import edu.mit.jwi.item.IPointer;
import edu.mit.jwi.item.ISenseKey;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWordID;

public class WordnetWord implements Word{
	String lexicon;
	ISenseKey wordSense;
	IWordID id;
	
	//Cache..
	ISynset synonyms;
	Hashtable<IPointer, List<ISynsetID>>rels;
	
	public WordnetWord()
	{
		rels = new Hashtable<IPointer, List<ISynsetID>>();
	}
	
	@Override
	public String getLexicon() {
		return lexicon;
	}

	@Override
	public Object getSense() {
		return wordSense;
	}

	@Override
	public Object getID() {
		return id;
	}

	@Override
	public void setLexicon(String lex) {
		this.lexicon = lex;
	}

	@Override
	public void setSense(Object senseID) {
		this.wordSense = (ISenseKey) senseID;
	}

	@Override
	public void setID(Object id) {
		this.id = (IWordID)id;
	}
	
	@Override
	public String toString()
	{
		return this.lexicon;
	}
	
	@Override
	public int hashCode()
	{
		return toString().hashCode();
	}
}
