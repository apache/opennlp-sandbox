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
package opennlp.tools.parse_thicket.apps;


import java.util.ArrayList;

import opennlp.tools.similarity.apps.ContentGenerator;
import opennlp.tools.similarity.apps.HitBase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatedSentenceFinderTest {

  private final ContentGenerator finder = new ContentGenerator();

  @Test
  void testAugmentWithMinedSentencesAndVerifyRelevanceTest() {
    HitBase input = new HitBase();
    input.setAbstractText("He is pictured here in the Swiss Patent Office where he did ...");
    input.setUrl("https://apod.nasa.gov/apod/ap951219.html");
    input.setTitle("Albert Einstein");
    HitBase result = finder.buildParagraphOfGeneratedText(input, "Swiss Patent Office", new ArrayList<>());
    assertNotNull(result.getOriginalSentences());
    assertTrue(result.getOriginalSentences().size() > 0);
    // TODO Investigate why the below to checks fail
    // assertTrue(result.getFragments().size()>0);
    // assertTrue(result.getFragments().get(0).getFragment().contains("Swiss Patent Office"));
  }
	
	/*
		public void testBuildParagraphOfGeneratedTextTest(){
			HitBase input = new HitBase();
			input.setAbstractText("Albert Einstein was a German-born theoretical physicist who developed the general theory of relativity, one of the two pillars of modern physics (alongside ...");
			input.setUrl("http://en.wikipedia.org/wiki/Albert_Einstein");
			input.setTitle("Albert Einstein - Wikipedia, the free encyclopedia");
			HitBase result = finder.buildParagraphOfGeneratedText(input,
					"Albert Einstein", new ArrayList<String>());
			System.out.println(result.toString());
			assertTrue(result.getOriginalSentences()!=null);
			assertTrue(result.getOriginalSentences().size()>0);
			assertTrue(result.getFragments().size()>0);
			assertTrue(result.getFragments().get(0).getFragment().indexOf("Albert Einstein")>-1);
		}
	*/

  @Test
  void testBuildParagraphOfGeneratedTextTestYearInTheEnd() {

    HitBase input = new HitBase();
    input.setAbstractText("Albert Einstein was born ... Germany, on March 14, 1879");
    input.setUrl("https://www.nobelprize.org/prizes/physics/1921/einstein/biographical");
    input.setTitle("Albert Einstein - Biographical");
    HitBase result = finder.buildParagraphOfGeneratedText(input, "Albert Einstein", new ArrayList<>());
    assertNotNull(result.getOriginalSentences());
    assertTrue(result.getOriginalSentences().size() > 0);
    assertTrue(result.getFragments().size() > 0);
    assertTrue(result.getFragments().get(0).getFragment().contains("Albert Einstein"));
  }
	
	/*
	public void testBuildParagraphOfGeneratedTextTestBio1(){
		HitBase input = new HitBase();
		input.setAbstractText("Today, the practical applications of Einstein�s theories ...");
		input.setUrl("http://einstein.biz/biography.php");
		input.setTitle("Biography");
		HitBase result = finder.buildParagraphOfGeneratedText(input,
				"applications of Einstein theories ", new ArrayList<String>());
		System.out.println(result.toString());
		assertTrue(result.getOriginalSentences()!=null);
		assertTrue(result.getOriginalSentences().size()>0);
		assertTrue(result.getFragments().size()>0);
		assertTrue(result.getFragments().get(0).getFragment().indexOf("Einstein")>-1);
	} 
	
	public void testBuildParagraphOfGeneratedTextTestBio2(){
		HitBase input = new HitBase();
		input.setAbstractText("The theory of relativity is a beautiful example of  ...");
		input.setUrl("https://en.wikiquote.org/wiki/Albert_Einstein");
		input.setTitle("Albert Einstein");
		HitBase result = finder.buildParagraphOfGeneratedText(input,
				"beautiful example of", new ArrayList<String>());
		System.out.println(result.toString());
		assertTrue(result.getOriginalSentences()!=null);
		assertTrue(result.getOriginalSentences().size()>0);
		assertTrue(result.getFragments().size()>0);
		assertTrue(result.getFragments().get(0).getFragment().indexOf("relativity")>-1);
	} 
	
	public void testBuildParagraphOfGeneratedTextTestBio3(){
		HitBase input = new HitBase();
		input.setAbstractText("I cannot conceive of a god who rewards and punishes his creatures or has a will of the kind that we experience  ...");
		input.setUrl("http://www.ldolphin.org/einstein.html");
		input.setTitle("Some Quotations of ALBERT EINSTEIN (1879-1955)");
		HitBase result = finder.buildParagraphOfGeneratedText(input,
				"cannot conceive a god", new ArrayList<String>());
		System.out.println(result.toString());
		assertTrue(result.getOriginalSentences()!=null);
		assertTrue(result.getOriginalSentences().size()>0);
		assertTrue(result.getFragments().size()>0);
		assertTrue(result.getFragments().get(0).getFragment().indexOf("cannot conceive")>-1);
	}  
	

	public void testBuildParagraphOfGeneratedTextTestBio4(){
		HitBase input = new HitBase();
		input.setAbstractText(" In 1905 our view of the world was changed dramatically and ...");
		input.setUrl("http://philosophynow.org/issues/93/Albert_Einstein_1879-1955");
		input.setTitle("ALBERT EINSTEIN (1879-1955)");
		HitBase result = finder.buildParagraphOfGeneratedText(input,
				"view of the world", new ArrayList<String>());
		System.out.println(result.toString());
		assertTrue(result.getOriginalSentences()!=null);
		assertTrue(result.getOriginalSentences().size()>0);
		assertTrue(result.getFragments().size()>0);
		assertTrue(result.getFragments().get(0).getFragment().indexOf("view of the world")>-1);
	}  */


}


//[Albert Einstein (/�lbrt anstan/; German. albt antan ( listen); 14 March 1879 18 April 1955) was a German-born theoretical physicist who developed the general theory of relativity, one of the two pillars of modern physics (alongside quantum mechanics). 2 3 While best known for his massenergy equivalence formula E = mc2 (which has been dubbed "the world's most famous equation"), 4 he received the 1921 Nobel Prize in Physics "for his services to theoretical physics, and especially for his discovery of the law of the photoelectric effect". 5 The latter was pivotal in establishing quantum theory. nullNear the beginning of his career, Einstein thought that Newtonian mechanics was no longer enough to reconcile the laws of classical mechanics with the laws of the electromagnetic field. This led to the development of his special theory of relativity.,

//"Today, the practical applications of Einstein�s theories include the development of the television"