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

package opennlp.tools.parse_thicket.kernel_interface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;


import opennlp.tools.parse_thicket.apps.MinedSentenceProcessor;
import opennlp.tools.parse_thicket.apps.SnippetToParagraph;
import opennlp.tools.similarity.apps.Fragment;
import opennlp.tools.similarity.apps.GeneratedSentenceProcessor;
import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.RelatedSentenceFinder;
import opennlp.tools.similarity.apps.utils.PageFetcher;
import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;
import opennlp.tools.similarity.apps.utils.Utils;
import opennlp.tools.textsimilarity.TextProcessor;


public class SnippetToParagraphFull extends SnippetToParagraph {
	private PageFetcher pFetcher = new PageFetcher();
	private static Logger LOG = Logger
			.getLogger("com.become.parse_thicket.apps.SnippetToParagraphFull");

	

	public HitBase formTextFromOriginalPageGivenSnippet(HitBase item) {

		String[] sents = extractSentencesFromPage(item.getUrl());

		String title = item.getTitle().replace("<b>", " ").replace("</b>", " ")
				.replace("  ", " ").replace("  ", " ");
		// generation results for this sentence
		List<String> result = new ArrayList<String>();
		// form plain text from snippet
		String snapshot = item.getAbstractText().replace("<b>", " ")
				.replace("</b>", " ").replace("  ", " ").replace("  ", " ").replace("\"", "");

		String snapshotMarked = snapshot.replace(" ...", ".");
		List<String> fragments = TextProcessor.splitToSentences(snapshotMarked);
		if (fragments.size()<3 && StringUtils.countMatches(snapshotMarked, ".")>1){
			snapshotMarked = snapshotMarked.replace("..", "&").replace(".", "&");
			String[] fragmSents = snapshotMarked.split("&");
			fragments = Arrays.asList(fragmSents);
		}

		for (String f : fragments) {
			String followSent = null;
			if (f.length() < 50)
				continue;
			String pageSentence = "";
			// try to find original sentence from webpage

			try {
				String[] mainAndFollowSent = getFullOriginalSentenceFromWebpageBySnippetFragment(
						f, sents);
				pageSentence = mainAndFollowSent[0];
				followSent = mainAndFollowSent[1];
				if (pageSentence!=null)
					result.add(pageSentence);
				else {
					result.add(f);
					LOG.info("Could not find the original sentence \n"+f +"\n in the page " );
				}
				//if (followSent !=null)
				//	result.add(followSent);
			} catch (Exception e) {

				e.printStackTrace();
			}
		}
		item.setOriginalSentences(result);
		return item;
	}

	
}

