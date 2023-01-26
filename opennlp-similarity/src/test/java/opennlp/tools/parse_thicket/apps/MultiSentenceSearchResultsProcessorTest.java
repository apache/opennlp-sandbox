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

import java.util.List;

import opennlp.tools.similarity.apps.HitBase;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

	public class MultiSentenceSearchResultsProcessorTest {

	private final MultiSentenceSearchResultsProcessor proc = new MultiSentenceSearchResultsProcessor();

	@Test
	@Ignore
	// TODO OPENNLP-1455 This test case fails with: "UnknownHostException: api.datamarket.azure.com: nodename nor servname provided, or not known"
	public void testSearchOrder() {
		List<HitBase> res; HitBase first = null;
		String query ;
		/*
		query = "I am now living abroad and have health insurance from Russia. How can I avoid penalty for not having health insurance in US";
		res = proc.runSearchViaAPI(query);
		// we verify that top answers have high similarity score
		System.out.println(res);
		first = res.get(0);
		assertTrue(first.getGenerWithQueryScore() > 2.0f);
	  */
		
		
		query = "Furious about reports that the IRS was used to target conservative groups, President Obama said that acting IRS Director Steve T. Miller was asked to resign. "+
				"IRS actions were inexcusable. Americans are right to be angry about it. Obama will not tolerate this type of behavior by IRS";
		res = proc.runSearchViaAPI(query);
		// we verify that top answers have high similarity score
		System.out.println(res);
		first = res.get(0);
		assertTrue(first.getGenerWithQueryScore() > 000f);

		query = " I see no meaningful distinction between complacency or complicity in the military's latest failure to uphold their own " +
				"standards of conduct. Nor do I see a distinction between the service member who orchestrated this offense and the chain of " +
				"command that was either oblivious to or tolerant of criminal behavior";
		res = proc.runSearchViaAPI(query);
		first = res.get(0);
		assertTrue(first.getGenerWithQueryScore() > 1.69);
		// assertTrue(second.getTitle().indexOf("living abroad")>-1);
		proc.close();

	}

	/*public void testSimpleQuery(){
		List<HitBase> res = proc.runSearchViaAPI("How can I pay tax on my income abroad");
	}*/

}
