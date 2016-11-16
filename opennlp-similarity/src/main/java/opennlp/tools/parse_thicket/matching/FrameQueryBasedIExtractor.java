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

package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.parse_thicket.VerbNetProcessor;
import opennlp.tools.textsimilarity.ParseTreeChunk;
import opennlp.tools.textsimilarity.TextProcessor;

public class FrameQueryBasedIExtractor {
	List<GeneralizationResult> templates = new ArrayList<GeneralizationResult>();
	Matcher matcher = Matcher.getInstance();



	private void init() {
		templates.clear();

	}
	public void buildPTTemplates(String[] smpls){

		GeneralizationResult templateCurr = matcher.assessRelevanceG(smpls[0], smpls[1]);
		for(int i=2; i<smpls.length; i++){

			templateCurr = matcher.assessRelevanceG(templateCurr, smpls[i]);
		}

		templates.add(templateCurr);
		System.out.println("template = "+ templateCurr);

	}

	public void buildTemplates(String[] samples){
		for(String setOfSamples : samples){
			List<String> smpls = TextProcessor.splitToSentences(setOfSamples);
			if (smpls.size()<2)
				continue;
			
			GeneralizationResult templateCurr = matcher.assessRelevanceG(smpls.get(0), smpls.get(1));
			for(int i=2; i<smpls.size(); i++){

				templateCurr = matcher.assessRelevanceG(templateCurr, smpls.get(i));
			}

			templates.add(templateCurr);
			System.out.println("template = "+ templateCurr+ "\n");
		}
	}
	
	public void buildTemplatesPairWise(String[] samples){
		for(String setOfSamples : samples){
			List<String> smpls = TextProcessor.splitToSentences(setOfSamples);

			GeneralizationResult templateCurr = null;
			for(int i=0; i<smpls.size(); i++)
				for(int j=i+1; j< smpls.size(); j++){
					templateCurr = matcher.assessRelevanceG(smpls.get(i), smpls.get(j));
					templates.add(templateCurr);
					System.out.println("template = "+ templateCurr+ "\n");
			}
		}
	}

	List<GeneralizationResult>  doIE(String text){
		List<GeneralizationResult> fires = new ArrayList<GeneralizationResult>();

		List<String> sentences = TextProcessor.splitToSentences(text);{
			for(String sent: sentences){
				for(GeneralizationResult t: templates){
					GeneralizationResult res = matcher.assessRelevanceG(t.getGen(), sent);
					boolean fire = matcher.isCoveredByTemplate(t.getGen(), res.getGen());
					System.out.println(res+ " => "+ fire + "\n");
					if (fire){
						res.setIfFire(fire);
						res.setText(sent);
						fires.add(res);
						System.out.println("=====================\n TEMPLATE FIRED: "+ sent + "\n====================\n");
					}
				}
			}

		}
		return fires;
	}

	List<GeneralizationResult>  doIEforPT(String text){
		List<GeneralizationResult> fires = new ArrayList<GeneralizationResult>();

		for(GeneralizationResult t: templates){
			GeneralizationResult res = matcher.assessRelevanceG(t.getGen(), text);
			boolean fire = matcher.isCoveredByTemplate(t.getGen(), res.getGen());
			System.out.println(res+ " =PT=> "+ fire + "\n");
			res.setIfFire(fire);
			res.setText(text);
			if (fire)
				fires.add(res);
			
		}
		return fires;
	}


	public static void main(String[] args){
		VerbNetProcessor.getInstance("/Users/borisgalitsky/Documents/workspace/opennlp-similarity/src/test/resources");
		FrameQueryBasedIExtractor extractor = new FrameQueryBasedIExtractor();
		
		String[] texts = new String[]{"An amusement park sells adult tickets for $3 and kids tickets for $2, and got the revenue $500 yesterday.",
						"A certified trainer conducts training for adult customers for $30 per hour and kid customer for $20 per hour, and got the revenue $1000 today."};		
		extractor.buildPTTemplates(texts);
		
		 texts = new String[]{"Crossing the snow slope was dangerous. They informed in the blog that an ice axe should be used. However, I am reporting that crossing the snow field in the late afternoon I had to use crampons.",
				"I could not cross the snow creek since it was dangerous. This was because the previous hiker reported that ice axe should be used in late afternoon.  To inform the fellow hikers, I had to use crampons going across the show field in the late afternoon ",
		};		
		extractor.buildPTTemplates(texts);
		List<GeneralizationResult>  res = extractor.doIEforPT( "I had to use crampons to cross snow slopes without an ice axe in late afternoon. However in summer I do not feel it was dangerous crossing the snow.");

		System.exit(0);

		extractor.buildTemplates(new String[] { ""
				+ "A junior sale engineer expert travels to customers on site. A junior design expert goes to customer companies. "
				+ "A junior software engineer rushes to customer sites. "   
		});
		res = extractor.doIE( "Cisco junior sale representative expert flew to customers data centers. ");

		extractor.init();

		extractor.buildTemplates(new String[] { "John Doe send his California driver license 1234567. "
				+ "Jill Paparapathi received her Ohio license 4567456"   });

		res = extractor.doIE( "Mary Jones send her Canada prisoner id number 666666666. Mary Stewart hid her Mexico cook id number 666666666 . Robin Hood mentioned his UK fisher id  2345."
				+ "Yesterday Peter Doe hid his Bolivia set id number 666666666. Robin mentioned her best Peru fisher man id  2345. Spain hid her Catalonian driver id number 666666666. John Poppins hid her  prisoner id  666666666. "
				+ "Microsoft announced its Canada windows azure release number 666666666. John Poppins hid her Apple id  666666666");

	}

}
