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
package opennlp.tools.similarity.apps.solr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.logging.Logger;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

import opennlp.tools.similarity.apps.HitBase;
import opennlp.tools.similarity.apps.RelatedSentenceFinder;
import opennlp.tools.similarity.apps.RelatedSentenceFinderML;
import opennlp.tools.textsimilarity.chunker2matcher.ParserChunker2MatcherProcessor;

public class ContentGeneratorRequestHandler extends SearchHandler {
	private static final Logger LOG =
					Logger.getLogger("com.become.search.requestHandlers.SearchResultsReRankerRequestHandler");
	private final WordDocBuilderEndNotes docBuilder = new WordDocBuilderEndNotes ();

	public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp){

		String query = req.getParams().get("q");
		LOG.info(query);

		String[] runCommand = new String[12], runInternal = new String[8];
		runCommand[0] = "java";
		runCommand[1] = "-Xmx1g";
		runCommand[2] = "-jar";
		runCommand[3] = "pt.jar";
		runCommand[4] = "\""+query+"\"";
		runCommand[5] = req.getParams().get("email");
		runCommand[6] = req.getParams().get("resourceDir");
		runCommand[7] = req.getParams().get("stepsNum");
		runCommand[8] = req.getParams().get("searchResultsNum");
		runCommand[9] = req.getParams().get("relevanceThreshold");
		runCommand[10] = req.getParams().get("lang");
		runCommand[11] = req.getParams().get("bingKey");

		for(int i= 0; i<8; i++){
			runInternal[i] = runCommand[i+4];
		}
		String resultText = null;
		try {
			resultText = cgRunner(runInternal);
		} catch (Exception e1) {
			
		/*
				Runtime r = Runtime.getRuntime();
				Process mStartProcess = null;
				String workDir = req.getParams().get("workDir");
				if (workDir == null)
					System.err.println("workDir = null");

				try {
					mStartProcess = r.exec(runCommand, null, new File(workDir));
				} catch (IOException e) {
					e.printStackTrace();
				}

				StreamLogger outputGobbler = new StreamLogger(mStartProcess.getInputStream());
				outputGobbler.start();
				}
		*/
		}
		
		NamedList<Object> values = rsp.getValues();
		values.remove("response");
		values.add("response", "We completed your request to write an essay on '"+query+"' and sent you an email at "+ runCommand[5]);
		values.add("text", resultText);
		rsp.setAllValues(values);

	}

	static class StreamLogger extends Thread{

		private final InputStream mInputStream;

		public StreamLogger(InputStream is) {
			this.mInputStream = is;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(mInputStream);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					System.out.println(line);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	public String cgRunner(String[] args) {
		int count=0; 
		for(String a: args){
			System.out.print(count+">>" + a + " | ");
			count++;
		}
		try {
			String resourceDir = args[2];
			ParserChunker2MatcherProcessor sm = null;
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
					//"xdnRVcVf9m4vDvW1SkTAz5kS5DFYa19CrPYGelGJxnc";
		}

		RelatedSentenceFinder f = null;
		String lang = args[6];
		if (lang.startsWith("es") || lang.startsWith("ru") || lang.startsWith("de")){
			f = new RelatedSentenceFinderML(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Float.parseFloat(args[5]), bingKey);
			f.setLang(lang);
		} else	    

			if (args.length>4 && args[4]!=null)
				f = new RelatedSentenceFinder(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Float.parseFloat(args[5]), bingKey);
			else
				f = new RelatedSentenceFinder();
		String generatedContent = null;
		List<HitBase> hits = null;
		try {

			hits = f.generateContentAbout(args[0].replace('+', ' ').replace('"', ' ').trim());
			
			System.out.println(HitBase.toString(hits));
			generatedContent = HitBase.toResultantString(hits) + "\n REFERENCES \n" + HitBase.produceReferenceSection(hits) ;

			try {
				writeResultInAFile(args[0].replace('+', ' '), generatedContent);
			} catch (Exception e2) {
				e2.printStackTrace();
			}
			
			String attachmentFileName = null;
			try {
				attachmentFileName = docBuilder.buildWordDoc(hits, args[0].replace('+', ' ').replace('"', ' '));
			} catch (Exception e2) {
				e2.printStackTrace();
			}
			
			opennlp.tools.apps.utils.email.EmailSender s = new opennlp.tools.apps.utils.email.EmailSender();

			try {
				s.sendMail("smtp.rambler.ru", "bg7550@rambler.ru", "pill0693", new InternetAddress("bg7550@rambler.ru"), new InternetAddress[]{new InternetAddress(args[1])}, new InternetAddress[]{}, new InternetAddress[]{}, 
						"Generated content for you on '"+args[0].replace('+', ' ')+"'", generatedContent, attachmentFileName);
			} catch (AddressException e) {
				e.printStackTrace();
			} catch (Exception e) {

				e.printStackTrace();
				try {
					s.sendMail("smtp.rambler.ru", "bg7550@rambler.ru", "pill0693", new InternetAddress("bg7550@rambler.ru"), new InternetAddress[]{new InternetAddress(args[1])}, new InternetAddress[]{}, new InternetAddress[]{}, 
							"Generated content for you on '"+args[0].replace('+', ' ')+"'", generatedContent, attachmentFileName);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}


		} catch (Exception e) {
			e.printStackTrace();
		}
		return generatedContent;
	}

	private void writeResultInAFile(String title, String content){
		FileOutputStream fop = null;
		File file;
		String absPath = new File(".").getAbsolutePath();
		absPath = absPath.substring(0, absPath.length()-1);
 
		try {
 
			file = new File(absPath+"/written/"+ title.replace(' ','_').replace('\"', ' ').trim()+ ".txt");
			// if file doesn't exist, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
			fop = new FileOutputStream(file);
  
			// get the content in bytes
			byte[] contentInBytes = content.getBytes();
 
			fop.write(contentInBytes);
			fop.flush();
			fop.close(); 
			 
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}

/*
http://173.255.254.250:8983/solr/contentgen/?q=human+body+anatomy&email=bgalitsky@hotmail.com&resourceDir=/home/solr/solr-4.4.0/example/src/test/resources&workDir=/home/solr/solr-4.4.0/example/solr-webapp/webapp/WEB-INF/lib&stepsNum=20&searchResultsNum=10&relevanceThreshold=1.5
 */