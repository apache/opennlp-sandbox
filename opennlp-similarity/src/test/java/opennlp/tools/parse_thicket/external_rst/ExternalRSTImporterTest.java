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
package opennlp.tools.parse_thicket.external_rst;


import java.util.List;

import junit.framework.TestCase;
import opennlp.tools.parse_thicket.ParseThicket;
import opennlp.tools.parse_thicket.WordWordInterSentenceRelationArc;
import opennlp.tools.parse_thicket.matching.Matcher;

public class ExternalRSTImporterTest extends TestCase{
	

	public void testBuildParseThicketFromTextWithRSTtest(){
		Matcher m = new Matcher();
		// We combine our own RST rules with those of Joty 2014 to produce an augmented parse thicket
		String externalRSTresultFilename = "/external_rst/resInput.txt";

		ParseThicket pt = m.buildParseThicketFromTextWithRST("I explained that I made a deposit, and then wrote a check, which bounced due to a bank error. A customer service representative confirmed that it usually takes a day to process the deposit. "
				+ "I reminded that I was unfairly charged an overdraft fee amonth ago in a similar situation. "+
				"  They explained that the overdraft fee was due to insufficient funds as disclosed in my account information. I disagreed with their fee because I made a deposit well in "+
				" advance and wanted this fee back. They denied responsibility saying that nothing an be done at this point. They also confirmed that I needed to look into the account rules closer.");
		ExternalRSTImporter imp = new ExternalRSTImporter();

		List<WordWordInterSentenceRelationArc> arcsRST = imp.buildPT2ptPhrases( pt , externalRSTresultFilename);
		assertTrue(arcsRST .size() > 10);


	}

}
