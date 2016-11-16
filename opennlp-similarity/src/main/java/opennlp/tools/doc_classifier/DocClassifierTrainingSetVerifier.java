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
package opennlp.tools.doc_classifier;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.jsmlearning.ProfileReaderWriter;

import org.apache.commons.io.FileUtils;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.json.JSONObject;

/*
 * This utility gets 'training_corpus' as input and creates a new version of training_corpus with verified files.
 * Verified => classified by existing training set as only belonging to its target category, no other categories, not empty.
 */
public class DocClassifierTrainingSetVerifier {
	public static String projectHome = new File(".").getAbsolutePath();
	public static String resourceDir = new File(".").getAbsolutePath().replace("/.", "") + "/src/main/resources";
	DocClassifier classifier = null;
	private String sourceDir = null, destinationDir = null;
	

	protected ArrayList<File> queue = new ArrayList<File>();

	protected Tika tika = new Tika();
	public DocClassifierTrainingSetVerifier(String resource) {

		
		classifier = new DocClassifier("", new JSONObject());

	}
	private int FRAGMENT_LENGTH = 500;


	protected void addFiles(File file) {

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

	public void processDirectory(String fileName) throws IOException {
		List<String[]> report = new ArrayList<String[]>();
		report.add(new String[] { "filename", "category",
				"confirmed?" ,
		});
		
		addFiles(new File(fileName));
		//FileUtils.deleteDirectory(new File(destinationDir));
		//FileUtils.forceMkdir(new File(destinationDir));
		

		for (File f : queue) {
			String content = null;
			try {
				System.out.println("processing "+f.getName());
				
				//if (f.getName().indexOf(".html")<0)
					//continue;
				classifier = new DocClassifier("", new JSONObject());


				content = tika.parseToString(f);

				//classifier.runExpressionsOnContent(content);
				List<String> resultsClassif = classifier.getEntityOrClassFromText(content);
				Boolean bRejected = true;
				if (resultsClassif.size()==1 
						&& resultsClassif.get(0).equals(
								ClassifierTrainingSetIndexer.getCategoryFromFilePath(f.getAbsolutePath()))){
					String destFileName = f.getAbsolutePath().replace(sourceDir, destinationDir);
					FileUtils.copyFile(f, new File(destFileName));
					bRejected = false;
				} else {
					System.out.println("File "+ f.getAbsolutePath() + "\n classified as "+
							resultsClassif.toString() + " but should be " + ClassifierTrainingSetIndexer.getCategoryFromFilePath(f.getAbsolutePath()) );
				}
				bRejected = !bRejected;
				String fragment = content;
				if (content.length() > FRAGMENT_LENGTH)
					fragment = content.substring(0, FRAGMENT_LENGTH);
				fragment = fragment.replaceAll("\n", " ").trim();
				report.add(new String[] { f.getName(),  resultsClassif.toString(), ClassifierTrainingSetIndexer.getCategoryFromFilePath(f.getAbsolutePath()),
						(bRejected).toString(),   
						fragment});
				ProfileReaderWriter.writeReport(report,  "DocClassifierMultiLingRpt.csv");

			} catch (TikaException e) {
				System.out.println("Tika problem with file"
						+ f.getAbsolutePath());
			} catch (Exception ee) {
				ee.printStackTrace();
			}
		}


		queue.clear();
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			System.err
			.println("Verifier accepts two arguments: [0] - input 'training_corpus' folder, "
					+ "[1] - output 'training_corpus' folder . "
					+ "All paths should include category name as a part of full path string, such as '/computing/' " );
			System.exit(0);
		}

		DocClassifierTrainingSetVerifier runner = new DocClassifierTrainingSetVerifier(null);
		runner.sourceDir = args[0]; runner.destinationDir = args[1];
		runner.sourceDir =
			//	"/Users/borisgalitsky/Documents/svm_tk_july2015/milkyway/eval_corpus_multiling";
				"/Users/borisgalitsky/Documents/merged_svm_tk/milkyway/training_corpus_new_multilingual";
		runner.destinationDir =
				"/Users/borisgalitsky/Documents/svm_tk_july2015/milkyway/training_corpus_multilingual_verif";
		//	"/Users/borisgalitsky/Documents/svm_tk_july2015/milkyway/eval_corpus_multiling_bogus";

		try {
			runner.processDirectory( runner.sourceDir);
		} catch (IOException e) {
			e.printStackTrace();
		}


	}
}

/*
/Users/borisgalitsky/Documents/workspace/deepContentInspection/src/main/resources/docs/netflix
 */