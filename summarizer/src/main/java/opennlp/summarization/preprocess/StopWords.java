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

/**
 *
 * @author rtww
 */
public class StopWords {
    private Hashtable<String, Boolean> h;
    private static StopWords instance;
 	
    public StopWords()
    {
        h = new Hashtable<String, Boolean>();
        h.put("0", true);
        h.put("1", true);
        h.put("2", true);
        h.put("3", true);
        h.put("4", true);
        h.put("5", true);
        h.put("6", true);
        h.put("7", true);
        h.put("8", true);
        h.put("9", true);

        h.put("a", true);
        h.put("about", true);
        h.put("above", true);
        h.put("after", true);
        h.put("again", true);
        h.put("against", true);
        h.put("all", true);
        h.put("am", true);
        h.put("an", true);
        h.put("and", true);
        h.put("any", true);
        h.put("are", true);
        h.put("aren't", true);
        h.put("as", true);
        h.put("at", true);
        h.put("be", true);
        h.put("because", true);
        h.put("been", true);
        h.put("before", true);
        h.put("being", true);
        h.put("below", true);
        h.put("between", true);
        h.put("both", true);
        h.put("but", true);
        h.put("by", true);
        h.put("can't", true);
        h.put("cannot", true);
        h.put("could", true);
        h.put("couldn't", true);
        h.put("did", true);
        h.put("didn't", true);
        h.put("do", true);
        h.put("does", true);
        h.put("doesn't", true);
        h.put("doing", true);
        h.put("don't", true);
        h.put("down", true);
        h.put("during", true);
        h.put("each", true);
        h.put("few", true);
        h.put("for", true);
        h.put("from", true);
        h.put("further", true);
        h.put("had", true);
        h.put("hadn't", true);
        h.put("has", true);
        h.put("hasn't", true);
        h.put("have", true);
        h.put("haven't", true);
        h.put("having", true);
        h.put("he", true);
        h.put("he'd", true);
        h.put("he'll", true);
        h.put("he's", true);
        h.put("her", true);
        h.put("here", true);
        h.put("here's", true);
        h.put("hers", true);
        h.put("herself", true);
        h.put("him", true);
        h.put("himself", true);
        h.put("his", true);
        h.put("how", true);
        h.put("how's", true);
        h.put("i", true);
        h.put("i'd", true);
        h.put("i'll", true);
        h.put("i'm", true);
        h.put("i've", true);
        h.put("if", true);
        h.put("in", true);
        h.put("into", true);
        h.put("is", true);
        h.put("isn't", true);
        h.put("it", true);
        h.put("it's", true);
        h.put("its", true);
        h.put("itself", true);
        h.put("let's", true);
        h.put("me", true);
        h.put("more", true);
        h.put("most", true);
        h.put("mustn't", true);
        h.put("my", true);
        h.put("myself", true);
        h.put("no", true);
        h.put("nor", true);
        h.put("not", true);
        h.put("of", true);
        h.put("off", true);
        h.put("on", true);
        h.put("once", true);
        h.put("only", true);
        h.put("or", true);
        h.put("other", true);
        h.put("ought", true);
        h.put("our", true);
        h.put("ours ", true);
        h.put(" ourselves", true);
        h.put("out", true);
        h.put("over", true);
        h.put("own", true);
        h.put("same", true);
        h.put("shan't", true);
        h.put("she", true);
        h.put("she'd", true);
        h.put("she'll", true);
        h.put("she's", true);
        h.put("should", true);
        h.put("shouldn't", true);
        h.put("so", true);
        h.put("some", true);
        h.put("say", true);
        h.put("said", true);
        h.put("such", true);
        h.put("than", true);
        h.put("that", true);
        h.put("that's", true);
        h.put("the", true);
        h.put("their", true);
        h.put("theirs", true);
        h.put("them", true);
        h.put("themselves", true);
        h.put("then", true);
        h.put("there", true);
        h.put("there's", true);
        h.put("these", true);
        h.put("they", true);
        h.put("they'd", true);
        h.put("they'll", true);
        h.put("they're", true);
        h.put("they've", true);
        h.put("this", true);
        h.put("those", true);
        h.put("through", true);
        h.put("to", true);
        h.put("too", true);
        h.put("under", true);
        h.put("until", true);
        h.put("up", true);
        h.put("very", true);
        h.put("was", true);
        h.put("wasn't", true);
        h.put("we", true);
        h.put("we'd", true);
        h.put("we'll", true);
        h.put("we're", true);
        h.put("we've", true);
        h.put("were", true);
        h.put("weren't", true);
        h.put("what", true);
        h.put("what's", true);
        h.put("when", true);
        h.put("when's", true);
        h.put("where", true);
        h.put("where's", true);
        h.put("which", true);
        h.put("while", true);
        h.put("who", true);
        h.put("who's", true);
        h.put("whom", true);
        h.put("why", true);
        h.put("why's", true);
        h.put("with", true);
        h.put("won't", true);
        h.put("would", true);
        h.put("wouldn't", true);
        h.put("you", true);
        h.put("you'd", true);
        h.put("you'll", true);
        h.put("you're", true);
        h.put("you've", true);
        h.put("your", true);
        h.put("yours", true);
        h.put("yourself", true);
        h.put("yourselves ", true);
    }

    public boolean isStopWord(String s)
    {
        boolean ret = h.get(s)==null? false: true;
        if(s.length()==1) ret = true;
        return ret;
    }

    public static StopWords getInstance()
    {
		if(instance == null)
			instance = new StopWords();
		return instance;	
    } 
}
