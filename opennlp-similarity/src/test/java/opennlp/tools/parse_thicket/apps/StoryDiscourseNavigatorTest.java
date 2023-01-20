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

import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import opennlp.tools.similarity.apps.StoryDiscourseNavigator;

import static org.junit.Assert.assertTrue;

public class StoryDiscourseNavigatorTest {

	@Test
	@Ignore
	// TODO OPENNLP-1455 This test fails with "UnknownHostException: api.datamarket.azure.com: nodename nor servname provided, or not known"
	public void testGeneratedExtensionKeywords(){
		String[] res = new StoryDiscourseNavigator().obtainAdditionalKeywordsForAnEntity("Albert Einstein");
		assertTrue(res.length>0);
		assertTrue(Arrays.asList(res).toString().contains("physics"));
		assertTrue(Arrays.asList(res).toString().contains("relativity"));
	}
}
