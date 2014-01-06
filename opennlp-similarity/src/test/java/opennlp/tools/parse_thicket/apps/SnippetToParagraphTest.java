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


import opennlp.tools.similarity.apps.HitBase;
import junit.framework.TestCase;


public class SnippetToParagraphTest extends TestCase {
	SnippetToParagraph converter = new SnippetToParagraph();

	public void testConversionTest(){
		HitBase input = new HitBase();
		input.setAbstractText("... complicity in the military's latest failure to uphold their own standards of conduct. Nor do I see a distinction between the service member who orchestrated this offense ...");
		input.setUrl("http://armedservices.house.gov/index.cfm/press-releases?ContentRecord_id=b5d9aeab-6745-4eba-94ea-12295fd40e67");
		input.setTitle("Press Releases - News - Armed Services Republicans");
		HitBase result = converter.formTextFromOriginalPageGivenSnippet(input);
		assertTrue(result.getOriginalSentences()!=null);
		assertTrue(result.getOriginalSentences().size()>0);
	}

}
