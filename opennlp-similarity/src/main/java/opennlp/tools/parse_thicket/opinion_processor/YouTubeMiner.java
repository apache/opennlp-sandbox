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
package opennlp.tools.parse_thicket.opinion_processor;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import opennlp.tools.similarity.apps.utils.PageFetcher;

public class YouTubeMiner {
	private PageFetcher fetcher = new PageFetcher();
	public YouTubeMinerResult getData(String url){
		YouTubeMinerResult result = new YouTubeMinerResult();
		String content = fetcher.fetchOrigHTML(url);
		try {
			FileUtils.writeStringToFile(new File(url.replace(':', '_').replace('/', '_')), content);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if (url.indexOf("channel")>-1){
			try { //subscriber-count" title="30" 
				String subscribersStr = StringUtils.substringBetween(content,"subscriber-count", "tabindex");
				String dirtyNumber = StringUtils.substringBetween(subscribersStr, "title=\"", "\"");
				String cleanNumber = dirtyNumber.replaceAll("[^\\x00-\\x7F]", "");
				if (cleanNumber!=null){
					int subscribers = Integer.parseInt(cleanNumber );
					result.subscribers = subscribers;
				} else {
					System.err.println("Not found data for 'subscriber-count', 'tabindex'");
				}
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {

				String subscribersStr = StringUtils.substringBetween(content,"subscriber-count", "tabindex");
				String dirtyNumber = StringUtils.substringBetween(subscribersStr, "title=\"", "\"").replace(" ", "");
				if (dirtyNumber!=null){
					int subscribers = Integer.parseInt(dirtyNumber );
					result.subscribers = subscribers;
				} else {
					System.err.println("Not found data for 'subscriber-count', 'tabindex'");
				}

				String viewsStrDirty = StringUtils.substringBetween(content,
						//"div class=\"watch-view-count\">"," views</div>");
						//view-count">12 просмотров</div>
						"view-count","<div>");
				String viewsStr = StringUtils.substringBetween(viewsStrDirty,">", " ");
				if (viewsStr!=null){
					int views = Integer.parseInt(viewsStr );
					result.views = views;
				} else {
					System.err.println("Not found data for 'view-count','<div>'");
				}
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return result;
	}




	public static void main(String[] args){
		YouTubeMiner  miner = new YouTubeMiner();
		System.out.println(miner.getData("https://www.youtube.com/channel/UC-maQbG5eUS5c1wmaTnLwTA"));
		System.out.println(miner.getData("https://www.youtube.com/watch?v=U6X4VT9dVr8"));
		System.out.println(miner.getData("https://www.youtube.com/watch?v=kH-AQnta714"));
		System.out.println(miner.getData("https://www.youtube.com/watch?v=pWb50Kn1ShQ"));
	}
}


