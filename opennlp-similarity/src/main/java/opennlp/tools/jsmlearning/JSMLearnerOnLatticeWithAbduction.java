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

package opennlp.tools.jsmlearning;

import java.util.Arrays;
import java.util.List;

public class JSMLearnerOnLatticeWithAbduction extends JSMLearnerOnLatticeWithDeduction {

	@Override
	public JSMDecision buildLearningModel(List<String> posTexts, List<String> negTexts, 
			String unknown, String[] separationKeywords){
		//TODO verify each hypothesis
		return super.buildLearningModel(posTexts, negTexts, unknown, separationKeywords);
	}


	public static void main (String[] args) {
		String[] posArr = new String[] {"I rent an office space. This office is for my business. I can deduct office rental expense from my business profit to calculate net income. ",
				"To run my business, I have to rent an office. The net business profit is calculated as follows. Rental expense needs to be subtracted from revenue. ",
				"To store goods for my retail business I rent some space. When I calculate the net income, I take revenue and subtract business expenses such as office rent. ",
		"I rent some space for my business. To calculate my net income, I subtract from revenue my rental business expense."};

		String[] negArr = new String[] {"I rent out a first floor unit of my house to a travel business. I need to add the rental income to my profit. However, when I repair my house, I can deduct the repair expense from my rental income. ",
				"I receive rental income from my office. I have to claim it as a profit in my tax forms. I need to add my rental income to my profits, but subtract rental expenses such as repair from it. ",
				"I advertised my property as a business rental. Advertisement and repair expenses can be subtracted from the rental income. Remaining rental income needs to be added to my profit and be reported as taxable profit. ",			
		"I showed  my property to a business owner to rent. Expenses on my time spent on advertisement are subtracted from the rental income. My rental profits are added to my taxable income.  "};	

		String unknown = "I do not want to rent anything to anyone. I just want to rent a space for myself. I neither calculate deduction of individual or business tax. I subtract my tax from my income";
		JSMLearnerOnLatticeWithAbduction jsm = new JSMLearnerOnLatticeWithAbduction();
		JSMDecision dec1 =  // may be determined by 'subtract'
				jsm.buildLearningModel(Arrays.asList(posArr), Arrays.asList(negArr), unknown , new String[]{"subtract"});
		JSMDecision dec2 = // may be determined by ...
				jsm.buildLearningModel(Arrays.asList(posArr), Arrays.asList(negArr), unknown , new String[]{"business"});
		JSMDecision dec3 = // may be determined by ...
				jsm.buildLearningModel(Arrays.asList(posArr), Arrays.asList(negArr), unknown , new String[]{"property"});
		// Finally, do prediction
		JSMDecision dec = // may be determined by ...
				jsm.buildLearningModel(Arrays.asList(posArr), Arrays.asList(negArr), unknown , new String[]{"property"});
	}
}
