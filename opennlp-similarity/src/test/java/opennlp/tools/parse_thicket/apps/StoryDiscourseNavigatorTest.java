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
import java.util.Arrays;
import java.util.List;

import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.RelatedSentenceFinder;
import opennlp.tools.similarity.apps.StoryDiscourseNavigator;
import junit.framework.TestCase;


public class StoryDiscourseNavigatorTest extends TestCase {
	RelatedSentenceFinder finder = new RelatedSentenceFinder();

	
	public void testGeneratedExtednsionKeywords(){
		String[] res = new StoryDiscourseNavigator().obtainAdditionalKeywordsForAnEntity("Albert Einstein");
		System.out.println(Arrays.asList(res));
		assertTrue(res.length>0);
		assertTrue(Arrays.asList(res).toString().indexOf("physics")>-1);
		assertTrue(Arrays.asList(res).toString().indexOf("relativity")>-1);
		
		
		
	} 

}

//[Albert Einstein (/ælbrt anstan/; German. albt antan ( listen); 14 March 1879 18 April 1955) was a German-born theoretical physicist who developed the general theory of relativity, one of the two pillars of modern physics (alongside quantum mechanics). 2 3 While best known for his massenergy equivalence formula E = mc2 (which has been dubbed "the world's most famous equation"), 4 he received the 1921 Nobel Prize in Physics "for his services to theoretical physics, and especially for his discovery of the law of the photoelectric effect". 5 The latter was pivotal in establishing quantum theory. nullNear the beginning of his career, Einstein thought that Newtonian mechanics was no longer enough to reconcile the laws of classical mechanics with the laws of the electromagnetic field. This led to the development of his special theory of relativity.,
