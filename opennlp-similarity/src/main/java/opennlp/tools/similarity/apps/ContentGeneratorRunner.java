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

import java.util.List;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import opennlp.tools.apps.utils.email.EmailSender;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class ContentGeneratorRunner {
	public static void main(String[] args) {
		ParserChunker2MatcherProcessor sm = null;
	    	    
		try {
			String resourceDir = args[2];
			if (resourceDir!=null)
				sm = ParserChunker2MatcherProcessor.getInstance(resourceDir);
			else
				sm = ParserChunker2MatcherProcessor.getInstance();
	
		} catch (Exception e) {
			e.printStackTrace();
		}
	    
		String bingKey = args[7];
		if (bingKey == null){
			bingKey = "e8ADxIjn9YyHx36EihdjH/tMqJJItUrrbPTUpKahiU0=";
		}

		RelatedSentenceFinder f = null;
		String lang = args[6];
		if (lang.startsWith("es")){
			f = new RelatedSentenceFinderML(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Float.parseFloat(args[5]), bingKey);
			f.setLang(lang);
		} else

			if (args.length>4 && args[4]!=null)
				f = new RelatedSentenceFinder(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Float.parseFloat(args[5]), bingKey);
			else
				f = new RelatedSentenceFinder();

		List<HitBase> hits = null;
		try {

			hits = f.generateContentAbout(args[0].replace('+', ' ').replace('"', ' ').trim());
			System.out.println(HitBase.toString(hits));
			String generatedContent = HitBase.toResultantString(hits);

			opennlp.tools.apps.utils.email.EmailSender s = new opennlp.tools.apps.utils.email.EmailSender();

		try {
			s.sendMail("smtp.live.com", "bgalitsky@hotmail.com", "borgalor", new InternetAddress("bgalitsky@hotmail.com"), new InternetAddress[]{new InternetAddress(args[1])}, new InternetAddress[]{}, new InternetAddress[]{},
					"Generated content for you on '"+args[0].replace('+', ' ')+"'", generatedContent, null);
		} catch (AddressException e) {
			e.printStackTrace();
		} catch (Exception e) {

			e.printStackTrace();
			try {
				s.sendMail("smtp.live.com", "bgalitsky@hotmail.com", "borgalor", new InternetAddress("bgalitsky@hotmail.com"), new InternetAddress[]{new InternetAddress(args[1])}, new InternetAddress[]{}, new InternetAddress[]{},
						"Generated content for you on '"+args[0].replace('+', ' ')+"'", generatedContent, null);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}


		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}

/*
 * C:\stanford-corenlp>java -Xmx1g -jar pt.jar albert+einstein bgalitsky@hotmail.com C:/stanford-corenlp/src/test/resources
 * 
 * http://173.255.254.250:8983/solr/contentgen/?q=albert+einstein&email=bgalitsky@hotmail.com&resourceDir=/home/solr/solr-4.4.0/example/src/test/resources&workDir=/home/solr/solr-4.4.0/example/solr-webapp/webapp/WEB-INF/lib&stepsNum=20&searchResultsNum=100&relevanceThreshold=0.5&lang=es-US&bingKey=e8ADxIjn9YyHx36EihdjH/tMqJJItUrrbPTUpKahiU0=
 */
