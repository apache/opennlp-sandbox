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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.jsmlearning.ProfileReaderWriter;
import opennlp.tools.parse_thicket.VerbNetProcessor;

import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

public class PersonalInformationExtractor {
	FrameQueryBasedIExtractor extractor = new FrameQueryBasedIExtractor();
	private ArrayList<File> queue = new ArrayList<File>();
	private Tika tika = new Tika();

	public void runExtractor(String filename){
		String content = null;
		try {
			content = FileUtils.readFileToString(new File(filename));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		extractor.buildTemplates(new String[] { "John Doe send his California driver license 1234567 . "
				+ "Jill Jones received her Ohio license 4567456. ", 
				" Mary Poppins got her identification 8765. Jorge Malony sold his identification 9876. ",
				//" President Jorge Smith of Microsoft used his id 4567. Manager John Smith of Google used his id 8765. "
				" Johh Doe 123. Don Joe 1323. "

		});

		List<GeneralizationResult>  res = extractor.doIE( content);

	}


	private void addFiles(File file) {

		try {
			if (!file.exists()) {
				System.out.println(file + " does not exist.");
			}
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					try {
						addFiles(f);
					} catch (Exception e) {
					}
				}
			} else {
				queue.add(file);
			}
		} catch (Exception e) {

		}
	}

	public void processDirectory(String filename, String template) throws IOException {
		List<String[]> report = new ArrayList<String[]>(); 
		report.add(new String[]{"filename", "text",  "generalization", "fired?" });
		String templateStr = null;
		try {

			templateStr =  FileUtils.readFileToString(new File(template));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String[] samples = templateStr.split("&");

		extractor.buildTemplates(samples);

		addFiles(new File(filename));


		for (File f : queue) {
			String content=null;
			try {
				content = tika.parseToString(f);
				List<GeneralizationResult>  res = extractor.doIE( content);

				for(GeneralizationResult gr: res){
					report.add(new String[]{filename, gr.getText(),  gr.getGen().toString(), gr.getbFire().toString() });
				}

			} catch (TikaException e) {
				System.out.println("Tika problem with file" + f.getAbsolutePath());
			} catch (Exception ee){
				ee.printStackTrace();
			}
			ProfileReaderWriter.writeReport(report, "PII_report.csv");
		}

		queue.clear();
	}


	public void runExtractor(String filename, String template){
		String content = null, templateStr = null;
		try {
			content = FileUtils.readFileToString(new File(filename));
			templateStr =  FileUtils.readFileToString(new File(template));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String[] samples = templateStr.split("&");

		extractor.buildTemplates(samples);

		List<GeneralizationResult>  res = extractor.doIE( content);
		List<String[]> report = new ArrayList<String[]>();

		for(GeneralizationResult gr: res){
			report.add(new String[]{filename, gr.getText(),  gr.getGen().toString(), gr.getbFire().toString() });
		}


	}

	public static void main(String[] args){
		//String filename = "/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/test/resources/pii/agreement.txt";
		
		if (args ==null || args.length!=3)
			System.err.println("Usage: java -Xmx10g -jar *.jar path-to-resources path-to-file-to-analyze path-to-file-with_samples\n");
		try {
			VerbNetProcessor.getInstance(args[0]);
			new PersonalInformationExtractor().processDirectory( args[1], args[2]);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
