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

import java.util.HashSet;
import java.util.Set;

/**
 * @author rtww
 */
public class StopWords {
  private static StopWords instance;
  private final Set<String> h;

  public StopWords() {
    h = new HashSet<>();
    h.add("0");
    h.add("1");
    h.add("2");
    h.add("3");
    h.add("4");
    h.add("5");
    h.add("6");
    h.add("7");
    h.add("8");
    h.add("9");

    h.add("a");
    h.add("about");
    h.add("above");
    h.add("after");
    h.add("again");
    h.add("against");
    h.add("all");
    h.add("am");
    h.add("an");
    h.add("and");
    h.add("any");
    h.add("are");
    h.add("aren't");
    h.add("as");
    h.add("at");
    h.add("be");
    h.add("because");
    h.add("been");
    h.add("before");
    h.add("being");
    h.add("below");
    h.add("between");
    h.add("both");
    h.add("but");
    h.add("by");
    h.add("can't");
    h.add("cannot");
    h.add("could");
    h.add("couldn't");
    h.add("did");
    h.add("didn't");
    h.add("do");
    h.add("does");
    h.add("doesn't");
    h.add("doing");
    h.add("don't");
    h.add("down");
    h.add("during");
    h.add("each");
    h.add("few");
    h.add("for");
    h.add("from");
    h.add("further");
    h.add("had");
    h.add("hadn't");
    h.add("has");
    h.add("hasn't");
    h.add("have");
    h.add("haven't");
    h.add("having");
    h.add("he");
    h.add("he'd");
    h.add("he'll");
    h.add("he's");
    h.add("her");
    h.add("here");
    h.add("here's");
    h.add("hers");
    h.add("herself");
    h.add("him");
    h.add("himself");
    h.add("his");
    h.add("how");
    h.add("how's");
    h.add("i");
    h.add("i'd");
    h.add("i'll");
    h.add("i'm");
    h.add("i've");
    h.add("if");
    h.add("in");
    h.add("into");
    h.add("is");
    h.add("isn't");
    h.add("it");
    h.add("it's");
    h.add("its");
    h.add("itself");
    h.add("let's");
    h.add("me");
    h.add("more");
    h.add("most");
    h.add("mustn't");
    h.add("my");
    h.add("myself");
    h.add("no");
    h.add("nor");
    h.add("not");
    h.add("of");
    h.add("off");
    h.add("on");
    h.add("once");
    h.add("only");
    h.add("or");
    h.add("other");
    h.add("ought");
    h.add("our");
    h.add("ours ");
    h.add(" ourselves");
    h.add("out");
    h.add("over");
    h.add("own");
    h.add("same");
    h.add("shan't");
    h.add("she");
    h.add("she'd");
    h.add("she'll");
    h.add("she's");
    h.add("should");
    h.add("shouldn't");
    h.add("so");
    h.add("some");
    h.add("say");
    h.add("said");
    h.add("such");
    h.add("than");
    h.add("that");
    h.add("that's");
    h.add("the");
    h.add("their");
    h.add("theirs");
    h.add("them");
    h.add("themselves");
    h.add("then");
    h.add("there");
    h.add("there's");
    h.add("these");
    h.add("they");
    h.add("they'd");
    h.add("they'll");
    h.add("they're");
    h.add("they've");
    h.add("this");
    h.add("those");
    h.add("through");
    h.add("to");
    h.add("too");
    h.add("under");
    h.add("until");
    h.add("up");
    h.add("very");
    h.add("was");
    h.add("wasn't");
    h.add("we");
    h.add("we'd");
    h.add("we'll");
    h.add("we're");
    h.add("we've");
    h.add("were");
    h.add("weren't");
    h.add("what");
    h.add("what's");
    h.add("when");
    h.add("when's");
    h.add("where");
    h.add("where's");
    h.add("which");
    h.add("while");
    h.add("who");
    h.add("who's");
    h.add("whom");
    h.add("why");
    h.add("why's");
    h.add("with");
    h.add("won't");
    h.add("would");
    h.add("wouldn't");
    h.add("you");
    h.add("you'd");
    h.add("you'll");
    h.add("you're");
    h.add("you've");
    h.add("your");
    h.add("yours");
    h.add("yourself");
    h.add("yourselves");
  }

  public static StopWords getInstance() {
    if (instance == null)
      instance = new StopWords();
    return instance;
  }

  public boolean isStopWord(String s) {
    if (s.length() <= 1) {
      return true;
    } else {
      return h.contains(s);
    }
  }
}
