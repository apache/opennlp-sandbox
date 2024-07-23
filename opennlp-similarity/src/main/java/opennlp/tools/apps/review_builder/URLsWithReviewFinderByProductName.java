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

package opennlp.tools.apps.review_builder;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.similarity.apps.BingQueryRunner;
import opennlp.tools.similarity.apps.HitBase;

public class URLsWithReviewFinderByProductName {
	final BingQueryRunner search = new BingQueryRunner();
	
	public List<String> findFacebookURLByNameAndZip(String name){
		List<HitBase> foundFBPages = search.runSearch(name, 20);
		List<String> results = new ArrayList<>();
		for(HitBase h: foundFBPages){
			results.add(h.getUrl());
		}
		return results;
	}
	
}
