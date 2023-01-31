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

package opennlp.tools.similarity.apps;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import opennlp.tools.similarity.apps.utils.StringDistanceMeasurer;

import org.apache.commons.lang.StringUtils;

public class HitBase {
	private static final Logger LOG = Logger
			.getLogger("opennlp.tools.similarity.apps.HitBase");

	private String abstractText;

	private String clickUrl;

	private String displayUrl;

	private String url;

	private String date;

	private String title;

	private Double generWithQueryScore;

	private String source;

	private List<String> originalSentences;

	private String pageContent;

	private List<Fragment> fragments;

	public HitBase() {
		super();
	}

	public String getPageContent() {
		return pageContent;
	}

	public HitBase(String orig, String[] generateds) {
		originalSentences = new ArrayList<String>();
		originalSentences.add(orig);

		fragments = new ArrayList<Fragment>();
		for (String sent : generateds) {
			Fragment f = new Fragment(sent, 0.0);
			fragments.add(f);
		}
		// the rest of params are null
	}

	public void setPageContent(String pageContent) {
		this.pageContent = pageContent;
	}

	public List<Fragment> getFragments() {
		return fragments;
	}

	public void setFragments(List<Fragment> fragments) {
		this.fragments = fragments;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public List<String> getOriginalSentences() {
		return originalSentences;
	}

	public void setOriginalSentences(List<String> originalSentences) {
		this.originalSentences = originalSentences;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAbstractText() {
		return abstractText;
	}

	public void setAbstractText(String abstractText) {
		this.abstractText = abstractText;
	}

	public String getClickUrl() {
		return clickUrl;
	}

	public void setClickUrl(String clickUrl) {
		this.clickUrl = clickUrl;
	}

	public String getDisplayUrl() {
		return displayUrl;
	}

	public void setDisplayUrl(String displayUrl) {
		this.displayUrl = displayUrl;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public Double getGenerWithQueryScore() {
		return generWithQueryScore;
	}

	public void setGenerWithQueryScore(Double generWithQueryScore) {
		this.generWithQueryScore = generWithQueryScore;
	}

	public String toString() {
		// return "\n"+this.getUrl()+" | " +this.getTitle()+ " | "+
				// this.abstractText ;
		if (this.getFragments() != null && this.getFragments().size() > 0)
			return this.getFragments().toString();
		else
			return this.title;
	}

	public static String toString(List<HitBase> hits) {
		StringBuffer buf = new StringBuffer();
		Boolean pBreak = true;
		for (HitBase hit : hits) {
			String fragm = (hit.toString());
			if (fragm.length() > 15) {
				if (pBreak)
					buf.append(fragm + " | ");
				else
					buf.append(fragm + " | \n");
				// switch to opposite
				if (pBreak)
					pBreak = false;
				else
					pBreak = true;
			}

		}
		return buf.toString();
	}

	public static String toResultantString(List<HitBase> hits) {
		StringBuffer buf = new StringBuffer();
		Boolean pBreak = true;
		for (HitBase hit : hits) {
			try {
				if (hit.getFragments()==null)	
					continue;
				String fragm = hit.getFragments().toString();
				if (fragm.length() > 15) {
					if (pBreak)
						buf.append(fragm + " | 	");
					else
						buf.append(fragm + " | <br>\n");
					// switch to opposite
					if (pBreak)
						pBreak = false;
					else
						pBreak = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		return buf.toString().replace("[", "").replace("]", "").replace(" | ", "")
				.replace(".,", ".").replace(".\"", "\"").replace(". .", ".")
				.replace(",.", ".");
	}
	
	public static String produceReferenceSection(List<HitBase> hits) {
		StringBuffer buf = new StringBuffer();
		for (HitBase hit : hits) {
			try {
				if (hit.getUrl()==null)	
					continue;
				buf.append(hit.getUrl());					
			
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
		return buf.toString();
	}

	public static List<HitBase> removeDuplicates(List<HitBase> hits) {
		StringDistanceMeasurer meas = new StringDistanceMeasurer();
		double imageDupeThresh = 0.8; // if more similar, then considered dupes
		List<Integer> idsToRemove = new ArrayList<Integer>();
		List<HitBase> hitsDedup = new ArrayList<HitBase>();
		try {
			for (int i = 0; i < hits.size(); i++)
				for (int j = i + 1; j < hits.size(); j++) {
					String title1 = hits.get(i).getTitle();
					String title2 = hits.get(j).getTitle();
					if (StringUtils.isEmpty(title1) || StringUtils.isEmpty(title2))
						continue;
					if (meas.measureStringDistance(title1, title2) > imageDupeThresh) {
						idsToRemove.add(j); // dupes found, later list member to be deleted
					}
				}
			for (int i = 0; i < hits.size(); i++)
				if (!idsToRemove.contains(i))
					hitsDedup.add(hits.get(i));
			if (hitsDedup.size() < hits.size()) {
				LOG.info("Removed duplicates from relevant search results, including "
						+ hits.get(idsToRemove.get(0)).getTitle());
			}
		} catch (Exception e) {
			LOG.severe("Problem removing duplicates from relevant images: " + e);
		}
		return hitsDedup;
	}
}